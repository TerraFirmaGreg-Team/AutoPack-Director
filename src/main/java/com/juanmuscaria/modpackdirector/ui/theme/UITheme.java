package com.juanmuscaria.modpackdirector.ui.theme;

import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import mdlaf.MaterialLookAndFeel;
import mdlaf.themes.MaterialLiteTheme;
import mdlaf.themes.MaterialOceanicTheme;
import mdlaf.themes.MaterialTheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Objects;

public class UITheme {
    public static MaterialTheme forName(String themeName) {
        return switch (themeName.toLowerCase(Locale.ROOT)) {
            default -> new MaterialLiteTheme();
            case "material-dark", "jmars-dark" -> new JMarsDarkThemeFixed();
            case "material-oceanic" -> new MaterialOceanicTheme();
        };
    }

    public static void apply(String themeName, LoggerDelegate logger) {
        try {
            UIManager.setLookAndFeel(new MaterialLookAndFeel(forName(themeName)));
        } catch (Throwable e) {
            logger.warn("Unable to set UI look and feel", e);
        }
    }

    public static Image getDefaultIcon(LoggerDelegate logger) {
        try {
            var lookAndFeel = UIManager.getLookAndFeel();
            if (lookAndFeel instanceof MaterialLookAndFeel materialLookAndFeel) {
                if (materialLookAndFeel.getTheme() instanceof MaterialLiteTheme) {
                    return ImageIO.read(Objects.requireNonNull(UITheme.class.getResourceAsStream("/modpackdirector/ModpackDirectorAlt.png")));
                }
            }
            return ImageIO.read(Objects.requireNonNull(UITheme.class.getResourceAsStream("/modpackdirector/ModpackDirector.png")));
        } catch (Exception e) {
            logger.warn("Unable to load built-in app icon", e);
            return new BufferedImage(64, 64, ColorSpace.TYPE_RGB);
        }
    }
}
