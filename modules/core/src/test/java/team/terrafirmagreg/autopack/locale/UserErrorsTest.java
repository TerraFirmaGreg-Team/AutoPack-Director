package team.terrafirmagreg.autopack.locale;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ConnectException;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.autopack.manage.InstallError;

class UserErrorsTest {

    @BeforeEach
    void initMessages() {
        Messages.init("en_us");
    }

    @Test
    void formatDetailUsesLocalizedNetworkMessage() {
        InstallError error = new InstallError(
                Level.SEVERE,
                Messages.get("autopack.error.install_failed", "Test Mod", ""),
                new ConnectException("Connection refused"));

        String detail = UserErrors.formatDetail(error);
        assertNotNull(detail);
        assertTrue(detail.contains("connect"));
    }

    @Test
    void formatDetailReturnsNullWithoutException() {
        InstallError error = new InstallError(Level.SEVERE, "plain error");

        assertNull(UserErrors.formatDetail(error));
    }
}
