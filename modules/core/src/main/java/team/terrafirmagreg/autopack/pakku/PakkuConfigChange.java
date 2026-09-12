package team.terrafirmagreg.autopack.pakku;

import java.nio.file.Path;
import lombok.Builder;
import lombok.Getter;
import team.terrafirmagreg.autopack.locale.Messages;

@Getter
@Builder
public class PakkuConfigChange {
    private final Path targetPath;
    private final String platform;
    private final Object addonId;
    private final String fileId;
    private final String fileName;
    private final String comment;
    private final boolean create;

    public String summary() {
        String displayName = comment != null && !comment.isEmpty()
                ? comment
                : targetPath.getFileName().toString();
        String platformName = PakkuPlatforms.format(platform);
        String key = create ? "autopack.pakku.entry.config_create" : "autopack.pakku.entry.config_update";
        return Messages.get(key, displayName, platformName);
    }
}
