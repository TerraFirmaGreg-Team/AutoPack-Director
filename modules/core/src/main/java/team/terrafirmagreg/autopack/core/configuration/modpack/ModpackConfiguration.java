package team.terrafirmagreg.autopack.core.configuration.modpack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.net.URL;

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
    private final String uiTheme;

    public static ModpackConfiguration createDefault() {
        return ModpackConfiguration.builder()
                .packName("Modpack Director")
                .uiTheme("material-dark")
                .build();
    }

    public String uiTheme() {
        return uiTheme != null ? uiTheme : "material-dark";
    }
}
