package team.terrafirmagreg.autopack.ui;

import team.terrafirmagreg.autopack.util.PlatformDelegate;

public final class DirectorUis {
    private DirectorUis() {}

    public static DirectorUi forPlatform(PlatformDelegate platform) {
        if (platform.headless()) {
            return DirectorUi.headless();
        }
        return new SwingDirectorUi(platform.logger());
    }
}
