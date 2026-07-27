package team.terrafirmagreg.autopack.core.configuration.modpack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Getter
@Accessors(fluent = true)
public class ModpackIconConfiguration {
    @JsonProperty(required = true)
    private final String path;

    @JsonProperty
    private final int width;

    @JsonProperty
    private final int height;
}
