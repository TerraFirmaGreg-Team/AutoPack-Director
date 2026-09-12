package team.terrafirmagreg.autopack.pakku;

import java.net.URL;
import lombok.Builder;
import lombok.Getter;
import team.terrafirmagreg.autopack.locale.Messages;

@Getter
@Builder
public class PakkuMissingMod {
    private final String fileName;
    private final URL downloadUrl;
    private final String platform;

    public String summary() {
        return Messages.get("autopack.pakku.entry.missing_mod", fileName, PakkuPlatforms.format(platform));
    }
}
