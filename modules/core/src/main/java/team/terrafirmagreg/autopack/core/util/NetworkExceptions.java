package team.terrafirmagreg.autopack.core.util;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class NetworkExceptions {
    private NetworkExceptions() {
    }

    public static boolean isConnectivityError(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException
                || t instanceof ConnectException
                || t instanceof NoRouteToHostException
                || t instanceof SocketTimeoutException
                || t instanceof SSLException
                || t instanceof SocketException) {
                return true;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return false;
    }

    public static String describe(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof UnknownHostException) {
                return "Could not resolve host \"" + t.getMessage() + "\". "
                    + "Please check your internet connection or DNS settings.";
            }
            if (t instanceof SocketTimeoutException) {
                return "The connection timed out. The server may be unreachable or your internet "
                    + "connection may be too slow/unstable.";
            }
            if (t instanceof ConnectException) {
                return "Could not connect to the server (" + t.getMessage() + "). "
                    + "Please check your internet connection.";
            }
            if (t instanceof NoRouteToHostException) {
                return "No route to host (" + t.getMessage() + "). "
                    + "Please check your internet connection or firewall settings.";
            }
            if (t instanceof SSLException) {
                return "A secure connection could not be established (" + t.getMessage() + "). "
                    + "This may be caused by an outdated system, a proxy, or antivirus interference.";
            }
            if (t instanceof SocketException) {
                return "The network connection was interrupted (" + t.getMessage() + "). "
                    + "Please check your internet connection.";
            }
            if (t == t.getCause()) {
                break;
            }
        }

        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
