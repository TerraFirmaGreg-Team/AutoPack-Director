package team.terrafirmagreg.autopack.locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.autopack.configuration.ConfigurationController;
import tools.jackson.core.type.TypeReference;

class MessagesTest {
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<Map<String, String>>() {};

    @Test
    void formatsPlaceholder() {
        Messages.init("en_us");

        assertEquals("Installing Example Pack", Messages.get("autopack.progress.installing", "Example Pack"));
        assertEquals("Following redirect 2 out of 5", Messages.get("autopack.progress.follow_redirect", 2, 5));
    }

    @Test
    void fallsBackToEnglishForMissingLocaleKeys() {
        Messages.init("fr_fr");

        assertEquals("Next", Messages.get("autopack.ui.selection.next"));
    }

    @Test
    void overlaysRussianTranslations() throws IOException {
        Map<String, String> ruRu = readLang("ru_ru");
        Messages.init("ru_ru");

        assertEquals(ruRu.get("autopack.ui.selection.next"), Messages.get("autopack.ui.selection.next"));
        assertEquals(ruRu.get("autopack.error.page.title"), Messages.get("autopack.error.page.title"));
        assertEquals("Установка Test Pack", Messages.get("autopack.progress.installing", "Test Pack"));
    }

    @Test
    void returnsKeyWhenMissing() {
        Messages.init("en_us");

        assertEquals("autopack.missing.key", Messages.get("autopack.missing.key"));
        assertEquals("autopack.missing.key:[value]", Messages.get("autopack.missing.key", "value"));
    }

    @Test
    void formatsErrorMessages() {
        Messages.init("en_us");

        assertEquals(
                "Could not download and install \"JEI\".", Messages.get("autopack.error.install_failed", "JEI", ""));
        assertTrue(Messages.get("autopack.error.config_parse", "broken.json", "invalid json")
                .contains("parse"));
    }

    private static Map<String, String> readLang(String languageCode) throws IOException {
        String path = String.format("/assets/autopack/lang/%s.json", languageCode);
        try (InputStream inputStream = Messages.class.getResourceAsStream(path)) {
            assertNotNull(inputStream, path);
            return ConfigurationController.OBJECT_MAPPER.readValue(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8), MAP_TYPE);
        }
    }
}
