package team.terrafirmagreg.autopack;

import team.terrafirmagreg.autopack.i18n.Messages;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;
import team.terrafirmagreg.autopack.ui.MainWindow;
import team.terrafirmagreg.autopack.ui.theme.UITheme;
import team.terrafirmagreg.autopack.util.PlatformDelegate;
import lombok.Getter;
import team.terrafirmagreg.autopack.core.configuration.ConfigFileType;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.InstallController;
import team.terrafirmagreg.autopack.core.manage.InstallError;
import team.terrafirmagreg.autopack.core.manage.NoOpProgressCallback;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;
import team.terrafirmagreg.autopack.core.manage.check.StopModReposts;
import team.terrafirmagreg.autopack.core.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.core.manage.install.InstalledMod;
import team.terrafirmagreg.autopack.core.manage.select.InstallSelector;
import team.terrafirmagreg.autopack.core.pakku.PakkuConfigChange;
import team.terrafirmagreg.autopack.core.pakku.PakkuDiff;
import team.terrafirmagreg.autopack.core.pakku.PakkuLockDiffer;
import team.terrafirmagreg.autopack.core.pakku.PakkuLockSync;
import team.terrafirmagreg.autopack.core.pakku.PakkuMissingMod;
import team.terrafirmagreg.autopack.core.util.ImageLoader;
import team.terrafirmagreg.autopack.core.util.NetworkExceptions;
import team.terrafirmagreg.autopack.core.util.WebClient;
import team.terrafirmagreg.autopack.core.util.WebGetResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@Getter
public class Director implements Callable<Boolean> {
    private static final TimeUnit DEFAULT_UNIT = TimeUnit.DAYS;
    private static final int DEFAULT_TIME = 1;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final NoOpProgressCallback NO_OP_PROGRESS_CALLBACK = new NoOpProgressCallback();
    private final ScheduledExecutorService taskExecutor = Executors.newScheduledThreadPool(Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors())),
        r -> new Thread(r, "Director Worker " + THREAD_NUMBER.incrementAndGet()));
    private final ConcurrentLinkedDeque<InstallError> errors = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<InstalledMod> installedMods = new ConcurrentLinkedDeque<>();
    private final InstallSelector installSelector = new InstallSelector();
    private final PlatformDelegate platform;
    private final LoggerDelegate logger;
    private final LookAndFeel prevLookAndFeel;
    private final ConfigurationController configurationController;
    private final InstallController installController;
    private final StopModReposts stopModReposts;
    private String modpackRemoteVersion;
    private MainWindow ui;

    public Director(PlatformDelegate platform) {
        this.platform = platform;
        this.logger = platform.logger();
        this.prevLookAndFeel = UIManager.getLookAndFeel();
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
        PakkuDiff pakkuDiff = PakkuLockDiffer.detect(
            platform.installationRoot(),
            platform.configurationDirectory(),
            logger
        );

        ModpackConfiguration modpackConfiguration = readBootstrapModpackConfiguration();
        UITheme.apply(modpackConfiguration.uiTheme(), logger);

        var messages = new Messages(platform);
        if (!platform.headless()) {
            ui = new MainWindow(messages, logger);
            ui.getModpackName().setText(modpackConfiguration.packName());
            var icon = modpackConfiguration.icon();
            Image iconImage = null;
            if (icon != null) {
                try {
                    iconImage = ImageLoader.getImage(icon.path(), icon.width(), icon.height());
                } catch (Throwable e) {
                    logger.error("Unable to load modpack icon {0}", icon.path(), e);
                }
            }
            ui.setModpackIcon(iconImage, icon == null ? null : new Dimension(icon.width(), icon.height()));
            ui.setLocationRelativeTo(null);
            ui.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    logger.info("User asked to exit");
                    UnsafeExit.exit(0);
                }
            });
            ui.setTitle(modpackConfiguration.packName());
            ui.pack();
            ui.setVisible(true);
        }

        handlePakkuDiff(pakkuDiff);

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
                 BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                modpackRemoteVersion = reader.readLine();
            } catch (IOException e) {
                String detail = NetworkExceptions.describe(e);
                logger.error("Failed to check modpack version from {0}: {1}",
                    modpackConfiguration.remoteVersion(), detail, e);
                addError(new InstallError(Level.SEVERE,
                    "Failed to check the modpack version from " + modpackConfiguration.remoteVersion()
                        + ": " + detail, e));
            }
        }

        if (hasFatalError()) {
            return false;
        }

        var preInstallationPage = ui == null ? null
            : ui.progressPage("autopack.progress.checking");

        List<RemoteMod> excludedMods = new ArrayList<>();
        List<InstallableMod> reInstalls = new ArrayList<>();
        List<InstallableMod> freshInstalls = new ArrayList<>();
        List<Callable<Void>> preInstallTasks = installController.createPreInstallTasks(
            mods,
            excludedMods,
            freshInstalls,
            reInstalls,
            preInstallationPage != null ?
                preInstallationPage::createProgressCallback :
                this::noOpCallback
        );

        awaitAll(taskExecutor.invokeAll(preInstallTasks));
        installSelector.accept(excludedMods, freshInstalls, reInstalls);

        if (hasFatalError()) {
            errorExit();
        }

        if (ui != null && installSelector.hasSelectableOptions()) {
            var selection = ui.selectionPage(installSelector);
            selection.waitForNext();
        }

        List<InstallableMod> toInstall = installSelector.computeModsToInstall();
        if (ui != null && !toInstall.isEmpty()) {
            var consent = ui.consent(toInstall);
            consent.waitForNext();
        }

        var installProgressPage = ui == null ? null :
            ui.progressPage("autopack.progress.installing", modpackConfiguration.packName());

        List<Callable<Void>> installTasks = installController.createInstallTasks(
            toInstall,
            installProgressPage != null ?
                installProgressPage::createProgressCallback :
                this::noOpCallback
        );

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

        if (modpackConfiguration.remoteVersion() != null && modpackConfiguration.localVersion() != null && modpackRemoteVersion != null && !modpackRemoteVersion.contains(modpackConfiguration.localVersion())) {
            logger.error("Modpack version mismatch!");
            if (ui != null) {
                var baseKey = modpackConfiguration.refuseLaunch() ? "autopack.dialog.outdated_blocked" : "autopack.dialog.outdated";
                var page = ui.messagePage(baseKey + ".title", baseKey + ".message", baseKey + ".button");
                page.waitForButton();
            }

            if (modpackConfiguration.refuseLaunch()) {
                logger.error("Please update before continuing!");
                UnsafeExit.exit(1);
            }
        }

        if (modpackConfiguration.requiresRestart() && !freshInstalls.isEmpty()) {
            logger.info("Installation complete, a restart is required to complete initialization.");
            if (ui != null) {
                ui.messagePage("autopack.dialog.restart.title", "autopack.dialog.restart.message",
                    "autopack.dialog.restart.button").waitForButton();
            }
            UnsafeExit.exit(0);
        }

        if (ui != null) {
            ui.dispose();
        }
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

    private ModpackConfiguration readBootstrapModpackConfiguration() {
        Path modpackConfigPath = platform.configurationDirectory().resolve(ConfigFileType.MODPACK.getSuffix());
        if (!Files.isRegularFile(modpackConfigPath)) {
            return ModpackConfiguration.createDefault();
        }
        try (InputStream stream = Files.newInputStream(modpackConfigPath)) {
            var tree = ConfigurationController.stripSchema(
                ConfigurationController.OBJECT_MAPPER.readTree(stream));
            return ConfigurationController.OBJECT_MAPPER.treeToValue(tree, ModpackConfiguration.class);
        } catch (IOException e) {
            logger.warn("Failed to read modpack.json for UI bootstrap: {0}", e.getMessage());
            return ModpackConfiguration.createDefault();
        }
    }

    private void handlePakkuDiff(PakkuDiff diff) throws InterruptedException {
        if (diff == null || diff.isEmpty()) {
            return;
        }

        if (diff.hasConfigDrift()) {
            if (ui == null) {
                logger.warn("pakku-lock config drift detected ({0} changes) but skipped in headless mode",
                    diff.getConfigUpdates().size());
            } else {
                List<String> entries = new ArrayList<>();
                for (PakkuConfigChange change : diff.getConfigUpdates()) {
                    entries.add(change.summary());
                }
                boolean sync = ui.pakkuPrompt(
                    "autopack.pakku.config.title",
                    "autopack.pakku.config.message",
                    entries
                ).waitForAnswer();
                if (sync) {
                    try {
                        PakkuLockSync.applyConfigs(diff, logger);
                    } catch (IOException e) {
                        logger.error("Failed to sync configs from pakku-lock", e);
                        addError(new InstallError(Level.SEVERE, "Failed to sync configs from pakku-lock", e));
                    }
                } else {
                    logger.info("User declined pakku-lock config sync");
                }
            }
        }

        if (diff.hasMissingMods()) {
            if (ui == null) {
                logger.warn("pakku-lock missing mods detected ({0} files) but skipped in headless mode",
                    diff.getMissingMods().size());
            } else {
                List<String> entries = new ArrayList<>();
                for (PakkuMissingMod missing : diff.getMissingMods()) {
                    entries.add(missing.summary());
                }
                boolean download = ui.pakkuPrompt(
                    "autopack.pakku.mods.title",
                    "autopack.pakku.mods.message",
                    entries
                ).waitForAnswer();
                if (download) {
                    var progressPage = ui.progressPage("autopack.progress.pakku_download");
                    try {
                        PakkuLockSync.fetchMissingMods(
                            diff,
                            platform.installationRoot().resolve("mods"),
                            progressPage.createProgressCallback("pakku", "Downloading"),
                            logger
                        );
                    } catch (IOException e) {
                        logger.error("Failed to download missing mods from pakku-lock", e);
                        addError(new InstallError(Level.SEVERE, "Failed to download missing mods from pakku-lock", e));
                    }
                } else {
                    logger.info("User declined downloading missing mods from pakku-lock");
                }
            }
        }
    }

    private ProgressCallback noOpCallback(String title, String info) {
        return NO_OP_PROGRESS_CALLBACK;
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
                if (ui != null) {
                    ui.errorPage(errors).waitForClose();
                } else {
                    StringBuilder msg = new StringBuilder("<html><b>Installation Failed</b><br><br>");
                    errors.forEach(e -> msg.append("&bull; ").append(e.getMessage()).append("<br>"));
                    msg.append("</html>");
                    JOptionPane.showMessageDialog(null, msg.toString(),
                        "Modpack Director", JOptionPane.ERROR_MESSAGE);
                }
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
                addError(new InstallError(
                    Level.SEVERE,
                    "A future task was cancelled unexpectedly",
                    e
                ));
            } catch (ExecutionException e) {
                logger.error("An exception occurred while performing asynchronous work", e);
                addError(new InstallError(
                    Level.SEVERE,
                    "An exception occurred while performing asynchronous work",
                    e
                ));
            }
        }
    }

    public void checkUrl(URL url) throws InstallException {
        this.stopModReposts.check(url);
    }
}
