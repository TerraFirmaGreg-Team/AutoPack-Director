package team.terrafirmagreg.autopack.core.manage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.InstallationPolicy;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.configuration.RemoteModMetadata;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.testsupport.DirectorTestSupport;
import team.terrafirmagreg.autopack.testsupport.TestRemoteMod;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void queryConnectivityErrorAddsNetworkDetail() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        TestRemoteMod mod = TestRemoteMod.builder()
            .queryException(new InstallException("query failed", new ConnectException("Connection refused")))
            .build();

        runPreInstall(director, mod);

        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertTrue(error.getMessage().contains("Failed to query information"));
        assertTrue(error.getMessage().contains("connect"));
        assertEquals(Level.SEVERE, error.getLevel());
    }

    @Test
    void queryNonConnectivityErrorOmitsNetworkDetail() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        TestRemoteMod mod = TestRemoteMod.builder()
            .queryException(new InstallException("query failed", new IOException("file not found")))
            .build();

        runPreInstall(director, mod);

        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertFalse(error.getMessage().contains("("));
    }

    @Test
    void continueOnFailedDownloadUsesWarningSeverity() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        InstallationPolicy policy = InstallationPolicy.builder()
            .continueOnFailedDownload(true)
            .build();
        TestRemoteMod mod = TestRemoteMod.builder()
            .installationPolicy(policy)
            .queryException(new InstallException("query failed", new ConnectException("refused")))
            .build();

        runPreInstall(director, mod);

        InstallError error = director.getErrors().peekLast();
        assertEquals(Level.WARNING, error.getLevel());
    }

    @Test
    void pathOutsideInstallationRootAddsSevereError() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        TestRemoteMod mod = TestRemoteMod.builder()
            .folder("../outside")
            .information(new RemoteModInformation("Escape", "escape.jar"))
            .build();

        runPreInstall(director, mod);

        InstallError error = director.getErrors().peekLast();
        assertNotNull(error);
        assertEquals(Level.SEVERE, error.getLevel());
        assertTrue(error.getMessage().contains("outside"));
    }

    @Test
    void installConnectivityErrorUsesConfiguredSeverity() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        InstallationPolicy policy = InstallationPolicy.builder()
            .continueOnFailedDownload(true)
            .build();
        Path target = tempDir.resolve("mods").resolve("test-mod.jar");
        Files.createDirectories(target.getParent());
        TestRemoteMod mod = TestRemoteMod.builder()
            .installationPolicy(policy)
            .installException(new InstallException("install failed", new ConnectException("refused")))
            .build();
        InstallableMod installable = new InstallableMod(
            mod,
            new RemoteModInformation("Test Mod", "test-mod.jar"),
            target
        );

        runInstall(director, installable);

        InstallError error = director.getErrors().peekLast();
        assertEquals(Level.WARNING, error.getLevel());
        assertTrue(error.getMessage().contains("connect"));
    }

    @Test
    void postInstallHashMismatchAddsSevereError() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("MD5", "00000000000000000000000000000000");
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .hashes(hashes)
            .build();
        Path target = tempDir.resolve("mods").resolve("test-mod.jar");
        Files.createDirectories(target.getParent());
        TestRemoteMod mod = TestRemoteMod.builder()
            .metadata(metadata)
            .installAction(() -> {
                try {
                    Files.write(target, "downloaded".getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .build();
        InstallableMod installable = new InstallableMod(
            mod,
            new RemoteModInformation("Test Mod", "test-mod.jar"),
            target
        );

        runInstall(director, installable);

        InstallError error = director.getErrors().peekLast();
        assertEquals(Level.SEVERE, error.getLevel());
        assertTrue(error.getMessage().contains("hash"));
    }

    @Test
    void existingOfflineFileSkipsRemoteQuery() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        Path target = tempDir.resolve("mods").resolve("already-there.jar");
        Files.createDirectories(target.getParent());
        Files.write(target, "ok".getBytes());

        TestRemoteMod mod = TestRemoteMod.builder()
            .offlineTargetFilename("already-there.jar")
            .queryException(new InstallException("should not query", new ConnectException("refused")))
            .build();

        List<RemoteMod> excludedMods = new ArrayList<>();
        List<InstallableMod> freshMods = new ArrayList<>();
        List<InstallableMod> reinstallMods = new ArrayList<>();
        List<Callable<Void>> tasks = director.getInstallController().createPreInstallTasks(
            Collections.singletonList(mod),
            excludedMods,
            freshMods,
            reinstallMods,
            (title, info) -> new NoOpProgressCallback()
        );
        tasks.get(0).call();

        assertTrue(director.getErrors().isEmpty());
        assertEquals(1, excludedMods.size());
        assertTrue(freshMods.isEmpty());
        assertTrue(reinstallMods.isEmpty());
    }

    @Test
    void markDisabledModsIoFailureAddsWarning() throws Exception {
        Director director = DirectorTestSupport.create(tempDir);
        Path parentFile = tempDir.resolve("not-a-dir");
        Files.write(parentFile, "blocked".getBytes());
        Path target = parentFile.resolve("test-mod.jar");
        TestRemoteMod mod = TestRemoteMod.builder().build();
        InstallableMod installable = new InstallableMod(
            mod,
            new RemoteModInformation("Test Mod", "test-mod.jar"),
            target
        );

        director.getInstallController().markDisabledMods(Collections.singletonList(installable));

        InstallError error = director.getErrors().peekLast();
        assertEquals(Level.WARNING, error.getLevel());
        assertTrue(error.getMessage().contains("disabled file"));
    }

    private void runPreInstall(Director director, RemoteMod mod) throws Exception {
        List<RemoteMod> excludedMods = new ArrayList<>();
        List<InstallableMod> freshMods = new ArrayList<>();
        List<InstallableMod> reinstallMods = new ArrayList<>();
        List<Callable<Void>> tasks = director.getInstallController().createPreInstallTasks(
            Collections.singletonList(mod),
            excludedMods,
            freshMods,
            reinstallMods,
            (title, info) -> new NoOpProgressCallback()
        );
        tasks.get(0).call();
    }

    private void runInstall(Director director, InstallableMod installable) throws Exception {
        List<Callable<Void>> tasks = director.getInstallController().createInstallTasks(
            Collections.singletonList(installable),
            (title, info) -> new NoOpProgressCallback()
        );
        tasks.get(0).call();
    }
}
