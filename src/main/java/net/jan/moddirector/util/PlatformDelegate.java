package net.jan.moddirector.util;

import net.jan.moddirector.logging.LoggerDelegate;

import java.nio.file.Path;

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
}
