package team.terrafirmagreg.autopack.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebClientTimeoutTest {

    @Test
    @Timeout(40)
    void getThrowsOnReadTimeout() throws Exception {
        Thread acceptThread = null;
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();

            acceptThread = new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            URL url = new URL("http://127.0.0.1:" + port + "/test");
            assertThrows(IOException.class, () -> WebClient.get(url));
        } finally {
            if (acceptThread != null) {
                acceptThread.interrupt();
                acceptThread.join(1000);
            }
        }
    }
}
