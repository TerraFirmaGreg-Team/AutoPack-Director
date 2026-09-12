package team.terrafirmagreg.autopack.util;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import team.terrafirmagreg.autopack.locale.Messages;

public final class NetworkExceptions {
    private NetworkExceptions() {}

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
                return Messages.get("autopack.network.unknown_host", t.getMessage());
            }
            if (t instanceof SocketTimeoutException) {
                return Messages.get("autopack.network.timeout");
            }
            if (t instanceof ConnectException) {
                return Messages.get("autopack.network.connect", t.getMessage());
            }
            if (t instanceof NoRouteToHostException) {
                return Messages.get("autopack.network.no_route", t.getMessage());
            }
            if (t instanceof SSLException) {
                return Messages.get("autopack.network.ssl", t.getMessage());
            }
            if (t instanceof SocketException) {
                return Messages.get("autopack.network.socket", t.getMessage());
            }
            if (t == t.getCause()) {
                break;
            }
        }

        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}
