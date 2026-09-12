package team.terrafirmagreg.autopack.ui;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import team.terrafirmagreg.autopack.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;
import team.terrafirmagreg.autopack.manage.InstallError;
import team.terrafirmagreg.autopack.manage.ProgressCallback;
import team.terrafirmagreg.autopack.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.manage.select.InstallSelector;

public interface DirectorUi {
    DirectorUi HEADLESS = new DirectorUi() {};

    static DirectorUi headless() {
        return HEADLESS;
    }

    default void open(ModpackConfiguration bootstrap, LoggerDelegate logger, Path configurationDirectory) {}

    default ProgressSession progress(String titleKey, Object... params) {
        return (title, info) -> ProgressCallback.NO_OP;
    }

    default void selectOptionalMods(InstallSelector selector) throws InterruptedException {}

    default void consentInstall(List<InstallableMod> mods) throws InterruptedException {}

    default void message(String titleKey, String messageKey, String buttonKey) throws InterruptedException {}

    default boolean pakkuPrompt(String titleKey, String messageKey, List<String> entries) throws InterruptedException {
        return false;
    }

    default void showErrors(Collection<InstallError> errors) throws InterruptedException {}

    default void warn(String body) {}

    default void close() {}

    default boolean isInteractive() {
        return false;
    }

    @FunctionalInterface
    interface ProgressSession {
        ProgressCallback callback(String title, String info);
    }
}
