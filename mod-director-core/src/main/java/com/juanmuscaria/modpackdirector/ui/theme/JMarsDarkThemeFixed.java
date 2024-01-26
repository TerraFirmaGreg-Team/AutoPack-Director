package com.juanmuscaria.modpackdirector.ui.theme;

import mdlaf.themes.JMarsDarkTheme;

import javax.swing.plaf.ColorUIResource;

// Progress bar texts where barely visible with the original colors
public class JMarsDarkThemeFixed extends JMarsDarkTheme {
    @Override
    protected void installColor() {
        super.installColor();
        this.backgroundProgressBar = new ColorUIResource(48, 49, 58);
        this.foregroundProgressBar = this.highlightBackgroundPrimary;
    }
}
