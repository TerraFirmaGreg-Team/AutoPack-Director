package team.terrafirmagreg.autopack.configuration.modpack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Accessors(fluent = true)
@Getter
public class PakkuSettings {
    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final Boolean promptConfigCreate;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final Boolean promptConfigUpdate;

    @Getter(AccessLevel.NONE)
    @JsonProperty
    private final Boolean promptMissingMods;

    public static PakkuSettings defaults() {
        return PakkuSettings.builder().build();
    }

    public boolean promptConfigCreate() {
        return promptConfigCreate == null || promptConfigCreate;
    }

    public boolean promptConfigUpdate() {
        return promptConfigUpdate == null || promptConfigUpdate;
    }

    public boolean promptMissingMods() {
        return promptMissingMods == null || promptMissingMods;
    }
}
