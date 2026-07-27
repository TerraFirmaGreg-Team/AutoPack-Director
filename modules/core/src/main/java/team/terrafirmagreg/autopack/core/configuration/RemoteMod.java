package team.terrafirmagreg.autopack.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

@SuperBuilder
@Getter
public abstract class RemoteMod {
    @JsonProperty
    private final RemoteModMetadata metadata;

    @JsonProperty
    private final InstallationPolicy installationPolicy;

    @JsonProperty
    private final Map<String, Object> options;

    @JsonProperty
    private final String folder;

    @JsonProperty
    private final Boolean inject;

    protected RemoteMod(RemoteModBuilder<?, ?> builder) {
        this.metadata = builder.metadata;
        this.installationPolicy = builder.installationPolicy == null
            ? InstallationPolicy.builder().build() : builder.installationPolicy;
        this.options = builder.options == null ? Collections.emptyMap() : builder.options;
        this.folder = builder.folder;
        this.inject = builder.inject;
    }

    protected RemoteMod(
        RemoteModMetadata metadata,
        InstallationPolicy installationPolicy,
        Map<String, Object> options,
        String folder,
        Boolean inject
    ) {
        this.metadata = metadata;
        this.installationPolicy = installationPolicy == null ? InstallationPolicy.builder().build() : installationPolicy;
        this.options = options == null ? Collections.emptyMap() : options;
        this.folder = folder;
        this.inject = inject;
    }

    public abstract String remoteType();

    public abstract String offlineName();

    public abstract String remoteUrl();

    public abstract RemoteModInformation queryInformation() throws InstallException;

    public abstract void performInstall(Path targetFile, ProgressCallback progressCallback, Director director,
                                        RemoteModInformation information) throws InstallException;

    public boolean forceInject() {
        return inject != null ? inject : folder == null;
    }
}
