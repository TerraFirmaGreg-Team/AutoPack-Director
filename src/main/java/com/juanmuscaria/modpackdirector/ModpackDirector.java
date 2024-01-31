package com.juanmuscaria.modpackdirector;

import com.juanmuscaria.modpackdirector.i18n.Messages;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.ui.DirectorMainGUI;
import com.juanmuscaria.modpackdirector.ui.theme.UITheme;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import lombok.Getter;
import net.jan.moddirector.core.configuration.ConfigurationController;
import net.jan.moddirector.core.configuration.ModDirectorRemoteMod;
import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.InstallController;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.manage.NoOpProgressCallback;
import net.jan.moddirector.core.manage.ProgressCallback;
import net.jan.moddirector.core.manage.check.StopModReposts;
import net.jan.moddirector.core.manage.install.InstallableMod;
import net.jan.moddirector.core.manage.install.InstalledMod;
import net.jan.moddirector.core.manage.select.InstallSelector;
import net.jan.moddirector.core.util.ImageLoader;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@Getter
public class ModpackDirector implements Callable<Boolean> {
    private static final TimeUnit DEFAULT_UNIT = TimeUnit.DAYS;
    private static final int DEFAULT_TIME = 1;
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final NoOpProgressCallback NO_OP_PROGRESS_CALLBACK = new NoOpProgressCallback();
    private final ScheduledExecutorService taskExecutor = Executors.newScheduledThreadPool(Math.min(8, Math.max(4, Runtime.getRuntime().availableProcessors())),
        r -> new Thread(r, "ModpackDirector Worker " + THREAD_NUMBER.incrementAndGet()));
    private final ConcurrentLinkedDeque<ModDirectorError> errors = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<InstalledMod> installedMods = new ConcurrentLinkedDeque<>();
    private final InstallSelector installSelector = new InstallSelector();
    private final PlatformDelegate platform;
    private final LoggerDelegate logger;
    private final LookAndFeel prevLookAndFeel;
    private final ConfigurationController configurationController;
    private final InstallController installController;
    private final StopModReposts stopModReposts;
    private String modpackRemoteVersion;

    public ModpackDirector(PlatformDelegate platform) {
        this.platform = platform;
        this.logger = platform.logger();
        this.prevLookAndFeel = UIManager.getLookAndFeel();
        this.configurationController = new ConfigurationController(this, platform.configurationDirectory());
        this.installController = new InstallController(this);
        this.stopModReposts = new StopModReposts(this);
    }

    @Override
    public Boolean call() throws Exception {
        configurationController.load();
        List<ModDirectorRemoteMod> mods = configurationController.getConfigurations();
        ModpackConfiguration modpackConfiguration = configurationController.getModpackConfiguration();

        if (modpackConfiguration == null) {
            logger.warn("This modpack does not contain a modpack.json, if you are the author, consider adding one!");
            modpackConfiguration = ModpackConfiguration.createDefault();
        } else if (modpackConfiguration.remoteVersion() != null) {
            try (WebGetResponse response = WebClient.get(modpackConfiguration.remoteVersion());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                modpackRemoteVersion = reader.readLine();
            }
        }
        UITheme.apply(modpackConfiguration.uiTheme(), logger);

        if (hasFatalError()) {
            return false;
        }

        var messages = new Messages(platform, true);
        DirectorMainGUI ui = null;
        if (!platform.headless()) {
            ui = new DirectorMainGUI(messages, logger);
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

        var preInstallationPage = ui == null ? null
            : ui.progressPage("modpack_director.progress.check_install");

        List<ModDirectorRemoteMod> excludedMods = new ArrayList<>();
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
        var installProgressPage = ui == null ? null :
            ui.progressPage("modpack_director.progress.install", modpackConfiguration.packName());

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

        if (modpackConfiguration.remoteVersion() != null && modpackConfiguration.localVersion() != null && !modpackRemoteVersion.contains(modpackConfiguration.localVersion())) {
            logger.error("Modpack version mismatch!");
            if (ui != null) {
                var baseKey = modpackConfiguration.refuseLaunch() ? "modpack_director.modpack_outdated_refuse_launch" : "modpack_director.modpack_outdated";
                var page = ui.messagePage(baseKey + ".title", baseKey, baseKey + ".button");
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
                ui.messagePage("modpack_director.restart_required.title", "modpack_director.restart_required",
                    "modpack_director.restart_required.button").waitForButton();
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

    public void addError(ModDirectorError error) {
        errors.add(error);
    }

    public boolean hasFatalError() {
        return errors.stream().anyMatch(e -> e.getLevel() == Level.SEVERE);
    }

    private ProgressCallback noOpCallback(String title, String info) {
        return NO_OP_PROGRESS_CALLBACK;
    }

    //TODO display a GUI with all errors
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
                addError(new ModDirectorError(
                    Level.SEVERE,
                    "A future task was cancelled unexpectedly",
                    e
                ));
            } catch (ExecutionException e) {
                logger.error("An exception occurred while performing asynchronous work", e);
                addError(new ModDirectorError(
                    Level.SEVERE,
                    "An exception occurred while performing asynchronous work",
                    e
                ));
            }
        }
    }

    public void checkUrl(URL url) throws ModDirectorException {
        this.stopModReposts.check(url);
    }
}
