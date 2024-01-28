package com.juanmuscaria.modpackdirector.modlauncher;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.logging.LoggerDelegate;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import com.juanmuscaria.modpackdirector.util.Side;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.jan.moddirector.core.manage.ModDirectorError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.logging.Level;

public class ModpackDirectorService implements ITransformationService, PlatformDelegate {
    private final LoggerDelegate logger = new Log4jLogger(LogManager.getLogger("ModpackDirector"));
    private Side side = Side.UNKNOWN;
    private Path gameDir;

    @Override
    public void initialize(IEnvironment env) {
        this.side = figureOutSide(env);
        this.gameDir = env.getProperty(IEnvironment.Keys.GAMEDIR.get()).get();
        ModpackDirector director = new ModpackDirector(this);
        logger.info("Detected side: {0}", side);

        try {
            if (!director.call()) {
                director.errorExit();
            }
        } catch (Exception e) {
            director.addError(new ModDirectorError(Level.SEVERE, "Activation error", e));
            director.errorExit();
        }
    }

    @Override
    public void beginScanning(IEnvironment iEnvironment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {

    }

    @Override
    public String name() {
        return "ModpackDirectorModlauncher";
    }

    @Override
    public Path configurationDirectory() {
        File configDir = new File(gameDir.toFile(), "config/mod-director");
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new UncheckedIOException(new IOException("Failed to create config directory " +
                configDir.getAbsolutePath()));
        }

        return configDir.toPath();
    }

    @Override
    public Path modFile(String modFileName) {
        return gameDir.resolve("mods").resolve(modFileName);
    }

    @Override
    public Path customFile(String modFileName, String modFolderName) {
        return gameDir.resolve(modFolderName).resolve(modFileName);
    }

    @Override
    public Path rootFile(String modFileName) {
        return gameDir.resolve(modFileName);
    }

    @Override
    public Path installationRoot() {
        return gameDir;
    }

    @Override
    public LoggerDelegate logger() {
        return logger;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public boolean headless() {
        return GraphicsEnvironment.isHeadless();
    }

    public List<ITransformer> transformers() {
        return new ArrayList<>();
    }

    // based on https://github.com/SpongePowered/Mixin/blob/41a68854f6e63e8ec6d38e7d7612230d7f73a9bc/src/modlauncher/java/org/spongepowered/asm/launch/platform/MixinPlatformAgentMinecraftForge.java#L74
    private Side figureOutSide(IEnvironment environment) {
        final String launchTarget = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("missing").toLowerCase(Locale.ROOT);
        if (launchTarget.contains("server")) {
            return Side.SERVER;
        }
        if (launchTarget.contains("client")) {
            return Side.CLIENT;
        }
        Optional<ILaunchHandlerService> launchHandler = environment.findLaunchHandler(launchTarget);
        if (launchHandler.isPresent()) {
            ILaunchHandlerService service = launchHandler.get();
            try {
                Method mdGetDist = service.getClass().getDeclaredMethod("getDist");
                String strDist = mdGetDist.invoke(service).toString().toLowerCase(Locale.ROOT);
                if (strDist.contains("server")) {
                    return Side.SERVER;
                }
                if (strDist.contains("client")) {
                    return Side.CLIENT;
                }
            } catch (Exception e) {
                logger.warn("Unable to get side", e);
                return Side.UNKNOWN;
            }
        }
        logger.warn("Unable to get side");
        return Side.UNKNOWN;
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
