package team.terrafirmagreg.autopack.locale;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import team.terrafirmagreg.autopack.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;
import team.terrafirmagreg.autopack.util.PlatformDelegate;
import tools.jackson.core.type.TypeReference;

public class Messages {
    private static final String LANG_PATH = "/assets/autopack/lang/%s.json";
    private static final String DEFAULT_LANG = "en_us";
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    @Getter
    private static Messages instance;

    private final Map<String, String> messages;
    private final LoggerDelegate logger;

    public Messages(PlatformDelegate platform) {
        this(platform.languageCode(), platform.logger());
    }

    public Messages(String languageCode) {
        this(languageCode, null);
    }

    Messages(String languageCode, LoggerDelegate logger) {
        this.logger = logger;
        this.messages = load(languageCode);
    }

    public static void init(PlatformDelegate platform) {
        instance = new Messages(platform);
    }

    public static void init(String languageCode) {
        instance = new Messages(languageCode);
    }

    public static String get(String key, Object... params) {
        return getInstance().getMessage(key, params);
    }

    String getMessage(String key, Object... params) {
        String template = messages.get(key);
        if (template == null) {
            return fallbackKey(key, params);
        }
        try {
            return String.format(Locale.ROOT, template, params);
        } catch (IllegalFormatException e) {
            if (logger != null) {
                logger.warn("Unable to format key {0} due to bad expression", key, e);
            }
            return fallbackKey(key, params);
        }
    }

    private Map<String, String> load(String languageCode) {
        Map<String, String> loaded = new HashMap<>(loadFile(DEFAULT_LANG));
        String normalized = Language.normalize(languageCode);
        if (!DEFAULT_LANG.equals(normalized)) {
            loaded.putAll(loadFile(normalized));
        }
        return loaded;
    }

    private Map<String, String> loadFile(String languageCode) {
        String path = String.format(LANG_PATH, languageCode);
        try (InputStream inputStream = Messages.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                return Collections.emptyMap();
            }
            Map<String, String> parsed = ConfigurationController.OBJECT_MAPPER.readValue(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8), MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("Unable to load language file {0}", path, e);
            }
            return Collections.emptyMap();
        }
    }

    private String fallbackKey(String key, Object... params) {
        if (params.length > 0) {
            return key + ':' + Arrays.toString(params);
        }
        return key;
    }
}
