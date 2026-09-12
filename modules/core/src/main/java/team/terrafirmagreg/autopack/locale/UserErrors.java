package team.terrafirmagreg.autopack.locale;

import team.terrafirmagreg.autopack.manage.InstallError;
import team.terrafirmagreg.autopack.util.NetworkExceptions;

public final class UserErrors {
    private UserErrors() {}

    public static String formatDetail(InstallError error) {
        Throwable exception = error.getException();
        if (exception == null) {
            return null;
        }
        if (NetworkExceptions.isConnectivityError(exception)) {
            return NetworkExceptions.describe(exception);
        }
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        if (exception.getMessage() != null && !exception.getMessage().equals(error.getMessage())) {
            return exception.getMessage();
        }
        return null;
    }
}
