package net.jan.moddirector.standalone;

import net.jan.moddirector.ModpackDirector;

public class ModDirectorStandalone {
    public static void main(String[] args) throws Exception {
        ModDirectorStandalonePlatform platform = new ModDirectorStandalonePlatform();
        ModpackDirector director = new ModpackDirector(platform);

        if (!director.call()) {
            director.errorExit();
        }

        System.out.println("============================================================");
        System.out.println("Installed mods summary:");
        System.out.println("============================================================");
        director.getInstalledMods().forEach((mod) -> {
            System.out.println(mod.getFile() + (mod.shouldInject() ? " has been injected" : " has not been injected"));
            mod.getOptions().forEach((key, value) -> System.out.println("- " + key + ": " + value));
        });
        System.out.println("============================================================");
    }
}
