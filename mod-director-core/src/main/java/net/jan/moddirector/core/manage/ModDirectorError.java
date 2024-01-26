package net.jan.moddirector.core.manage;

import lombok.Getter;

import java.util.logging.Level;

@Getter
public class ModDirectorError {
    private final Level level;
    private final String message;
    private final Throwable exception;

    public ModDirectorError(Level level, String message) {
        this.level = level;
        this.message = message;
        this.exception = null;
    }

    public ModDirectorError(Level level, Throwable exception) {
        this.level = level;
        this.message = exception.getMessage();
        this.exception = exception;
    }

    public ModDirectorError(Level level, String message, Throwable exception) {
        this.level = level;
        this.message = message;
        this.exception = exception;
    }
}
