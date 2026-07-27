package team.terrafirmagreg.autopack.standalone;

import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.ConfigValidationRunner;

import java.nio.file.Paths;

public class Standalone {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--validate".equals(args[0])) {
            System.exit(ConfigValidationRunner.validate(Paths.get(".", "config", "mod-director")));
            return;
        }

        StandalonePlatform platform = new StandalonePlatform();
        Director director = new Director(platform);

        if (!director.call()) {
            director.errorExit();
        }

        System.out.println("============================================================");
        System.out.println("Installed mods summary:");
        System.out.println("============================================================");
        director.getInstalledMods().forEach((mod) -> {
            System.out.println(mod.file() + (mod.inject() ? " has been injected" : " has not been injected"));
            mod.options().forEach((key, value) -> System.out.println("- " + key + ": " + value));
        });
        System.out.println("============================================================");
    }
}
