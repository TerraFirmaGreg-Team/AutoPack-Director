package team.terrafirmagreg.autopack.core.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.type.CurseRemoteMod;
import team.terrafirmagreg.autopack.core.configuration.type.ModrinthRemoteMod;
import team.terrafirmagreg.autopack.core.configuration.type.UrlRemoteMod;
import team.terrafirmagreg.autopack.core.manage.InstallError;
import team.terrafirmagreg.autopack.testsupport.DirectorTestSupport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReadsModpackAndUrlConfig() throws Exception {
        copyResources("modpack.json", "example.url.json");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertNotNull(director.getConfigurationController().getModpackConfiguration());
        assertEquals("Test Pack", director.getConfigurationController().getModpackConfiguration().packName());
        assertEquals(1, director.getConfigurationController().getConfigurations().size());
        assertTrue(director.getConfigurationController().getConfigurations().get(0) instanceof UrlRemoteMod);
        assertEquals("example download", director.getConfigurationController().getConfigurations().get(0).getComment());
    }

    @Test
    void loadReadsSingleTypeConfigs() throws Exception {
        copyResources("example.curse.json", "example.modrinth.json");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertEquals(2, director.getConfigurationController().getConfigurations().size());
        assertTrue(containsType(director, CurseRemoteMod.class));
        assertTrue(containsType(director, ModrinthRemoteMod.class));
    }

    @Test
    void loadReadsBundleConfig() throws Exception {
        copyResource("example.bundle.json", "pack.bundle.json");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertEquals(3, director.getConfigurationController().getConfigurations().size());
    }

    @Test
    void invalidJsonAddsSevereError() throws Exception {
        copyResource("invalid.json", "broken.url.json");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.hasFatalError());
        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertEquals(Level.SEVERE, error.getLevel());
        assertTrue(error.getMessage().contains("parse"));
        assertTrue(error.getMessage().contains("broken.url.json"));
    }

    @Test
    void unknownJsonFileIsIgnored() throws Exception {
        Path configDir = configDirectory();
        Files.createDirectories(configDir);
        Files.write(configDir.resolve("unknown.json"), "{\"ignored\": true}".getBytes(StandardCharsets.UTF_8));
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.getConfigurationController().getConfigurations().isEmpty());
        assertFalse(director.hasFatalError());
    }

    @Test
    void missingRequiredFieldReportsFileName() throws Exception {
        writeConfig("missing.curse.json", "{\"fileId\": 1}");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.hasFatalError());
        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertTrue(error.getMessage().contains("missing.curse.json"));
        assertTrue(error.getMessage().contains("addonId") || error.getMessage().toLowerCase().contains("required"));
    }

    @Test
    void unknownKeyReportsParseError() throws Exception {
        writeConfig("typo.curse.json", "{\"addonId\": 1, \"fileId\": 2, \"addonID\": 3}");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.hasFatalError());
        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertTrue(error.getMessage().contains("typo.curse.json"));
    }

    @Test
    void missingUrlFailsFast() throws Exception {
        writeConfig("missing.url.json", "{\"fileName\": \"mod.jar\"}");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.hasFatalError());
        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertTrue(error.getMessage().contains("missing.url.json"));
    }

    @Test
    void invalidModpackFallsBackAndContinuesLoading() throws Exception {
        writeConfig("modpack.json", "{\"packName\": 123}");
        copyResource("example.url.json", "example.url.json");
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.hasFatalError());
        assertEquals("Modpack Director", director.getConfigurationController().getModpackConfiguration().packName());
        assertEquals(1, director.getConfigurationController().getConfigurations().size());
    }

    @Test
    void bundleSkipsBadEntryAndLoadsValidOnes() throws Exception {
        writeConfig("mixed.bundle.json", """
            {
              "curse": [
                { "addonId": 1, "fileId": 2 },
                { "fileId": 3 }
              ],
              "url": [
                { "url": "https://example.com/ok.jar", "fileName": "ok.jar" }
              ]
            }
            """);
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.hasFatalError());
        assertEquals(2, director.getConfigurationController().getConfigurations().size());
        InstallError error = director.getErrors().stream()
            .filter(e -> e.getMessage().contains("curse[1]"))
            .findFirst()
            .orElse(null);
        assertNotNull(error);
    }

    @Test
    void schemaPropertyIsStrippedBeforeParse() throws Exception {
        writeConfig("with-schema.curse.json", """
            {
              "$schema": "./schemas/curse.schema.json",
              "addonId": 1,
              "fileId": 2
            }
            """);
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertFalse(director.hasFatalError());
        assertEquals(1, director.getConfigurationController().getConfigurations().size());
    }

    @Test
    void validateRunnerAcceptsExampleConfigs() throws Exception {
        copyResources("modpack.json", "example.url.json", "example.curse.json", "example.modrinth.json", "example.bundle.json");
        int code = ConfigValidationRunner.validate(configDirectory());
        assertEquals(0, code);
    }

    private void writeConfig(String name, String content) throws IOException {
        Path configDir = configDirectory();
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve(name), content);
    }

    private void copyResources(String... resources) throws IOException {
        for (String resource : resources) {
            copyResource(resource, resource);
        }
    }

    private void copyResource(String resource, String targetName) throws IOException {
        Path configDir = configDirectory();
        Files.createDirectories(configDir);
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config/" + resource)) {
            if (inputStream == null) {
                throw new IOException("Missing test resource config/" + resource);
            }
            Files.copy(inputStream, configDir.resolve(targetName));
        }
    }

    private Path configDirectory() {
        return tempDir.resolve("config").resolve("mod-director");
    }

    private static boolean containsType(Director director, Class<?> type) {
        for (RemoteMod configuration : director.getConfigurationController().getConfigurations()) {
            if (type.isInstance(configuration)) {
                return true;
            }
        }
        return false;
    }
}
