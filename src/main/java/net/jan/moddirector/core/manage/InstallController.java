package net.jan.moddirector.core.manage;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.RemoteModInformation;
import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.install.InstalledMod;
import net.jan.moddirector.core.util.HashResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.logging.Level;

public class InstallController {

    private final ModpackDirector director;

    public InstallController(ModpackDirector director) {
        this.director = director;
    }

    private Level downloadSeverityLevelFor(ModDirectorRemoteMod mod) {
        return mod.getInstallationPolicy().shouldContinueOnFailedDownload() ?
            Level.WARNING : Level.SEVERE;
    }

    public List<Callable<Void>> createPreInstallTasks(
        List<ModDirectorRemoteMod> allMods,
        List<ModDirectorRemoteMod> excludedMods,
        List<InstallableMod> freshMods,
        List<InstallableMod> reinstallMods,
        BiFunction<String, String, ProgressCallback> callbackFactory
    ) {
        List<Callable<Void>> preInstallTasks = new ArrayList<>();

        for (ModDirectorRemoteMod mod : allMods) {
            preInstallTasks.add(() -> {
                ProgressCallback callback = callbackFactory.apply(mod.offlineName(), "Checking installation status");

                callback.indeterminate(true);
                callback.message("Checking installation requirements");

                if (mod.getMetadata() != null && !mod.getMetadata().shouldTryInstall(director.platform())) {
                    director.logger().debug(
                        "Skipping mod {0} because shouldTryInstall() returned false",
                        mod.offlineName()
                    );

                    excludedMods.add(mod);

                    callback.done();
                    return null;
                }

                callback.message("Querying mod information");

                RemoteModInformation information;

                try {
                    information = mod.queryInformation();
                } catch (ModDirectorException e) {
                    director.logger().error("Failed to query information for {0} from {1}}",
                        mod.offlineName(), mod.remoteType(), e);
                    director.addError(new ModDirectorError(downloadSeverityLevelFor(mod),
                        "Failed to query information for mod " + mod.offlineName() + " from " + mod.remoteType(),
                        e));
                    callback.done();
                    return null;
                }

                callback.title(information.displayName());
                Path targetFile = computeInstallationTargetPath(mod, information);

                if (targetFile == null) {
                    callback.done();
                    return null;
                }

                Path disabledFile = computeDisabledPath(targetFile);

                if (Files.isRegularFile(disabledFile) || !isVersionCompliant(mod)) {
                    excludedMods.add(mod);
                    callback.done();
                    return null;
                }

                InstallableMod installableMod = new InstallableMod(mod, information, targetFile);

                var bansoukouPatchedFile = computeBansoukouPatchedPath(targetFile);
                var bansoukouDisabledFile = computeBansoukouDisabledPath(targetFile);

                if (mod.getMetadata() != null && (Files.isRegularFile(targetFile) || (Files.isRegularFile(bansoukouPatchedFile)
                    && Files.isRegularFile(bansoukouDisabledFile)))) {
                    HashResult hashResult = mod.getMetadata().checkHashes(Files.isRegularFile(targetFile) ? targetFile
                        : bansoukouDisabledFile, director.platform());

                    switch (hashResult) {
                        case UNKNOWN:
                            director.logger().info("Skipping download of {0} as hashes can't be determined but file exists",
                                targetFile.toString());
                            callback.done();

                            excludedMods.add(mod);
                            return null;

                        case MATCHED:
                            director.logger().info("Skipping download of [0] as the hashes match", targetFile.toString());
                            callback.done();

                            excludedMods.add(mod);
                            return null;

                        case UNMATCHED:
                            director.logger().warn("File {0} exists, but hashes do not match, downloading again!",
                                targetFile.toString());
                    }
                    Files.deleteIfExists(bansoukouPatchedFile);
                    Files.deleteIfExists(bansoukouDisabledFile);
                    reinstallMods.add(installableMod);

                } else if (mod.getInstallationPolicy().shouldDownloadAlways() && Files.isRegularFile(targetFile)) {
                    director.logger().info("Force downloading file {0} as download always option is set.",
                        targetFile.toString());
                    reinstallMods.add(installableMod);

                } else if (Files.isRegularFile(targetFile)) {
                    director.logger().debug("File {0} exists and no metadata given, skipping download.",
                        targetFile.toString());
                    excludedMods.add(mod);

                } else {
                    freshMods.add(installableMod);
                }

                if (!excludedMods.contains(mod) && mod.getInstallationPolicy().getSupersededFileName() != null) {
                    Path supersededFile = targetFile.resolveSibling(mod.getInstallationPolicy().getSupersededFileName());
                    if (Files.isRegularFile(supersededFile)) {
                        director.logger().info("Superseding {0}", targetFile);
                        Files.move(supersededFile, supersededFile.resolveSibling(supersededFile.getFileName() + ".disabled-by-mod-director"));
                    }
                }

                callback.done();
                return null;
            });
        }

        return preInstallTasks;
    }

    private Path computeInstallationTargetPath(ModDirectorRemoteMod mod, RemoteModInformation information) {
        Path installationRoot = director.platform().installationRoot().toAbsolutePath().normalize();

        Path targetFile = (mod.getFolder() == null ?
            director.platform().modFile(information.targetFilename())
            : mod.getFolder().equalsIgnoreCase(".") ?
            director.platform().rootFile(information.targetFilename())
            : director.platform().customFile(information.targetFilename(), mod.getFolder()))
            .toAbsolutePath().normalize();

        if (!targetFile.startsWith(installationRoot)) {
            director.logger().error("Tried to install a file to {0}, which is outside the installation root of {1}!",
                targetFile.toString(), director.platform().installationRoot());
            director.addError(new ModDirectorError(Level.SEVERE,
                "Tried to install a file to " + targetFile + ", which is outside of " +
                    "the installation root " + installationRoot));
            return null;
        }

        return targetFile;
    }

    private Path computeDisabledPath(Path modFile) {
        return modFile.resolveSibling(modFile.getFileName() + ".disabled-by-mod-director");
    }

    private Path computeBansoukouPatchedPath(Path modFile) {
        return modFile.resolveSibling(modFile.getFileName().toString().replace(".jar", "-patched.jar"));
    }

    private Path computeBansoukouDisabledPath(Path modFile) {
        return modFile.resolveSibling(modFile.getFileName().toString().replace(".jar", ".disabled"));
    }

    private boolean isVersionCompliant(ModDirectorRemoteMod mod) {
        String versionMod = mod.getInstallationPolicy().getModpackVersion();
        String versionModpackRemote = director.getModpackRemoteVersion();

        ModpackConfiguration modpackConfiguration = director.getConfigurationController().getModpackConfiguration();
        String versionModpackLocal = null;
        if (modpackConfiguration != null) {
            versionModpackLocal = modpackConfiguration.localVersion();
        }

        if (versionMod != null) {
            if (versionModpackRemote != null) {
                return Objects.equals(versionMod, versionModpackRemote);
            } else if (versionModpackLocal != null) {
                return Objects.equals(versionMod, versionModpackLocal);
            }
        }
        return true;
    }

    public void markDisabledMods(List<InstallableMod> mods) {
        for (InstallableMod mod : mods) {
            try {
                Path disabledFile = computeDisabledPath(mod.getTargetFile());

                Files.createDirectories(disabledFile.getParent());
                Files.createFile(disabledFile);
            } catch (IOException e) {
                director.logger().warn(
                    "Failed to create disabled file, the user might be asked again if he wants to install the mod", e
                );

                director.addError(new ModDirectorError(
                    Level.WARNING,
                    "Failed to create disabled file",
                    e
                ));
            }
        }
    }

    public List<Callable<Void>> createInstallTasks(
        List<InstallableMod> mods,
        BiFunction<String, String, ProgressCallback> callbackFactory
    ) {
        List<Callable<Void>> installTasks = new ArrayList<>();

        for (InstallableMod mod : mods) {
            installTasks.add(() -> {
                handle(mod, callbackFactory.apply(mod.getRemoteInformation().targetFilename(), "Installing"));
                return null;
            });
        }

        return installTasks;
    }

    private void handle(InstallableMod mod, ProgressCallback callback) {
        try {
            ModDirectorRemoteMod remoteMod = mod.getRemoteMod();

            director.logger().debug("Now handling {0} from backend {1}}", remoteMod.offlineName(), remoteMod.remoteType());

            Path targetFile = mod.getTargetFile();

            try {
                Files.createDirectories(targetFile.getParent());
            } catch (IOException e) {
                director.logger().error("Failed to create directory {0}", targetFile.getParent().toString(), e);
                director.addError(new ModDirectorError(Level.SEVERE,
                    "Failed to create directory" + targetFile.getParent().toString(), e));
                return;
            }

            try {
                mod.performInstall(director, callback);
            } catch (ModDirectorException e) {
                director.logger().log(downloadSeverityLevelFor(remoteMod), "Failed to install mod {0}", remoteMod.offlineName(), e);
                director.addError(new ModDirectorError(downloadSeverityLevelFor(remoteMod),
                    "Failed to install mod " + remoteMod.offlineName(), e));
                return;
            }

            if (remoteMod.getMetadata() != null && remoteMod.getMetadata().checkHashes(targetFile, director.platform()) == HashResult.UNMATCHED) {
                director.logger().error("Mod did not match hash after download, aborting!");
                director.addError(new ModDirectorError(Level.SEVERE,
                    "Mod did not match hash after download"));
            } else {
                if (remoteMod.getInstallationPolicy().shouldExtract()) {
                    director.logger().info("Extracted mod file {0}", targetFile.toString());
                } else {
                    director.logger().info("Installed mod file {0}", targetFile.toString());
                }
                director.getInstalledMods().add(new InstalledMod(targetFile, remoteMod.getOptions(), remoteMod.forceInject()));
            }
        } finally {
            callback.done();
        }
    }
}
