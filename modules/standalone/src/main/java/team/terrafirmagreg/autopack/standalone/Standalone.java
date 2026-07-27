package team.terrafirmagreg.autopack.standalone;

import team.terrafirmagreg.autopack.Director;

public class Standalone {
    public static void main(String[] args) throws Exception {
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
