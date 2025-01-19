package net.jan.moddirector.core.configuration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jan.moddirector.ModpackDirector;
import lombok.Getter;
import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.jan.moddirector.core.configuration.type.*;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.util.IOOperation;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public class ConfigurationController {
    public static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private final ModpackDirector director;
    private final Path configurationDirectory;
    @Getter
    private final List<ModDirectorRemoteMod> configurations;
    @Getter
    private ModpackConfiguration modpackConfiguration;

    public ConfigurationController(ModpackDirector director, Path configurationDirectory) {
        this.director = director;
        this.configurationDirectory = configurationDirectory;
        this.configurations = new ArrayList<>();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper instance = new ObjectMapper();
        instance.setDefaultLeniency(false);
        instance.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        return instance;
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
            director.addError(new ModDirectorError(Level.SEVERE,
                "Failed to iterate configuration directory", e));
        }
    }

    private boolean loadModpackConfiguration(Path configurationPath) {
        try (InputStream stream = Files.newInputStream(configurationPath)) {
            modpackConfiguration = OBJECT_MAPPER.readValue(stream, ModpackConfiguration.class);
            return true;
        } catch (IOException e) {
            director.getLogger().error("Failed to read modpack configuration!", e);
            director.addError(new ModDirectorError(Level.SEVERE,
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
                Files.delete(remoteConfigPath);
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
                    configurations.add(OBJECT_MAPPER.convertValue(jsonNode, CurseRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("modrinth");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    configurations.add(OBJECT_MAPPER.convertValue(jsonNode, ModrinthRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("url");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    configurations.add(OBJECT_MAPPER.convertValue(jsonNode, UrlRemoteMod.class));
                }
            }

            jsonArray = jsonTree.get("modify");
            if (jsonArray != null && jsonArray.isArray()) {
                for (JsonNode jsonNode : jsonArray) {
                    handleModifyConfig(OBJECT_MAPPER.convertValue(jsonNode, ModifyMod.class));
                }
            }
        } catch (IOException e) {
            handleConfigException(e);
        }
    }

    private void handleSingleConfig(Path configurationPath) {
        Class<? extends ModDirectorRemoteMod> targetType = getTypeForFile(configurationPath);
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
        try {
            Path installationRoot = director.getPlatform().installationRoot().toAbsolutePath().normalize();
            Path modifyModFolderPath = installationRoot.resolve(modifyMod.getFolder());
            if (modifyMod.getFileName() == null) {
                if (Files.isDirectory(modifyModFolderPath) && modifyMod.shouldDelete()) {
                    director.getLogger().info("Deleting folder {0}", modifyModFolderPath);
                    try (Stream<Path> paths = Files.walk(modifyModFolderPath)) {
                        paths.forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                    Files.deleteIfExists(modifyModFolderPath);
                }
            } else {
                Path modifyModFilePath = modifyModFolderPath.resolve(modifyMod.getFileName());
                if (Files.isRegularFile(modifyModFilePath)) {
                    if (modifyMod.shouldDisable()) {
                        director.getLogger().info("Disabling file {0}", modifyModFilePath);
                        Files.move(modifyModFilePath, modifyModFilePath.resolveSibling(modifyMod.getFileName() + ".disabled-by-mod-director"));
                    } else if (modifyMod.shouldDelete()) {
                        director.getLogger().info("Deleting file {0}", modifyModFilePath);
                        Files.delete(modifyModFilePath);
                    } else {
                        Path modifyModNewFilePath = null;
                        if (modifyMod.getNewFolder() != null) {
                            director.getLogger().info("Moving file {0}", modifyModFilePath);
                            modifyModFolderPath = installationRoot.resolve(modifyMod.getNewFolder());
                            Files.createDirectories(modifyModFolderPath);
                            modifyModNewFilePath = modifyModFolderPath.resolve(modifyMod.getFileName());
                        }
                        if (modifyMod.getNewFileName() != null) {
                            director.getLogger().info("Renaming file {0}", modifyModFilePath);
                            modifyModNewFilePath = modifyModNewFilePath != null // Moved before?
                                ? modifyModNewFilePath.resolveSibling(modifyMod.getNewFileName()) // Yes -> Use new folder
                                : modifyModFilePath.resolveSibling(modifyMod.getNewFileName()); // No -> Use old folder
                        }
                        if (modifyModNewFilePath != null) {
                            if (Files.exists(modifyModNewFilePath)) {
                                Path disabledFilePath = modifyModNewFilePath.resolveSibling(modifyModNewFilePath.getFileName() + ".disabled-by-mod-director");
                                if (Files.exists(disabledFilePath)) {
                                    Files.delete(disabledFilePath);
                                }
                                Files.move(modifyModNewFilePath, disabledFilePath);
                            }
                            Files.move(modifyModFilePath, modifyModNewFilePath);
                        }
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            handleConfigException(e);
        }
    }

    private void handleConfigException(Exception e) {
        director.getLogger().error("Failed to {0} a configuration for reading!", (e instanceof JsonParseException ? "parse" : "open"), e);
        director.addError(new ModDirectorError(Level.SEVERE,
            "Failed to " + (e instanceof JsonParseException ? "parse" : "open") + " a configuration for reading", e));
    }

    private Class<? extends ModDirectorRemoteMod> getTypeForFile(Path file) {
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
}
