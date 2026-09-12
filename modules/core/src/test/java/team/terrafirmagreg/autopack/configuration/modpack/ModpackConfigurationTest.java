package team.terrafirmagreg.autopack.configuration.modpack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.autopack.configuration.ConfigurationController;
import tools.jackson.databind.JsonNode;

class ModpackConfigurationTest {

    @Test
    void parsesPromptConfigCreateFalse() throws IOException {
        ModpackConfiguration config = readFixture("config/pakku/prompts.json");

        assertFalse(config.pakku().promptConfigCreate());
        assertTrue(config.pakku().promptConfigUpdate());
        assertTrue(config.pakku().promptMissingMods());
        assertTrue(config.promptInstallConsent());
        assertTrue(config.promptOptionalMods());
    }

    @Test
    void defaultsWithoutPakkuBlock() throws IOException {
        ModpackConfiguration config = readFixture("config/modpack.json");

        assertTrue(config.pakku().promptConfigCreate());
        assertTrue(config.pakku().promptConfigUpdate());
        assertTrue(config.pakku().promptMissingMods());
        assertTrue(config.promptInstallConsent());
        assertTrue(config.promptOptionalMods());
        assertTrue(config.checkStopModReposts());
    }

    private static ModpackConfiguration readFixture(String resource) throws IOException {
        try (InputStream stream =
                ModpackConfigurationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            JsonNode tree = ConfigurationController.stripSchema(ConfigurationController.OBJECT_MAPPER.readTree(stream));
            return ConfigurationController.OBJECT_MAPPER.treeToValue(tree, ModpackConfiguration.class);
        }
    }
}
