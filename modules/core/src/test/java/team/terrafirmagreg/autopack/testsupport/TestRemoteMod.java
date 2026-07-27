package team.terrafirmagreg.autopack.testsupport;

import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.InstallationPolicy;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.configuration.RemoteModMetadata;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;

import java.nio.file.Path;

public class TestRemoteMod extends RemoteMod {
    private final String name;
    private final RemoteModInformation information;
    private final InstallException queryException;
    private final InstallException installException;
    private final Runnable installAction;

    private TestRemoteMod(
        RemoteModMetadata metadata,
        InstallationPolicy installationPolicy,
        String folder,
        String name,
        RemoteModInformation information,
        InstallException queryException,
        InstallException installException,
        Runnable installAction
    ) {
        super(metadata, installationPolicy, null, folder, null);
        this.name = name != null ? name : "test-mod";
        this.information = information != null
            ? information
            : new RemoteModInformation("Test Mod", "test-mod.jar");
        this.queryException = queryException;
        this.installException = installException;
        this.installAction = installAction;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String remoteType() {
        return "test";
    }

    @Override
    public String offlineName() {
        return name;
    }

    @Override
    public String remoteUrl() {
        return "test://mod";
    }

    @Override
    public RemoteModInformation queryInformation() throws InstallException {
        if (queryException != null) {
            throw queryException;
        }
        return information;
    }

    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, Director director,
                               RemoteModInformation information) throws InstallException {
        if (installException != null) {
            throw installException;
        }
        if (installAction != null) {
            installAction.run();
        }
        progressCallback.done();
    }

    public static final class Builder {
        private RemoteModMetadata metadata;
        private InstallationPolicy installationPolicy;
        private String folder;
        private String name;
        private RemoteModInformation information;
        private InstallException queryException;
        private InstallException installException;
        private Runnable installAction;

        public Builder metadata(RemoteModMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder installationPolicy(InstallationPolicy installationPolicy) {
            this.installationPolicy = installationPolicy;
            return this;
        }

        public Builder folder(String folder) {
            this.folder = folder;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder information(RemoteModInformation information) {
            this.information = information;
            return this;
        }

        public Builder queryException(InstallException queryException) {
            this.queryException = queryException;
            return this;
        }

        public Builder installException(InstallException installException) {
            this.installException = installException;
            return this;
        }

        public Builder installAction(Runnable installAction) {
            this.installAction = installAction;
            return this;
        }

        public TestRemoteMod build() {
            return new TestRemoteMod(
                metadata,
                installationPolicy,
                folder,
                name,
                information,
                queryException,
                installException,
                installAction
            );
        }
    }
}
