package team.terrafirmagreg.autopack.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Getter
@Accessors(fluent = true)
public class ModifyMod {
    @JsonProperty
    private final String fileName;

    @JsonProperty(required = true)
    private final String folder;

    @JsonProperty
    private final boolean disable;

    @JsonProperty
    private final boolean delete;

    @JsonProperty
    private final String newFolder;

    @JsonProperty
    private final String newFileName;
}
