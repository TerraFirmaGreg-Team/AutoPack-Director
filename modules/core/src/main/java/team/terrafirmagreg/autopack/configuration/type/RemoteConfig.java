package team.terrafirmagreg.autopack.configuration.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URL;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Builder
@Getter
public class RemoteConfig {
    @JsonProperty(required = true)
    private final URL url;

    @JsonProperty
    private final String comment;
}
