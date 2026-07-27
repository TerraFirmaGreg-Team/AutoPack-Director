package team.terrafirmagreg.autopack.core.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.core.configuration.type.ModifyMod;
import team.terrafirmagreg.autopack.core.configuration.type.RemoteConfig;
import team.terrafirmagreg.autopack.core.manage.InstallError;
import team.terrafirmagreg.autopack.core.util.IOOperation;
import team.terrafirmagreg.autopack.core.util.JacksonProvider;
import team.terrafirmagreg.autopack.core.util.WebClient;
import team.terrafirmagreg.autopack.core.util.WebGetResponse;
import team.terrafirmagreg.autopack.ui.WarningDisplay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
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
        Path modpackConfigPath = configurationDirectory.resolve(ConfigFileType.MODPACK.getSuffix());
        if (Files.exists(modpackConfigPath)) {
            loadModpackConfiguration(modpackConfigPath);
        }

        try (Stream<Path> paths = Files.walk(configurationDirectory)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !ConfigFileType.isModpackFileName(p.getFileName().toString()))
                .sorted()
                .forEach(this::addConfig);
        } catch (IOException e) {
            director.getLogger().error("Failed to iterate configuration directory!", e);
            director.addError(new InstallError(Level.SEVERE,
                "Failed to iterate configuration directory", e));
        }
    }

    private void loadModpackConfiguration(Path configurationPath) {
        try {
            modpackConfiguration = readConfig(configurationPath, ConfigFileType.MODPACK);
        } catch (IOException e) {
            handleConfigException(configurationPath, null, e);
            modpackConfiguration = ModpackConfiguration.createDefault();
        }
    }

    private void addConfig(Path configurationPath) {
        director.getLogger().info("Loading config {0}", configurationPath.toString());

        ConfigFileType type = ConfigFileType.fromPath(configurationPath);
        if (type == null) {
            director.getLogger().warn("Ignoring unknown json file {}0", configurationPath.toString());
            return;
        }

        switch (type) {
            case REMOTE:
                handleRemoteConfig(configurationPath);
                break;
            case BUNDLE:
                handleBundleConfig(configurationPath);
                break;
            case MODIFY:
                handleModifyConfig(configurationPath);
                break;
            case CURSE:
            case MODRINTH:
            case URL:
                handleSingleConfig(configurationPath, type);
                break;
            default:
                director.getLogger().warn("Ignoring unknown json file {}0", configurationPath.toString());
                break;
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
        try {
            RemoteConfig remoteConfig = readConfig(configurationPath, ConfigFileType.REMOTE);
            try (WebGetResponse response = WebClient.get(remoteConfig.getUrl())) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                IOOperation.copy(response.getInputStream(), outputStream);
                String fileName = remoteConfig.getUrl().toString()
                    .substring(remoteConfig.getUrl().toString().lastIndexOf('/') + 1);
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
            handleConfigException(configurationPath, null, e);
        }
    }

    private void handleBundleConfig(Path configurationPath) {
        try {
            JsonNode jsonTree = readConfigTree(configurationPath, ConfigFileType.BUNDLE);

            for (ConfigFileType entryType : ConfigFileType.bundleEntryTypes()) {
                JsonNode jsonArray = jsonTree.get(entryType.getSchemaType());
                if (jsonArray == null || !jsonArray.isArray()) {
                    continue;
                }
                for (int i = 0; i < jsonArray.size(); i++) {
                    String context = entryType.getSchemaType() + "[" + i + "]";
                    try {
                        JsonNode entry = stripSchema(jsonArray.get(i).deepCopy());
                        List<String> schemaErrors = ConfigSchemaValidator.validateNode(entry, entryType);
                        if (!schemaErrors.isEmpty()) {
                            reportSchemaErrors(configurationPath, context, schemaErrors);
                            continue;
                        }
                        Object value = OBJECT_MAPPER.treeToValue(entry, entryType.getModelClass());
                        if (entryType == ConfigFileType.MODIFY) {
                            handleModifyConfig((ModifyMod) value);
                        } else {
                            configurations.add((RemoteMod) value);
                        }
                    } catch (IOException e) {
                        handleConfigException(configurationPath, context, e);
                    }
                }
            }
        } catch (IOException e) {
            handleConfigException(configurationPath, null, e);
        }
    }

    private void handleSingleConfig(Path configurationPath, ConfigFileType type) {
        try {
            configurations.add(readConfig(configurationPath, type));
        } catch (IOException e) {
            handleConfigException(configurationPath, null, e);
        }
    }

    private void handleModifyConfig(Path configurationPath) {
        try {
            ModifyMod modifyMod = readConfig(configurationPath, ConfigFileType.MODIFY);
            handleModifyConfig(modifyMod);
        } catch (IOException e) {
            handleConfigException(configurationPath, null, e);
        }
    }

    private void handleModifyConfig(ModifyMod modifyMod) {
        Path installationRoot = director.getPlatform().installationRoot().toAbsolutePath().normalize();
        Path modifyModFolderPath = installationRoot.resolve(modifyMod.folder());

        if (modifyMod.fileName() == null) {
            if (Files.isDirectory(modifyModFolderPath) && modifyMod.delete()) {
                director.getLogger().info("Deleting folder {0}", modifyModFolderPath);
                try (Stream<Path> paths = Files.walk(modifyModFolderPath)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(this::safeDelete);
                } catch (IOException e) {
                    handleConfigException(null, "modify folder " + modifyModFolderPath, e);
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
            handleConfigException(null, "modify " + modifyModFilePath, e);
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

    @SuppressWarnings("unchecked")
    private <T> T readConfig(Path configurationPath, ConfigFileType type) throws IOException {
        JsonNode tree = readConfigTree(configurationPath, type);
        return (T) OBJECT_MAPPER.treeToValue(tree, type.getModelClass());
    }

    private JsonNode readConfigTree(Path configurationPath, ConfigFileType type) throws IOException {
        try (InputStream stream = Files.newInputStream(configurationPath)) {
            JsonNode tree = stripSchema(OBJECT_MAPPER.readTree(stream));
            List<String> schemaErrors = ConfigSchemaValidator.validateNode(tree, type);
            if (!schemaErrors.isEmpty()) {
                throw new ConfigSchemaException(String.join("; ", schemaErrors));
            }
            return tree;
        }
    }

    public static JsonNode stripSchema(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove("$schema");
        }
        return node;
    }

    private void reportSchemaErrors(Path configurationPath, String context, List<String> schemaErrors) {
        handleConfigException(configurationPath, context, new ConfigSchemaException(String.join("; ", schemaErrors)));
    }

    private void handleConfigException(Path path, String context, Exception e) {
        String action = e instanceof JsonProcessingException || e instanceof ConfigSchemaException
            ? "parse" : "open";
        String location = formatLocation(path, context);
        String message = "Failed to " + action + " " + location
            + (e.getMessage() != null ? ": " + e.getMessage() : "");
        director.getLogger().error(message, e);
        director.addError(new InstallError(Level.SEVERE, message, e));
    }

    private static String formatLocation(Path path, String context) {
        StringBuilder location = new StringBuilder();
        if (path != null) {
            location.append(path.getFileName() != null ? path.getFileName().toString() : path.toString());
        } else {
            location.append("configuration");
        }
        if (context != null && !context.isEmpty()) {
            location.append(" → ").append(context);
        }
        return location.toString();
    }

    @FunctionalInterface
    private interface FileOperation {
        void run() throws IOException;
    }
}
