package team.terrafirmagreg.autopack.core.manage.install;

import com.github.bsideup.jabel.Desugar;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;

import java.nio.file.Path;

@Desugar
public record InstallableMod(RemoteMod remoteMod, RemoteModInformation remoteInformation, Path targetFile) {

    public void performInstall(Director director, ProgressCallback callback) throws InstallException {
        remoteMod.performInstall(targetFile, callback, director, remoteInformation);
    }
}
