package team.terrafirmagreg.autopack.core.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebClientTest {

    @Test
    void followsRedirectToFinalResource() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();

        server.createContext("/final", exchange -> {
            byte[] body = "final-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=abc");
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/final");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + port + "/start");
            try (WebGetResponse response = WebClient.get(url)) {
                assertEquals("final-body", readBody(response.getInputStream()));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tooManyRedirectsThrows() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + port + "/redirect");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + port + "/redirect");
            IOException error = assertThrows(IOException.class, () -> WebClient.get(url));
            assertTrue(error.getMessage().contains("redirect"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonHttpRedirectThrows() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().set("Location", "file:///etc/passwd");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + port + "/start");
            IOException error = assertThrows(IOException.class, () -> WebClient.get(url));
            assertTrue(error.getMessage().contains("not http"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessStatusThrows() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/forbidden", exchange -> {
            byte[] body = "blocked".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            URL url = new URL("http://127.0.0.1:" + port + "/forbidden");
            IOException error = assertThrows(IOException.class, () -> WebClient.get(url));
            assertTrue(error.getMessage().contains("403"));
            assertTrue(error.getMessage().contains("/forbidden"));
        } finally {
            server.stop(0);
        }
    }

    private static String readBody(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[256];
        int read = inputStream.read(buffer);
        if (read < 0) {
            return "";
        }
        return new String(buffer, 0, read, StandardCharsets.UTF_8);
    }
}
