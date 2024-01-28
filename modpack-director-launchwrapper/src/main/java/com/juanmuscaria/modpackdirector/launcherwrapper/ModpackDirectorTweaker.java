package com.juanmuscaria.modpackdirector.launcherwrapper;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.launcherwrapper.forge.ForgeLateLoader;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import lombok.Getter;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class ModpackDirectorTweaker implements ITweaker, PlatformDelegate {
    private final LoggerDelegate logger = new Log4jLogger(LogManager.getLogger("ModpackDirector"));
    private final ModpackDirector director;

    private List<String> args;
    @Getter
    private File gameDir;
    private File assetsDir;
    @Getter
    private String profile;
    private LaunchClassLoader classLoader;
    private Side side;

    public ModpackDirectorTweaker() {
        this.director = new ModpackDirector(this);
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        this.args = args;
        this.gameDir = gameDir;
        if (gameDir == null) {
            this.gameDir = new File(".").getAbsoluteFile();
            logger().debug("Fixing null game directory to {0}", this.gameDir.getPath());
        }

        this.assetsDir = assetsDir;
        this.profile = profile;
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        this.classLoader = classLoader;
        URL minecraftMainClass =
            classLoader.getResource("net/minecraft/client/main/Main.class");
        if (minecraftMainClass != null) {
            side = Side.CLIENT;
        } else {
            side = Side.SERVER;
        }
        logger.info("Detected side: {0}", side);

        try {
            if (!director.call()) {
                director.errorExit();
            }
        } catch (Exception e) {
            director.addError(new ModDirectorError(Level.SEVERE, "Activation error", e));
            director.errorExit();
        }

        if (!director.getInstalledMods().isEmpty()) {
            ForgeLateLoader loader = new ForgeLateLoader(this, director, classLoader);
            loader.execute();
        }
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    public void callInjectedTweaker(ITweaker tweaker) {
        tweaker.acceptOptions(args, gameDir, assetsDir, profile);
        tweaker.injectIntoClassLoader(classLoader);
    }

    @Override
    public String name() {
        return "Launchwrapper";
    }

    @Override
    public Path configurationDirectory() {
        File configDir = new File(gameDir, "config/mod-director");
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new UncheckedIOException(new IOException("Failed to create config directory " +
                configDir.getAbsolutePath()));
        }

        return configDir.toPath();
    }

    @Override
    public Path modFile(String modFileName) {
        return gameDir.toPath().resolve("mods").resolve(modFileName);
    }

    @Override
    public Path customFile(String modFileName, String modFolderName) {
        return gameDir.toPath().resolve(modFolderName).resolve(modFileName);
    }

    @Override
    public Path rootFile(String modFileName) {
        return gameDir.toPath().resolve(modFileName);
    }

    @Override
    public Path installationRoot() {
        return gameDir.toPath();
    }

    @Override
    public LoggerDelegate logger() {
        return logger;
    }

    @Override
    public Side side() {
        if (side == null) {
            throw new IllegalStateException("Too Early!");
        }
        return side;
    }


    @Override
    public boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }
}

class Log4jLogger implements LoggerDelegate {
    private final Logger logger;

    public Log4jLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void log(Level level, String message, Object... format) {
        var log4jLevel = translate(level);
        if (logger.isEnabled(log4jLevel)) {

            Throwable throwable = null;
            if (format.length > 0 && format[format.length - 1] instanceof Throwable) {
                throwable = (Throwable) format[format.length - 1];
                format = Arrays.copyOf(format, format.length - 1);
            }
            logger.log(log4jLevel, javaLoggingFormat(message, format));
            if (throwable != null) {
                logger.log(log4jLevel, "Exception: ", throwable);
            }
        }
    }

    private org.apache.logging.log4j.Level translate(Level level) {
        if (level.intValue() >= Level.OFF.intValue()) {
            return org.apache.logging.log4j.Level.OFF;
        } else if (level.intValue() >= Level.SEVERE.intValue()) {
            return org.apache.logging.log4j.Level.ERROR;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return org.apache.logging.log4j.Level.WARN;
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return org.apache.logging.log4j.Level.INFO;
        } else if (level.intValue() >= Level.FINE.intValue()) {
            return org.apache.logging.log4j.Level.DEBUG;
        } else if (level.intValue() >= Level.FINEST.intValue()) {
            return org.apache.logging.log4j.Level.TRACE;
        } else if (level.intValue() >= Level.ALL.intValue()) {
            return org.apache.logging.log4j.Level.ALL;
        }
        return org.apache.logging.log4j.Level.INFO;
    }
}