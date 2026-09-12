package team.terrafirmagreg.autopack;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import lombok.Getter;
import team.terrafirmagreg.autopack.configuration.ConfigFileType;
import team.terrafirmagreg.autopack.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.configuration.RemoteMod;
import team.terrafirmagreg.autopack.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.configuration.modpack.PakkuSettings;
import team.terrafirmagreg.autopack.exception.InstallException;
import team.terrafirmagreg.autopack.locale.Messages;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;
import team.terrafirmagreg.autopack.manage.InstallController;
import team.terrafirmagreg.autopack.manage.InstallError;
import team.terrafirmagreg.autopack.manage.ProgressCallback;
import team.terrafirmagreg.autopack.manage.check.StopModReposts;
import team.terrafirmagreg.autopack.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.manage.install.InstalledMod;
import team.terrafirmagreg.autopack.manage.select.InstallSelector;
import team.terrafirmagreg.autopack.pakku.PakkuConfigChange;
import team.terrafirmagreg.autopack.pakku.PakkuDiff;
import team.terrafirmagreg.autopack.pakku.PakkuLockDiffer;
import team.terrafirmagreg.autopack.pakku.PakkuLockSync;
import team.terrafirmagreg.autopack.pakku.PakkuMissingMod;
import team.terrafirmagreg.autopack.ui.DirectorUi;
import team.terrafirmagreg.autopack.util.NetworkExceptions;
import team.terrafirmagreg.autopack.util.PlatformDelegate;
import team.terrafirmagreg.autopack.util.WebClient;
import team.terrafirmagreg.autopack.util.WebGetResponse;

@Getter
public class Director implements Callable<Boolean> {
    private static final TimeUnit DEFAULT_UNIT = TimeUnit.DAYS;
    private static final int DEFAULT_TIME = 1;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private final ScheduledExecutorService taskExecutor = Executors.newScheduledThreadPool(
            Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors())),
            r -> new Thread(r, "Director Worker " + THREAD_NUMBER.incrementAndGet()));
    private final ConcurrentLinkedDeque<InstallError> errors = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<InstalledMod> installedMods = new ConcurrentLinkedDeque<>();
    private final InstallSelector installSelector = new InstallSelector();
    private final PlatformDelegate platform;
    private final LoggerDelegate logger;
    private final DirectorUi ui;
    private final ConfigurationController configurationController;
    private final InstallController installController;
    private final StopModReposts stopModReposts;
    private String modpackRemoteVersion;

    public Director(PlatformDelegate platform) {
        this(platform, DirectorUi.headless());
    }

    public Director(PlatformDelegate platform, DirectorUi ui) {
        this.platform = platform;
        this.logger = platform.logger();
        this.ui = ui != null ? ui : DirectorUi.headless();
        Messages.init(platform);
        this.configurationController = new ConfigurationController(this, platform.configurationDirectory());
        this.installController = new InstallController(this);
        this.stopModReposts = new StopModReposts(this);
        initializeTrustStore();
    }

    private void initializeTrustStore() {
        try (InputStream is = Director.class.getResourceAsStream("/cacerts")) {
            if (is == null) {
                logger.warn("Unable to replace CA certificates: bundled trust store not found");
                return;
            }
            File cacertsCopy = File.createTempFile("cacerts", "");
            cacertsCopy.deleteOnExit();
            Files.copy(is, cacertsCopy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.setProperty("javax.net.ssl.trustStore", cacertsCopy.getAbsolutePath());
            logger.info("Successfully replaced CA certificates with updated ones");
        } catch (Exception e) {
            logger.warn("Unable to replace CA certificates", e);
        }
    }

    @Override
    public Boolean call() throws Exception {
        PakkuDiff pakkuDiff =
                PakkuLockDiffer.detect(platform.installationRoot(), platform.configurationDirectory(), logger);

        ModpackConfiguration modpackConfiguration = readBootstrapModpackConfiguration();
        ui.open(modpackConfiguration, logger, platform.configurationDirectory());

        handlePakkuDiff(pakkuDiff, modpackConfiguration);

        configurationController.load();
        List<RemoteMod> mods = configurationController.getConfigurations();
        if (configurationController.getModpackConfiguration() != null) {
            modpackConfiguration = configurationController.getModpackConfiguration();
        } else {
            logger.warn("This modpack does not contain a modpack.json, if you are the author, consider adding one!");
            modpackConfiguration = ModpackConfiguration.createDefault();
        }

        if (modpackConfiguration.checkStopModReposts()) {
            stopModReposts.load();
        } else {
            logger.info("StopModReposts checks disabled via modpack.json");
        }

        if (modpackConfiguration.remoteVersion() != null) {
            try (WebGetResponse response = WebClient.get(modpackConfiguration.remoteVersion());
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                modpackRemoteVersion = reader.readLine();
            } catch (IOException e) {
                String detail = NetworkExceptions.describe(e);
                logger.error(
                        "Failed to check modpack version from {0}: {1}",
                        modpackConfiguration.remoteVersion(), detail, e);
                addError(new InstallError(
                        Level.SEVERE,
                        Messages.get("autopack.error.version_check", modpackConfiguration.remoteVersion(), detail),
                        e));
            }
        }

        if (hasFatalError()) {
            return false;
        }

        DirectorUi.ProgressSession preInstallation =
                ui.isInteractive() ? ui.progress("autopack.progress.checking") : null;

        List<RemoteMod> excludedMods = new ArrayList<>();
        List<InstallableMod> reInstalls = new ArrayList<>();
        List<InstallableMod> freshInstalls = new ArrayList<>();
        List<Callable<Void>> preInstallTasks = installController.createPreInstallTasks(
                mods,
                excludedMods,
                freshInstalls,
                reInstalls,
                preInstallation != null ? preInstallation::callback : this::noOpCallback);

        awaitAll(taskExecutor.invokeAll(preInstallTasks));
        installSelector.accept(excludedMods, freshInstalls, reInstalls);

        if (hasFatalError()) {
            errorExit();
        }

        if (ui.isInteractive() && installSelector.hasSelectableOptions() && modpackConfiguration.promptOptionalMods()) {
            ui.selectOptionalMods(installSelector);
        }

        List<InstallableMod> toInstall = installSelector.computeModsToInstall();
        if (ui.isInteractive() && !toInstall.isEmpty() && modpackConfiguration.promptInstallConsent()) {
            ui.consentInstall(toInstall);
        }

        DirectorUi.ProgressSession installProgress = ui.isInteractive()
                ? ui.progress("autopack.progress.installing", modpackConfiguration.packName())
                : null;

        List<Callable<Void>> installTasks = installController.createInstallTasks(
                toInstall, installProgress != null ? installProgress::callback : this::noOpCallback);

        installTasks.add(() -> {
            installController.markDisabledMods(installSelector.computeDisabledMods());
            return null;
        });

        awaitAll(taskExecutor.invokeAll(installTasks));

        if (hasFatalError()) {
            errorExit();
        }

        taskExecutor.shutdown();
        if (!taskExecutor.awaitTermination(DEFAULT_TIME, DEFAULT_UNIT)) {
            logger.warn("Unable to terminate all tasks.");
        }

        if (modpackConfiguration.remoteVersion() != null
                && modpackConfiguration.localVersion() != null
                && modpackRemoteVersion != null
                && !modpackRemoteVersion.contains(modpackConfiguration.localVersion())) {
            logger.error("Modpack version mismatch!");
            if (ui.isInteractive()) {
                var baseKey = modpackConfiguration.refuseLaunch()
                        ? "autopack.dialog.outdated_blocked"
                        : "autopack.dialog.outdated";
                ui.message(baseKey + ".title", baseKey + ".message", baseKey + ".button");
            }

            if (modpackConfiguration.refuseLaunch()) {
                logger.error("Please update before continuing!");
                UnsafeExit.exit(1);
            }
        }

        if (modpackConfiguration.requiresRestart() && !freshInstalls.isEmpty()) {
            logger.info("Installation complete, a restart is required to complete initialization.");
            if (ui.isInteractive()) {
                ui.message(
                        "autopack.dialog.restart.title",
                        "autopack.dialog.restart.message",
                        "autopack.dialog.restart.button");
            }
            UnsafeExit.exit(0);
        }

        ui.close();
        return !hasFatalError();
    }

    public List<InstalledMod> getInstalledMods() {
        return new ArrayList<>(installedMods);
    }

    public void addError(InstallError error) {
        errors.add(error);
    }

    public boolean hasFatalError() {
        return errors.stream().anyMatch(e -> e.getLevel() == Level.SEVERE);
    }

    public void warn(String body) {
        ui.warn(body);
    }

    private ModpackConfiguration readBootstrapModpackConfiguration() {
        Path modpackConfigPath = platform.configurationDirectory().resolve(ConfigFileType.MODPACK.getSuffix());
        if (!Files.isRegularFile(modpackConfigPath)) {
            return ModpackConfiguration.createDefault();
        }
        try (InputStream stream = Files.newInputStream(modpackConfigPath)) {
            var tree = ConfigurationController.stripSchema(ConfigurationController.OBJECT_MAPPER.readTree(stream));
            return ConfigurationController.OBJECT_MAPPER.treeToValue(tree, ModpackConfiguration.class);
        } catch (IOException e) {
            logger.warn("Failed to read modpack.json for UI bootstrap: {0}", e.getMessage());
            return ModpackConfiguration.createDefault();
        }
    }

    private void handlePakkuDiff(PakkuDiff diff, ModpackConfiguration modpackConfiguration)
            throws InterruptedException {
        if (diff.isEmpty()) {
            return;
        }

        PakkuSettings pakku = modpackConfiguration.pakku();
        List<PakkuConfigChange> configUpdates = filterPakkuConfigUpdates(diff.getConfigUpdates(), pakku);

        if (configUpdates.isEmpty()) {
            if (diff.hasConfigDrift()) {
                logger.info("Pakku config create/update prompts disabled; skipping config sync UI");
            }
        } else if (!ui.isInteractive()) {
            logger.warn(
                    "pakku-lock config drift detected ({0} changes) but skipped in headless mode",
                    configUpdates.size());
        } else {
            List<String> entries = new ArrayList<>();
            for (PakkuConfigChange change : configUpdates) {
                entries.add(change.summary());
            }
            boolean sync = ui.pakkuPrompt("autopack.pakku.config.title", "autopack.pakku.config.message", entries);
            if (sync) {
                try {
                    PakkuLockSync.applyConfigs(
                            PakkuDiff.builder().configUpdates(configUpdates).build(), logger);
                } catch (IOException e) {
                    logger.error("Failed to sync configs from pakku-lock", e);
                    addError(new InstallError(
                            Level.SEVERE, Messages.get("autopack.error.pakku_sync", NetworkExceptions.describe(e)), e));
                }
            } else {
                logger.info("User declined pakku-lock config sync");
            }
        }

        if (!diff.hasMissingMods()) {
            return;
        }

        if (!pakku.promptMissingMods()) {
            logger.info("Pakku missing-mods prompt disabled via modpack.json");
            return;
        }

        if (!ui.isInteractive()) {
            logger.warn(
                    "pakku-lock missing mods detected ({0} files) but skipped in headless mode",
                    diff.getMissingMods().size());
            return;
        }

        List<String> entries = new ArrayList<>();
        for (PakkuMissingMod missing : diff.getMissingMods()) {
            entries.add(missing.summary());
        }
        boolean download = ui.pakkuPrompt("autopack.pakku.mods.title", "autopack.pakku.mods.message", entries);
        if (!download) {
            logger.info("User declined downloading missing mods from pakku-lock");
            return;
        }

        DirectorUi.ProgressSession progressPage = ui.progress("autopack.progress.pakku_download");
        try {
            PakkuLockSync.fetchMissingMods(
                    diff,
                    platform.installationRoot().resolve("mods"),
                    progressPage.callback("pakku", Messages.get("autopack.progress.pakku_file")),
                    logger);
        } catch (IOException e) {
            logger.error("Failed to download missing mods from pakku-lock", e);
            addError(new InstallError(
                    Level.SEVERE, Messages.get("autopack.error.pakku_download", NetworkExceptions.describe(e)), e));
        }
    }

    private static List<PakkuConfigChange> filterPakkuConfigUpdates(
            List<PakkuConfigChange> updates, PakkuSettings pakku) {
        List<PakkuConfigChange> filtered = new ArrayList<>();
        for (PakkuConfigChange change : updates) {
            if (change.isCreate()) {
                if (pakku.promptConfigCreate()) {
                    filtered.add(change);
                }
            } else if (pakku.promptConfigUpdate()) {
                filtered.add(change);
            }
        }
        return filtered;
    }

    private ProgressCallback noOpCallback(String title, String info) {
        return ProgressCallback.NO_OP;
    }

    public void errorExit() {
        logger.error("============================================================");
        logger.error("Summary of {0} encountered errors:", errors.size());
        errors.forEach(e -> {
            if (e.getException() != null) {
                logger.log(e.getLevel(), e.getMessage(), e.getException());
            } else {
                logger.log(e.getLevel(), e.getMessage());
            }
        });
        logger.error("============================================================");

        if (!platform.headless()) {
            try {
                ui.showErrors(errors);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
            }
        }

        UnsafeExit.exit(1);
    }

    public LoggerDelegate logger() {
        return logger;
    }

    public PlatformDelegate platform() {
        return platform;
    }

    private void awaitAll(List<Future<Void>> futures) throws InterruptedException {
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (CancellationException e) {
                logger.error("A future task was cancelled unexpectedly", e);
                addError(new InstallError(Level.SEVERE, Messages.get("autopack.error.async_cancelled"), e));
            } catch (ExecutionException e) {
                logger.error("An exception occurred while performing asynchronous work", e);
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String detail = cause.getMessage() != null
                        ? cause.getMessage()
                        : cause.getClass().getSimpleName();
                addError(new InstallError(Level.SEVERE, Messages.get("autopack.error.async_failed", detail), e));
            }
        }
    }

    public void checkUrl(URL url) throws InstallException {
        this.stopModReposts.check(url);
    }
}
