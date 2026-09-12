package team.terrafirmagreg.autopack.ui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.formdev.flatlaf.FlatLaf;
import org.junit.jupiter.api.Test;

class UIThemeTest {

    @Test
    void fallbackForUnknownSimpleNameIsLightLaf() {
        FlatLaf laf = UITheme.forName("does-not-exist");

        assertNotNull(laf);
        assertTrue(laf.getName().toLowerCase().contains("light"));
    }
}
