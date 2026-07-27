package team.terrafirmagreg.autopack.core.pakku;

import lombok.Builder;
import lombok.Getter;

import java.net.URL;

@Getter
@Builder
public class PakkuMissingMod {
    private final String fileName;
    private final URL downloadUrl;
    private final String platform;

    public String summary() {
        return fileName + " (" + platform + ")";
    }
}
