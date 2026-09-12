package team.terrafirmagreg.autopack.util;

import java.nio.file.Path;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;

public interface PlatformDelegate {
    String name();

    Path configurationDirectory();

    Path modFile(String modFileName);

    Path rootFile(String modFileName);

    Path customFile(String modFileName, String modFolderName);

    Path installationRoot();

    LoggerDelegate logger();

    Side side();

    boolean headless();

    default String languageCode() {
        return "en_us";
    }
}
