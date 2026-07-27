package team.terrafirmagreg.autopack.i18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class Language {
    private static final String LANG_PREFIX = "lang:";
    private static final String DEFAULT = "en_us";

    private Language() {
    }

    public static String detect(Path gameDir, List<String> launchArgs) {
        String fromArg = fromLaunchArgs(launchArgs);
        if (fromArg != null) {
            return normalize(fromArg);
        }

        String fromOptions = fromOptionsTxt(gameDir.resolve("options.txt"));
        if (fromOptions != null) {
            return normalize(fromOptions);
        }

        return fromJvmLocale();
    }

    private static String fromLaunchArgs(List<String> launchArgs) {
        if (launchArgs == null) {
            return null;
        }
        for (int i = 0; i < launchArgs.size(); i++) {
            String arg = launchArgs.get(i);
            if ("--lang".equals(arg) && i + 1 < launchArgs.size()) {
                return launchArgs.get(i + 1);
            }
            if (arg.startsWith("--lang=")) {
                return arg.substring("--lang=".length());
            }
        }
        return null;
    }

    private static String fromOptionsTxt(Path optionsFile) {
        if (!Files.isRegularFile(optionsFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(optionsFile, StandardCharsets.UTF_8)) {
                if (line.startsWith(LANG_PREFIX)) {
                    return line.substring(LANG_PREFIX.length()).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String fromJvmLocale() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        if (language.isEmpty()) {
            return DEFAULT;
        }
        String country = locale.getCountry().toLowerCase(Locale.ROOT);
        if (country.isEmpty()) {
            return language.equals("en") ? DEFAULT : language + "_" + language;
        }
        return language + "_" + country;
    }

    static String normalize(String code) {
        if (code == null || code.isEmpty()) {
            return DEFAULT;
        }
        return code.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
