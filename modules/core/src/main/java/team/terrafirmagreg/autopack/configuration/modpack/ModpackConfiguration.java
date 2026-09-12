package team.terrafirmagreg.autopack.configuration.modpack;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Accessors(fluent = true)
@Getter
public class ModpackConfiguration {
    @JsonProperty(required = true)
    private final String packName;

    @JsonProperty
    private final ModpackIconConfiguration icon;

    @JsonProperty
    private final String localVersion;

    @JsonProperty
    private final URL remoteVersion;

    @JsonProperty
    private final boolean refuseLaunch;

    @JsonProperty
    private final boolean requiresRestart;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final Boolean checkStopModReposts;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final String uiTheme;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final PakkuSettings pakku;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final Boolean promptInstallConsent;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final Boolean promptOptionalMods;

    public static ModpackConfiguration createDefault() {
        return ModpackConfiguration.builder()
                .packName("AutoPack Director")
                .uiTheme("material-dark")
                .build();
    }

    public boolean checkStopModReposts() {
        return checkStopModReposts == null || checkStopModReposts;
    }

    public String uiTheme() {
        return uiTheme != null ? uiTheme : "material-dark";
    }

    public PakkuSettings pakku() {
        return pakku != null ? pakku : PakkuSettings.defaults();
    }

    public boolean promptInstallConsent() {
        return promptInstallConsent == null || promptInstallConsent;
    }

    public boolean promptOptionalMods() {
        return promptOptionalMods == null || promptOptionalMods;
    }
}
