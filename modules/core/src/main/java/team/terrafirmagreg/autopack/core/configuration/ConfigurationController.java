package team.terrafirmagreg.autopack.core.configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import team.terrafirmagreg.autopack.Director;
import lombok.Getter;
import team.terrafirmagreg.autopack.core.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.core.configuration.type.*;
import team.terrafirmagreg.autopack.core.manage.InstallError;
import team.terrafirmagreg.autopack.core.util.IOOperation;
import team.terrafirmagreg.autopack.core.util.JacksonProvider;
import team.terrafirmagreg.autopack.core.util.WebClient;
import team.terrafirmagreg.autopack.core.util.WebGetResponse;
import team.terrafirmagreg.autopack.ui.WarningDisplay;

import java.io.*;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public class ConfigurationController {
    public static final ObjectMapper OBJECT_MAPPER = JacksonProvider.getObjectMapper();
    private final Director director;
    private final Path configurationDirectory;
    @Getter
    private final List<RemoteMod> configurations;
    @Getter
    private ModpackConfiguration modpackConfiguration;

    public ConfigurationController(Director director, Path configurationDirectory) {
        this.director = director;
        this.configurationDirectory = configurationDirectory;
        this.configurations = new ArrayList<>();
    }

    public void load() {
        Path modpackConfigPath = configurationDirectory.resolve("modpack.json");
        if (Files.exists(modpackConfigPath) && !loadModpackConfiguration(modpackConfigPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(configurationDirectory)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !p.getFileName().toString().equals("modpack.json"))
                .sorted()
                .forEach(this::addConfig);
        } catch (IOException e) {
            director.getLogger().error("Failed to iterate configuration directory!", e);
            director.addError(new InstallError(Level.SEVERE,
                "Failed to iterate configuration directory", e));
        }
    }

    private boolean loadModpackConfiguration(Path configurationPath) {
        try (InputStream stream = Files.newInputStream(configurationPath)) {
            modpackConfiguration = OBJECT_MAPPER.readValue(stream, ModpackConfiguration.class);
            return true;
        } catch (IOException e) {
            director.getLogger().error("Failed to read modpack configuration!", e);
            director.addError(new InstallError(Level.SEVERE,
                "Failed to read modpack configuration!"));
            return false;
        }
    }

    private void addConfig(Path configurationPath) {
        String configString = configurationPath.toString();

        director.getLogger().info("Loading config {0}", configString);

        if (configString.endsWith(".remote.json")) {
            handleRemoteConfig(configurationPath);
        } else if (configString.endsWith(".bundle.json")) {
            handleBundleConfig(configurationPath);
        } else if (configString.endsWith(".modify.json")) {
            handleModifyConfig(configurationPath);
        } else {
            handleSingleConfig(configurationPath);
        }
    }

    private void safeDelete(Path file) {
        int attempts = 5;
        while (attempts-- > 0) {
            try {
                Files.delete(file);
                return;
            } catch (FileSystemException e) {
                if (attempts == 0) {
                    String message = String.format("Could not delete file %s after multiple attempts: %s",
                        file.getFileName(), e.getMessage());
                    director.getLogger().warn(message);
                    WarningDisplay.show("The mod \"" + file.getFileName() + "\" could not be deleted.\n\n" +
                        "It may be locked by another process (e.g., Minecraft).\n\n" +
                        "Press OK to continue.\n\nReason: " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (IOException e) {
                String message = String.format("Unexpected IO error deleting file %s: %s",
                    file.getFileName(), e.getMessage());
                director.getLogger().warn(message);
                WarningDisplay.show("The mod \"" + file.getFileName() + "\" could not be deleted due to an unexpected error.\n\n" +
                    "Press OK to continue.\n\nReason: " + e.getMessage());
                return;
            }
        }
    }

    private void safeModify(FileOperation action, String contextDescription, Path sourcePath, Path targetPath) {
        int attempts = 5;
        while (attempts-- > 0) {
            try {
                action.run();
                return;
            } catch (IOException e) {
                if (attempts == 0) {
                    String message = String.format(
                        "ERROR: Failed to perform operation: %s%n%nSource:%n  %s%n%nTarget:%n  %s%n%nReason:%n  %s",
                        contextDescription,
                        sourcePath,
                        targetPath != null ? targetPath : "(unknown)",
                        e.getMessage()
                    );
                    director.getLogger().warn(message);
                    WarningDisplay.show(message);
                    return;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleRemoteConfig(Path configurationPath) {
        try (InputStream stream = Files.newInputStream(configurationPath)) {
            RemoteConfig remoteConfig = OBJECT_MAPPER.readValue(stream, RemoteConfig.class);
            try (WebGetResponse response = WebClient.get(remoteConfig.getUrl())) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                IOOperation.copy(response.getInputStream(), outputStream);
                String fileName = remoteConfig.getUrl().toString().substring(remoteConfig.getUrl().toString().lastIndexOf('/') + 1);
                Path installationRoot = director.getPlatform().installationRoot().toAbsolutePath().normalize();
                Path remoteConfigPath = installationRoot.resolve(configurationDirectory).resolve(fileName);
                Files.write(remoteConfigPath, outputStream.toByteArray());
                addConfig(remoteConfigPath);
                safeDelete(remoteConfigPath);
            } catch (UnknownHostException e) {
                director.getLogger().error("Failed to resolve URL {0}, skipping remote config...",
                    remoteConfig.getUrl(), e);
            }
        } catch (IOException e) {
            handleConfigException(e);
        }
    }

    private void handleBundleConfig(Path configurationPath) {
        try (InputStream stream = Files.newInputStream(configurationPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var jsonTree = OBJECT_MAPPER.readTree(reader);

            var jsonArray = jsonTree.get("curse");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    configurations.add(OBJECT_MAPPER.treeToValue(jsonNode, CurseRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("modrinth");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    configurations.add(OBJECT_MAPPER.treeToValue(jsonNode, ModrinthRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("url");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    configurations.add(OBJECT_MAPPER.treeToValue(jsonNode, UrlRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("modify");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    handleModifyConfig(OBJECT_MAPPER.treeToValue(jsonNode, ModifyMod.class));
                }
            }
        } catch (IOException e) {
            handleConfigException(e);
        }
    }

    private void handleSingleConfig(Path configurationPath) {
        Class<? extends RemoteMod> targetType = getTypeForFile(configurationPath);
        if (targetType != null) {
            try (InputStream stream = Files.newInputStream(configurationPath)) {
                configurations.add(OBJECT_MAPPER.readValue(stream, targetType));
            } catch (IOException e) {
                handleConfigException(e);
            }
        }
    }

    private void handleModifyConfig(Path configurationPath) {
        try (InputStream stream = Files.newInputStream(configurationPath)) {
            ModifyMod modifyMod = OBJECT_MAPPER.readValue(stream, ModifyMod.class);
            handleModifyConfig(modifyMod);
        } catch (IOException e) {
            handleConfigException(e);
        }
    }

    private void handleModifyConfig(ModifyMod modifyMod) {
        Path installationRoot = director.getPlatform().installationRoot().toAbsolutePath().normalize();
        Path modifyModFolderPath = installationRoot.resolve(modifyMod.folder());

        if (modifyMod.fileName() == null) {
            if (Files.isDirectory(modifyModFolderPath) && modifyMod.delete()) {
                director.getLogger().info("Deleting folder {0}", modifyModFolderPath);
                try (Stream<Path> paths = Files.walk(modifyModFolderPath)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> safeDelete(path));
                } catch (IOException e) {
                    handleConfigException(e);
                }
            }
            return;
        }

        Path modifyModFilePath = modifyModFolderPath.resolve(modifyMod.fileName());
        Path targetPath = null;

        try {
            if (Files.isRegularFile(modifyModFilePath)) {
                if (modifyMod.disable()) {
                    targetPath = modifyModFilePath.resolveSibling(modifyMod.fileName() + ".disabled-by-mod-director");
                } else if (!modifyMod.delete()) {
                    if (modifyMod.newFolder() != null) {
                        Path newFolder = installationRoot.resolve(modifyMod.newFolder());
                        Files.createDirectories(newFolder);
                        director.getLogger().info("Moving file {0}", modifyModFilePath);
                        targetPath = newFolder.resolve(modifyMod.fileName());
                    }

                    if (modifyMod.newFileName() != null) {
                        director.getLogger().info("Renaming file {0}", modifyModFilePath);
                        targetPath = targetPath != null
                            ? targetPath.resolveSibling(modifyMod.newFileName())
                            : modifyModFilePath.resolveSibling(modifyMod.newFileName());
                    }
                }
            }
        } catch (IOException e) {
            handleConfigException(e);
            return;
        }

        Path finalTarget = targetPath;

        safeModify(() -> {
            if (!Files.isRegularFile(modifyModFilePath)) {
                return;
            }
            if (modifyMod.disable()) {
                director.getLogger().info("Disabling file {0}", modifyModFilePath);
                Files.move(modifyModFilePath, finalTarget);
            } else if (modifyMod.delete()) {
                director.getLogger().info("Deleting file {0}", modifyModFilePath);
                safeDelete(modifyModFilePath);
            } else if (finalTarget != null) {
                if (Files.exists(finalTarget)) {
                    Path disabledFilePath = finalTarget.resolveSibling(
                        finalTarget.getFileName() + ".disabled-by-mod-director");
                    if (Files.exists(disabledFilePath)) {
                        safeDelete(disabledFilePath);
                    }
                    Files.move(finalTarget, disabledFilePath);
                }
                Files.move(modifyModFilePath, finalTarget);
            }
        }, "Mod file modification for " + modifyModFilePath.getFileName(), modifyModFilePath, finalTarget);
    }

    private void handleConfigException(Exception e) {
        director.getLogger().error("Failed to {0} a configuration for reading!", (e instanceof JsonParseException ? "parse" : "open"), e);
        director.addError(new InstallError(Level.SEVERE,
            "Failed to " + (e instanceof JsonParseException ? "parse" : "open") + " a configuration for reading", e));
    }

    private Class<? extends RemoteMod> getTypeForFile(Path file) {
        String name = file.toString();
        if (name.endsWith(".curse.json")) {
            return CurseRemoteMod.class;
        } else if (name.endsWith(".modrinth.json")) {
            return ModrinthRemoteMod.class;
        } else if (name.endsWith(".url.json")) {
            return UrlRemoteMod.class;
        } else {
            director.getLogger().warn("Ignoring unknown json file {}0", name);
            return null;
        }
    }

    @FunctionalInterface
    private interface FileOperation {
        void run() throws IOException;
    }
}
