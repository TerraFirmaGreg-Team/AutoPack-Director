package net.jan.moddirector.core.configuration.modpack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URL;

public class ModpackConfiguration {
    private final String packName;
    private final ModpackIconConfiguration icon;
    private final String localVersion;
    private final URL remoteVersion;
    private final boolean refuseLaunch;
    private final boolean requiresRestart;

    @JsonCreator
    public ModpackConfiguration(
        @JsonProperty(value = "packName", required = true) String packName,
        @JsonProperty("icon") ModpackIconConfiguration icon,
        @JsonProperty("localVersion") String localVersion,
        @JsonProperty("remoteVersion") URL remoteVersion,
        @JsonProperty("refuseLaunch") boolean refuseLaunch,
        @JsonProperty("requiresRestart") boolean requiresRestart
    ) {
        this.packName = packName;
        this.icon = icon;
        this.localVersion = localVersion;
        this.remoteVersion = remoteVersion;
        this.refuseLaunch = refuseLaunch;
        this.requiresRestart = requiresRestart;
    }

    public static ModpackConfiguration createDefault() {
        return new ModpackConfiguration(
            "Modpack Director",
            null,
            null,
            null,
            false,
            false
        );
    }

    public String packName() {
        return packName;
    }

    public ModpackIconConfiguration icon() {
        return icon;
    }

    public String localVersion() {
        return localVersion;
    }

    public URL remoteVersion() {
        return remoteVersion;
    }

    public boolean refuseLaunch() {
        return refuseLaunch;
    }

    public boolean requiresRestart() {
        return requiresRestart;
    }
}
