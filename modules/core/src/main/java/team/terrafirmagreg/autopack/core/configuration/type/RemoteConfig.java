package team.terrafirmagreg.autopack.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.net.URL;

@Jacksonized
@Builder
@Getter
public class RemoteConfig {
    @JsonProperty(required = true)
    private final URL url;
}
