package team.terrafirmagreg.autopack.cli;

import java.nio.file.Paths;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.configuration.ConfigValidationRunner;
import team.terrafirmagreg.autopack.ui.DirectorUis;

public class Cli {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--validate".equals(args[0])) {
            System.exit(ConfigValidationRunner.validate(Paths.get(".", "config", "mod-director")));
            return;
        }

        CliPlatform platform = new CliPlatform();
        Director director = new Director(platform, DirectorUis.forPlatform(platform));

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
