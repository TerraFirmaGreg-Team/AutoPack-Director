package team.terrafirmagreg.autopack.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.Collections;
import java.util.List;

@Jacksonized
@Builder
@Getter
@Accessors(fluent = true)
public class InstallationPolicy {
    @JsonProperty
    private final boolean continueOnFailedDownload;

    @JsonProperty
    private final String optionalKey;

    @Getter(AccessLevel.NONE)
    @JsonProperty("selectedByDefault")
    private final Boolean selectedByDefault;

    @JsonProperty
    private final String name;

    @JsonProperty
    private final String description;

    @JsonProperty
    private final boolean extract;

    @JsonProperty
    private final boolean deleteAfterExtract;

    @JsonProperty
    private final boolean downloadAlways;

    @JsonProperty
    private final String supersede;

    @JsonProperty
    private final List<String> supersedes;

    @JsonProperty
    private final boolean deleteSuperseded;

    @JsonProperty
    private final String modpackVersion;

    public boolean isSelectedByDefault() {
        return selectedByDefault != null ? selectedByDefault : optionalKey != null;
    }

    public List<String> allSupersedePatterns() {
        if (supersedes != null && !supersedes.isEmpty()) {
            return supersedes;
        }
        if (supersede != null) {
            return Collections.singletonList(supersede);
        }
        return Collections.emptyList();
    }
}
