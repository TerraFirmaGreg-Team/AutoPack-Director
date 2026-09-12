package team.terrafirmagreg.autopack.ui.theme;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.*;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;

public class UITheme {
    private static final String ICON_DARK = "/assets/autopack/icon/icon.png";
    private static final String ICON_LIGHT = "/assets/autopack/icon/icon_alt.png";
    private static final String THEME_SUFFIX = ".theme.json";

    public static FlatLaf forName(String themeName) {
        return forName(themeName, null);
    }

    public static FlatLaf forName(String themeName, Path configDir) {
        var lower = themeName.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "material-dark", "dark" -> new FlatDarkLaf();
            case "intellij-light", "intellij" -> new FlatIntelliJLaf();
            case "intellij-dark", "dracula" -> new FlatDarculaLaf();
            case "mac-light" -> new FlatMacLightLaf();
            case "mac-dark" -> new FlatMacDarkLaf();
            default -> loadThemeJson(themeName, configDir).orElseGet(FlatLightLaf::new);
        };
    }

    private static Optional<FlatLaf> loadThemeJson(String themeName, Path configDir) {
        if (!looksLikeThemePath(themeName)) {
            return Optional.empty();
        }
        try {
            Optional<InputStream> stream = openThemeStream(themeName, configDir);
            if (stream.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = stream.get()) {
                return Optional.of(IntelliJTheme.createLaf(in));
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeThemePath(String themeName) {
        return themeName.endsWith(THEME_SUFFIX) || themeName.contains("/");
    }

    private static Optional<InputStream> openThemeStream(String themeName, Path configDir) throws IOException {
        var resource = UITheme.class.getResourceAsStream("/" + themeName);
        if (resource != null) {
            return Optional.of(resource);
        }
        var path = Paths.get(themeName);
        if (!path.isAbsolute() && configDir != null) {
            path = configDir.resolve(themeName);
        }
        if (Files.exists(path)) {
            return Optional.of(Files.newInputStream(path));
        }
        return Optional.empty();
    }

    public static void apply(String themeName, Path configDir, LoggerDelegate logger) {
        try {
            UIManager.put("ClassLoader", FlatLaf.class.getClassLoader());
            FlatLightLaf.setup();
            UIManager.setLookAndFeel(forName(themeName, configDir));
        } catch (Throwable e) {
            logger.warn("Unable to set UI look and feel", e);
        }
    }

    public static Image getDefaultIcon(LoggerDelegate logger) {
        try {
            var lookAndFeel = UIManager.getLookAndFeel();
            if (lookAndFeel instanceof FlatLaf laf) {
                if (!laf.isDark()) {
                    return ImageIO.read(Objects.requireNonNull(UITheme.class.getResourceAsStream(ICON_LIGHT)));
                }
            }
            return ImageIO.read(Objects.requireNonNull(UITheme.class.getResourceAsStream(ICON_DARK)));
        } catch (Exception e) {
            logger.warn("Unable to load built-in app icon", e);
            return new BufferedImage(64, 64, ColorSpace.TYPE_RGB);
        }
    }
}
