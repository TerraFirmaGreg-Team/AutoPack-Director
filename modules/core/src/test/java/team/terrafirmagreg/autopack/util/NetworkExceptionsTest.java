package team.terrafirmagreg.autopack.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.autopack.locale.Messages;

class NetworkExceptionsTest {

    @BeforeEach
    void initMessages() {
        Messages.init("en_us");
    }

    @Test
    void describeWithMessagesUsesLocalizedConnectText() {
        String description = NetworkExceptions.describe(new ConnectException("refused"));

        assertEquals(Messages.get("autopack.network.connect", "refused"), description);
    }

    @Test
    void describeWithMessagesUsesLocalizedUnknownHostText() {

        String description = NetworkExceptions.describe(new UnknownHostException("example.com"));

        assertEquals(Messages.get("autopack.network.unknown_host", "example.com"), description);
    }

    @Test
    void isConnectivityErrorDetectsConnectException() {
        assertTrue(NetworkExceptions.isConnectivityError(new ConnectException("refused")));
    }
}
