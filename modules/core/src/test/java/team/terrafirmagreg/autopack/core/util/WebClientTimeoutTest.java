package team.terrafirmagreg.autopack.core.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebClientTimeoutTest {

    @Test
    void getThrowsOnReadTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            Thread acceptThread = new Thread(() -> {
                try {
                    Socket accepted = server.accept();
                    Thread.sleep(60_000);
                    accepted.close();
                } catch (Exception ignored) {
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            URL url = new URL("http://127.0.0.1:" + port + "/test");
            assertThrows(IOException.class, () -> WebClient.get(url));
        }
    }
}
