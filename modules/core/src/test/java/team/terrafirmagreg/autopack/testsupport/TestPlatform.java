package team.terrafirmagreg.autopack.testsupport;

import team.terrafirmagreg.autopack.logging.JavaLogger;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;
import team.terrafirmagreg.autopack.util.PlatformDelegate;
import team.terrafirmagreg.autopack.util.Side;

import java.nio.file.Path;
import java.util.logging.Logger;

public class TestPlatform implements PlatformDelegate {
    private final Path root;
    private final Side side;
    private final LoggerDelegate logger = new JavaLogger(Logger.getLogger("Director-Test"));

    public TestPlatform(Path root) {
        this(root, Side.UNKNOWN);
    }

    public TestPlatform(Path root, Side side) {
        this.root = root;
        this.side = side;
    }

    @Override
    public String name() {
        return "Test";
    }

    @Override
    public Path configurationDirectory() {
        return root.resolve("config").resolve("mod-director");
    }

    @Override
    public Path modFile(String modFileName) {
        return root.resolve("mods").resolve(modFileName);
    }

    @Override
    public Path rootFile(String modFileName) {
        return root.resolve(modFileName);
    }

    @Override
    public Path customFile(String modFileName, String modFolderName) {
        return root.resolve(modFolderName).resolve(modFileName);
    }

    @Override
    public Path installationRoot() {
        return root;
    }

    @Override
    public LoggerDelegate logger() {
        return logger;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public boolean headless() {
        return true;
    }
}
