package com.juanmuscaria.modpackdirector.util;

import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;

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
