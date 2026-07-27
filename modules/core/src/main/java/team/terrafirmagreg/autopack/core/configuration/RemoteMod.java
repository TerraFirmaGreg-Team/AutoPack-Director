package team.terrafirmagreg.autopack.core.configuration;

import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public abstract class RemoteMod {
    private final RemoteModMetadata metadata;
    private final InstallationPolicy installationPolicy;
    private final Map<String, Object> options;
    private final String folder;
    private final boolean inject;

    public RemoteMod(
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
        if (inject == null) {
            this.inject = folder == null;
        } else {
            this.inject = inject;
        }
    }

    public abstract String remoteType();

    public abstract String offlineName();

    public abstract String remoteUrl();

    public abstract RemoteModInformation queryInformation() throws InstallException;

    public abstract void performInstall(Path targetFile, ProgressCallback progressCallback, Director director,
                                        RemoteModInformation information) throws InstallException;

    public RemoteModMetadata getMetadata() {
        return metadata;
    }

    public InstallationPolicy getInstallationPolicy() {
        return installationPolicy;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public boolean forceInject() {
        return inject;
    }

    public String getFolder() {
        return folder;
    }
}
