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
    private final String uiTheme;

    @JsonCreator
    public ModpackConfiguration(
        @JsonProperty(value = "packName", required = true) String packName,
        @JsonProperty("icon") ModpackIconConfiguration icon,
        @JsonProperty("localVersion") String localVersion,
        @JsonProperty("remoteVersion") URL remoteVersion,
        @JsonProperty("refuseLaunch") boolean refuseLaunch,
        @JsonProperty("requiresRestart") boolean requiresRestart,
        @JsonProperty("uiTheme") String uiTheme
    ) {
        this.packName = packName;
        this.icon = icon;
        this.localVersion = localVersion;
        this.remoteVersion = remoteVersion;
        this.refuseLaunch = refuseLaunch;
        this.requiresRestart = requiresRestart;
        if (uiTheme == null) {
            this.uiTheme = "material-dark";
        } else {
            this.uiTheme = uiTheme;
        }

    }

    public static ModpackConfiguration createDefault() {
        return new ModpackConfiguration(
            "Modpack Director",
            null,
            null,
            null,
            false,
            false,
            "material-dark"
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

    public String uiTheme() {
        return uiTheme;
    }
}
