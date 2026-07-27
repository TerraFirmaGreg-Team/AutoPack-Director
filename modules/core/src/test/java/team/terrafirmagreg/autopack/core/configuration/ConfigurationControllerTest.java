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
    }

    @Test
    void unknownJsonFileIsIgnored() throws Exception {
        Path configDir = configDirectory();
        Files.createDirectories(configDir);
        Files.write(configDir.resolve("unknown.json"), "{\"ignored\": true}".getBytes());
        Director director = DirectorTestSupport.create(tempDir);

        director.getConfigurationController().load();

        assertTrue(director.getConfigurationController().getConfigurations().isEmpty());
        assertFalse(director.hasFatalError());
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
