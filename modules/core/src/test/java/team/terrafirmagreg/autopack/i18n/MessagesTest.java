package team.terrafirmagreg.autopack.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessagesTest {
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<Map<String, String>>() {
    };

    @Test
    void formatsPlaceholder() {
        Messages messages = new Messages("en_us");

        assertEquals("Installing Example Pack", messages.get("autopack.progress.installing", "Example Pack"));
    }

    @Test
    void fallsBackToEnglishForMissingLocaleKeys() {
        Messages messages = new Messages("fr_fr");

        assertEquals("Next", messages.get("autopack.ui.selection.next"));
    }

    @Test
    void overlaysLocaleTranslations() throws IOException {
        Map<String, String> ptBr = readLang("pt_br");
        Map<String, String> enUs = readLang("en_us");
        Messages messages = new Messages("pt_br");

        assertEquals(ptBr.get("autopack.ui.selection.next"), messages.get("autopack.ui.selection.next"));
        assertEquals(enUs.get("autopack.dialog.outdated.button"), messages.get("autopack.dialog.outdated.button"));
    }

    @Test
    void returnsKeyWhenMissing() {
        Messages messages = new Messages("en_us");

        assertEquals("autopack.missing.key", messages.get("autopack.missing.key"));
        assertEquals("autopack.missing.key:[value]", messages.get("autopack.missing.key", "value"));
    }

    private static Map<String, String> readLang(String languageCode) throws IOException {
        String path = String.format("/assets/autopack/lang/%s.json", languageCode);
        try (InputStream inputStream = Messages.class.getResourceAsStream(path)) {
            assertNotNull(inputStream, path);
            return ConfigurationController.OBJECT_MAPPER.readValue(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                    MAP_TYPE);
        }
    }
}
