package team.terrafirmagreg.autopack.testsupport;

import team.terrafirmagreg.autopack.Director;

import java.nio.file.Path;

public final class DirectorTestSupport {
    private DirectorTestSupport() {
    }

    public static Director create(Path root) {
        return new Director(new TestPlatform(root), false);
    }
}
