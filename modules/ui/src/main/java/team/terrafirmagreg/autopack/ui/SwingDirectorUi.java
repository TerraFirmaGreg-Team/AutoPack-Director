package team.terrafirmagreg.autopack.ui;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.swing.JOptionPane;
import team.terrafirmagreg.autopack.UnsafeExit;
import team.terrafirmagreg.autopack.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.locale.Messages;
import team.terrafirmagreg.autopack.locale.UserErrors;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;
import team.terrafirmagreg.autopack.manage.InstallError;
import team.terrafirmagreg.autopack.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.manage.select.InstallSelector;
import team.terrafirmagreg.autopack.ui.theme.UITheme;
import team.terrafirmagreg.autopack.util.ImageLoader;

public final class SwingDirectorUi implements DirectorUi {
    private final LoggerDelegate logger;
    private MainWindow window;

    public SwingDirectorUi(LoggerDelegate logger) {
        this.logger = logger;
    }

    @Override
    public void open(ModpackConfiguration bootstrap, LoggerDelegate logger, Path configurationDirectory) {
        UITheme.apply(bootstrap.uiTheme(), configurationDirectory, logger);

        window = new MainWindow(logger);
        window.getModpackName().setText(bootstrap.packName());
        var icon = bootstrap.icon();
        Image iconImage = null;
        if (icon != null) {
            try {
                iconImage = ImageLoader.getImage(icon.path(), icon.width(), icon.height());
            } catch (Throwable e) {
                logger.error("Unable to load modpack icon {0}", icon.path(), e);
            }
        }
        window.setModpackIcon(iconImage, icon == null ? null : new Dimension(icon.width(), icon.height()));
        window.setLocationRelativeTo(null);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logger.info("User asked to exit");
                UnsafeExit.exit(0);
            }
        });
        window.setTitle(bootstrap.packName());
        window.pack();
        window.setVisible(true);
    }

    @Override
    public ProgressSession progress(String titleKey, Object... params) {
        ensureWindow();
        var page = window.progressPage(titleKey, params);
        return page::createProgressCallback;
    }

    @Override
    public void selectOptionalMods(InstallSelector selector) throws InterruptedException {
        ensureWindow();
        window.selectionPage(selector).waitForNext();
    }

    @Override
    public void consentInstall(List<InstallableMod> mods) throws InterruptedException {
        ensureWindow();
        window.consent(mods).waitForNext();
    }

    @Override
    public void message(String titleKey, String messageKey, String buttonKey) throws InterruptedException {
        ensureWindow();
        window.messagePage(titleKey, messageKey, buttonKey).waitForButton();
    }

    @Override
    public boolean pakkuPrompt(String titleKey, String messageKey, List<String> entries) throws InterruptedException {
        ensureWindow();
        return window.pakkuPrompt(titleKey, messageKey, entries).waitForAnswer();
    }

    @Override
    public void showErrors(Collection<InstallError> errors) throws InterruptedException {
        if (window != null) {
            window.errorPage(errors).waitForClose();
            return;
        }
        StringBuilder msg = new StringBuilder("<html><b>")
                .append(Messages.get("autopack.error.page.title"))
                .append("</b><br><br>");
        errors.forEach(e -> {
            msg.append("&bull; ").append(e.getMessage()).append("<br>");
            String detail = UserErrors.formatDetail(e);
            if (detail != null) {
                msg.append("&nbsp;&nbsp;")
                        .append(Messages.get("autopack.error.page.detail", detail))
                        .append("<br>");
            }
        });
        msg.append("</html>");
        JOptionPane.showMessageDialog(
                null, msg.toString(), Messages.get("autopack.error.page.window_title"), JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void warn(String body) {
        WarningDisplay.show(body);
    }

    @Override
    public void close() {
        if (window != null) {
            window.dispose();
            window = null;
        }
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    private void ensureWindow() {
        if (window == null) {
            throw new IllegalStateException("Swing UI was not opened");
        }
    }
}
