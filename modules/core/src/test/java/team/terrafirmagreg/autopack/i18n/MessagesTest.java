package team.terrafirmagreg.autopack.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {

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
    void overlaysLocaleTranslations() {
        Messages messages = new Messages("pt_br");

        assertEquals("Próximo", messages.get("autopack.ui.selection.next"));
        assertEquals("Ok", messages.get("autopack.dialog.outdated.button"));
    }

    @Test
    void returnsKeyWhenMissing() {
        Messages messages = new Messages("en_us");

        assertEquals("autopack.missing.key", messages.get("autopack.missing.key"));
        assertEquals("autopack.missing.key:[value]", messages.get("autopack.missing.key", "value"));
    }
}
