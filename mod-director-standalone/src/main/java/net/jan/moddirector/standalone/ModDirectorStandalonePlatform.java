package net.jan.moddirector.standalone;

import net.jan.moddirector.logging.JavaLogger;
import net.jan.moddirector.logging.LoggerDelegate;
import net.jan.moddirector.util.PlatformDelegate;
import net.jan.moddirector.util.Side;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ModDirectorStandalonePlatform implements PlatformDelegate {
    private final LoggerDelegate logger = new JavaLogger(Logger.getLogger("ModDirector"));

    @Override
    public String name() {
        return "Standalone";
    }

    @Override
    public Path configurationDirectory() {
        return Paths.get(".", "config", "mod-director");
    }

    @Override
    public Path modFile(String modFileName) {
        return Paths.get(".", "mods").resolve(modFileName);
    }

    @Override
    public Path rootFile(String modFileName) {
        return Paths.get(".").resolve(modFileName);
    }

    @Override
    public Path customFile(String modFileName, String modFolderName) {
        return Paths.get(".", modFolderName).resolve(modFileName);
    }

    @Override
    public Path installationRoot() {
        return Paths.get(".");
    }

    @Override
    public LoggerDelegate logger() {
        return logger;
    }

    @Override
    public Side side() {
        return Side.UNKNOWN;
    }

    @Override
    public boolean headless() {
        return false;
    }
}
