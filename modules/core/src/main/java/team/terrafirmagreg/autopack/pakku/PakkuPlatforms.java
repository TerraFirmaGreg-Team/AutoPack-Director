package team.terrafirmagreg.autopack.pakku;

public final class PakkuPlatforms {
    private PakkuPlatforms() {}

    public static String format(String platform) {
        if (platform == null) {
            return "Unknown";
        }
        switch (platform.toLowerCase()) {
            case "curseforge":
            case "curse":
                return "CurseForge";
            case "modrinth":
                return "Modrinth";
            default:
                return platform;
        }
    }
}
