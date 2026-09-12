package team.terrafirmagreg.autopack.manage.install;

import java.nio.file.Path;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.configuration.RemoteMod;
import team.terrafirmagreg.autopack.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.exception.InstallException;
import team.terrafirmagreg.autopack.manage.ProgressCallback;

public record InstallableMod(RemoteMod remoteMod, RemoteModInformation remoteInformation, Path targetFile) {

    public void performInstall(Director director, ProgressCallback callback) throws InstallException {
        remoteMod.performInstall(targetFile, callback, director, remoteInformation);
    }
}
