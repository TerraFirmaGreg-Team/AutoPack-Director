package team.terrafirmagreg.autopack.core.manage;

import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.core.manage.install.InstalledMod;
import team.terrafirmagreg.autopack.core.util.HashResult;
import team.terrafirmagreg.autopack.core.util.NetworkExceptions;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InstallController {

    private final Director director;

    public InstallController(Director director) {
        this.director = director;
    }

    private Level downloadSeverityLevelFor(RemoteMod mod) {
        return mod.getInstallationPolicy().continueOnFailedDownload() ?
            Level.WARNING : Level.SEVERE;
    }

    public List<Callable<Void>> createPreInstallTasks(
        List<RemoteMod> allMods,
        List<RemoteMod> excludedMods,
        List<InstallableMod> freshMods,
        List<InstallableMod> reinstallMods,
        BiFunction<String, String, ProgressCallback> callbackFactory
    ) {
        List<Callable<Void>> preInstallTasks = new ArrayList<>();

        for (RemoteMod mod : allMods) {
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

                String offlineFileName = mod.offlineTargetFilename();
                if (offlineFileName != null && !offlineFileName.isEmpty()) {
                    RemoteModInformation offlineInformation =
                        new RemoteModInformation(offlineFileName, offlineFileName);
                    Path offlineTarget = computeInstallationTargetPath(mod, offlineInformation);
                    if (offlineTarget == null) {
                        callback.done();
                        return null;
                    }

                    Path disabledFile = computeDisabledPath(offlineTarget);
                    if (Files.isRegularFile(disabledFile) || !isVersionCompliant(mod)) {
                        excludedMods.add(mod);
                        callback.done();
                        return null;
                    }

                    if (!mod.getInstallationPolicy().downloadAlways()
                        && canSkipExistingInstall(mod, offlineTarget)) {
                        director.logger().debug(
                            "Skipping remote query for {0}; local file exists at {1}",
                            mod.offlineName(),
                            offlineTarget
                        );
                        excludedMods.add(mod);
                        callback.done();
                        return null;
                    }
                }

                callback.message("Querying mod information");

                RemoteModInformation information;

                try {
                    information = mod.queryInformation();
                } catch (InstallException e) {
                    String reason = NetworkExceptions.isConnectivityError(e)
                        ? " (" + NetworkExceptions.describe(e) + ")" : "";
                    director.logger().error("Failed to query information for {0} from {1}}",
                        mod.offlineName(), mod.remoteType(), e);
                    director.addError(new InstallError(downloadSeverityLevelFor(mod),
                        "Failed to query information for mod " + mod.offlineName() + " from " + mod.remoteType()
                            + reason,
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

                } else if (mod.getInstallationPolicy().downloadAlways() && Files.isRegularFile(targetFile)) {
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

                if (!excludedMods.contains(mod)) {
                    List<String> patterns = mod.getInstallationPolicy().allSupersedePatterns();
                    if (!patterns.isEmpty()) {
                        Path targetDir = targetFile.getParent();
                        FileSystem fs = targetDir.getFileSystem();
                        List<PathMatcher> matchers = patterns.stream()
                            .map(p -> fs.getPathMatcher("glob:" + p))
                            .collect(Collectors.toList());
                        try (Stream<Path> entries = Files.list(targetDir)) {
                            entries
                                .filter(Files::isRegularFile)
                                .filter(p -> !p.equals(targetFile))
                                .filter(p -> matchers.stream().anyMatch(m -> m.matches(p.getFileName())))
                                .forEach(old -> {
                                    try {
                                        if (mod.getInstallationPolicy().deleteSuperseded()) {
                                            Files.delete(old);
                                            director.logger().info("Deleted superseded file {0}", old);
                                        } else {
                                            Path disabled = old.resolveSibling(old.getFileName() + ".disabled-by-mod-director");
                                            Files.deleteIfExists(disabled);
                                            Files.move(old, disabled);
                                            director.logger().info("Disabled superseded file {0}", old);
                                        }
                                    } catch (IOException e) {
                                        director.logger().warn("Failed to process superseded file {0}", old, e);
                                    }
                                });
                        } catch (IOException e) {
                            director.logger().warn("Failed to scan directory for superseded files {0}", targetDir, e);
                        }
                    }
                }

                callback.done();
                return null;
            });
        }

        return preInstallTasks;
    }

    private boolean canSkipExistingInstall(RemoteMod mod, Path targetFile) {
        Path bansoukouPatchedFile = computeBansoukouPatchedPath(targetFile);
        Path bansoukouDisabledFile = computeBansoukouDisabledPath(targetFile);
        boolean exists = Files.isRegularFile(targetFile)
            || (Files.isRegularFile(bansoukouPatchedFile) && Files.isRegularFile(bansoukouDisabledFile));
        if (!exists) {
            return false;
        }
        if (mod.getMetadata() == null) {
            return true;
        }
        HashResult hashResult = mod.getMetadata().checkHashes(
            Files.isRegularFile(targetFile) ? targetFile : bansoukouDisabledFile,
            director.platform()
        );
        return hashResult == HashResult.UNKNOWN || hashResult == HashResult.MATCHED;
    }

    private Path computeInstallationTargetPath(RemoteMod mod, RemoteModInformation information) {
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
            director.addError(new InstallError(Level.SEVERE,
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

    private boolean isVersionCompliant(RemoteMod mod) {
        String versionMod = mod.getInstallationPolicy().modpackVersion();
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
                Path disabledFile = computeDisabledPath(mod.targetFile());

                Files.createDirectories(disabledFile.getParent());
                Files.createFile(disabledFile);
            } catch (IOException e) {
                director.logger().warn(
                    "Failed to create disabled file, the user might be asked again if he wants to install the mod", e
                );

                director.addError(new InstallError(
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
                handle(mod, callbackFactory.apply(mod.remoteInformation().targetFilename(), "Installing"));
                return null;
            });
        }

        return installTasks;
    }

    private void handle(InstallableMod mod, ProgressCallback callback) {
        try {
            RemoteMod remoteMod = mod.remoteMod();

            director.logger().debug("Now handling {0} from backend {1}}", remoteMod.offlineName(), remoteMod.remoteType());

            Path targetFile = mod.targetFile();

            try {
                Files.createDirectories(targetFile.getParent());
            } catch (IOException e) {
                director.logger().error("Failed to create directory {0}", targetFile.getParent().toString(), e);
                director.addError(new InstallError(Level.SEVERE,
                    "Failed to create directory" + targetFile.getParent().toString(), e));
                return;
            }

            try {
                mod.performInstall(director, callback);
            } catch (InstallException e) {
                String reason = NetworkExceptions.isConnectivityError(e)
                    ? " (" + NetworkExceptions.describe(e) + ")" : "";
                director.logger().log(downloadSeverityLevelFor(remoteMod), "Failed to install mod {0}", remoteMod.offlineName(), e);
                director.addError(new InstallError(downloadSeverityLevelFor(remoteMod),
                    "Failed to install mod " + remoteMod.offlineName() + reason, e));
                return;
            }

            if (remoteMod.getMetadata() != null && remoteMod.getMetadata().checkHashes(targetFile, director.platform()) == HashResult.UNMATCHED) {
                director.logger().error("Mod did not match hash after download, aborting!");
                director.addError(new InstallError(Level.SEVERE,
                    "Mod did not match hash after download"));
            } else {
                if (remoteMod.getInstallationPolicy().extract()) {
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
