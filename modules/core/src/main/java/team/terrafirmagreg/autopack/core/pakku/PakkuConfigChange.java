package team.terrafirmagreg.autopack.core.pakku;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;

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
        String action = create ? "create" : "update";
        return action + " " + targetPath.getFileName() + " (" + comment + ", fileId=" + fileId + ")";
    }
}
