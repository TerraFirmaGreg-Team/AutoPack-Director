package team.terrafirmagreg.autopack.logging;

import java.text.MessageFormat;

public final class MessageFormats {
    private MessageFormats() {}

    public static String format(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return MessageFormat.format(message, args);
        } catch (Exception ex) {
            return message;
        }
    }
}
