package team.terrafirmagreg.autopack.core.manage.check;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Getter
@Accessors(fluent = true)
public class StopModRepostsEntry {
    @JsonProperty(required = true)
    private final String domain;

    @JsonProperty(required = true)
    private final String path;

    @JsonProperty(required = true)
    private final String reason;

    @JsonProperty(required = true)
    private final String notes;
}
