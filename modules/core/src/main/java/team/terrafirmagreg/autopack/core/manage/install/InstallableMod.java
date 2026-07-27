package team.terrafirmagreg.autopack.core.manage.install;

import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;

import java.nio.file.Path;

public class InstallableMod {
    private final RemoteMod remoteMod;
    private final RemoteModInformation remoteInformation;
    private final Path targetFile;


    public InstallableMod(RemoteMod remoteMod, RemoteModInformation remoteInformation, Path targetFile) {
        this.remoteMod = remoteMod;
        this.remoteInformation = remoteInformation;
        this.targetFile = targetFile;
    }

    public RemoteMod getRemoteMod() {
        return remoteMod;
    }

    public RemoteModInformation getRemoteInformation() {
        return remoteInformation;
    }

    public Path getTargetFile() {
        return targetFile;
    }

    public void performInstall(Director director, ProgressCallback callback) throws InstallException {
        remoteMod.performInstall(targetFile, callback, director, remoteInformation);
    }
}
