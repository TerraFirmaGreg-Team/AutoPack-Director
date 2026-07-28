package team.terrafirmagreg.autopack.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

public class WebClient {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";
    public static final int CONNECT_TIMEOUT = 15_000;
    public static final int READ_TIMEOUT = 30_000;

    public static WebGetResponse get(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        applyTimeouts(connection);
        if (!(connection instanceof HttpURLConnection)) {
            return new WebGetResponse(connection.getInputStream(), connection.getContentLengthLong());
        }

        int redirectCount = 0;
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        httpConnection.setInstanceFollowRedirects(false);
        httpConnection.setRequestProperty("User-Agent", USER_AGENT);
        httpConnection.connect();

        while (true) {
            int status = httpConnection.getResponseCode();
            if (status >= 300 && status <= 399) {
                if (redirectCount > 10) {
                    throw new IOException("Server tried to redirect too many times");
                }

                String newUrl = httpConnection.getHeaderField("Location");
                if (newUrl == null || newUrl.isEmpty()) {
                    throw new IOException("Server sent redirect without Location for " + url);
                }
                String cookies = readSetCookieHeader(httpConnection);
                closeQuietly(httpConnection);

                try {
                    url = upgradeHttpToHttpsIfSameHost(url, new URL(newUrl));
                    connection = url.openConnection();
                    applyTimeouts(connection);

                    if (!(connection instanceof HttpURLConnection)) {
                        throw new IOException("Server sent a redirect url which was not http: " + newUrl);
                    }

                    redirectCount++;

                    httpConnection = (HttpURLConnection) connection;
                    httpConnection.setInstanceFollowRedirects(false);
                    if (cookies != null) {
                        httpConnection.setRequestProperty("Cookie", cookies);
                    }
                    httpConnection.setRequestProperty("User-Agent", USER_AGENT);
                    httpConnection.connect();
                } catch (MalformedURLException e) {
                    throw new IOException("Server sent invalid redirect url", e);
                }
            } else {
                break;
            }
        }

        int status = httpConnection.getResponseCode();
        if (status < 200 || status > 299) {
            closeQuietly(httpConnection);
            throw new IOException("Server returned HTTP response code: " + status + " for URL: " + url);
        }

        return new WebGetResponse(httpConnection.getInputStream(), httpConnection.getContentLengthLong());
    }

    private static URL upgradeHttpToHttpsIfSameHost(URL previous, URL redirect) throws MalformedURLException {
        if (!"https".equalsIgnoreCase(previous.getProtocol())
            || !"http".equalsIgnoreCase(redirect.getProtocol())
            || previous.getHost() == null
            || !previous.getHost().equalsIgnoreCase(redirect.getHost())) {
            return redirect;
        }

        int port = redirect.getPort();
        if (port == 80 || port == -1) {
            return new URL("https", redirect.getHost(), -1, redirect.getFile());
        }
        return new URL("https", redirect.getHost(), port, redirect.getFile());
    }

    private static void applyTimeouts(URLConnection connection) {
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
    }

    private static void closeQuietly(HttpURLConnection connection) {
        try {
            InputStream errorStream = connection.getErrorStream();
            if (errorStream != null) {
                errorStream.close();
            }
        } catch (IOException ignored) {
        }
        connection.disconnect();
    }

    private static String readSetCookieHeader(HttpURLConnection connection) {
        String cookies = connection.getHeaderField("Set-Cookie");
        if (cookies != null) {
            return cookies;
        }

        Map<String, List<String>> headerFields = connection.getHeaderFields();
        if (headerFields == null) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")
                && entry.getValue() != null && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }
}
