package team.terrafirmagreg.autopack.testsupport;

import java.nio.file.Path;
import team.terrafirmagreg.autopack.Director;

public final class DirectorTestSupport {
    private DirectorTestSupport() {}

    public static Director create(Path root) {
        return new Director(new TestPlatform(root));
    }
}
