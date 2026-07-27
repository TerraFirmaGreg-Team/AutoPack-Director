package team.terrafirmagreg.autopack.util;

import team.terrafirmagreg.autopack.i18n.Language;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;

import java.nio.file.Path;
import java.util.Collections;

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
        return Language.detect(installationRoot(), Collections.emptyList());
    }
}
