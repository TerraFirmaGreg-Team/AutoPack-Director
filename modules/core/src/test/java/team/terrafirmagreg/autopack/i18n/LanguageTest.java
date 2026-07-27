package team.terrafirmagreg.autopack.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageTest {

    @TempDir
    Path gameDir;

    @Test
    void detectsLanguageFromLaunchArgument() throws Exception {
        assertEquals("ru_ru", Language.detect(gameDir, Arrays.asList("--lang", "ru_ru")));
        assertEquals("pt_br", Language.detect(gameDir, Collections.singletonList("--lang=pt-BR")));
    }

    @Test
    void detectsLanguageFromOptionsTxt() throws Exception {
        Files.write(gameDir.resolve("options.txt"), Collections.singletonList("lang:de_de"), StandardCharsets.UTF_8);

        assertEquals("de_de", Language.detect(gameDir, Collections.emptyList()));
    }

    @Test
    void launchArgumentOverridesOptionsTxt() throws Exception {
        Files.write(gameDir.resolve("options.txt"), Collections.singletonList("lang:de_de"), StandardCharsets.UTF_8);

        assertEquals("fr_fr", Language.detect(gameDir, Arrays.asList("--lang", "fr_fr")));
    }

    @Test
    void normalizesLanguageCodes() {
        assertEquals("en_us", Language.normalize("EN-US"));
        assertEquals("en_us", Language.normalize(""));
    }
}
