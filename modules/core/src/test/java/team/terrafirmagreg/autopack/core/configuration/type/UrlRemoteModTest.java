package team.terrafirmagreg.autopack.core.configuration.type;

import org.junit.jupiter.api.Test;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.exception.InstallException;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlRemoteModTest {

    @Test
    void queryInformationUsesExplicitFileName() throws Exception {
        UrlRemoteMod mod = UrlRemoteMod.builder()
            .fileName("named.jar")
            .url(new URL("https://example.com/download"))
            .build();

        RemoteModInformation information = mod.queryInformation();

        assertEquals("named.jar", information.displayName());
        assertEquals("named.jar", information.targetFilename());
    }

    @Test
    void queryInformationDerivesNameFromUrl() throws Exception {
        UrlRemoteMod mod = UrlRemoteMod.builder()
            .url(new URL("https://example.com/files/derived.jar"))
            .build();

        RemoteModInformation information = mod.queryInformation();

        assertEquals("derived.jar", information.targetFilename());
    }

    @Test
    void resolveFollowUrlFindsAbsoluteHref() throws Exception {
        URL current = new URL("https://example.com/page");
        String html = "<html><a href=\"https://cdn.example.com/mod.jar\">download</a></html>";

        URL resolved = UrlRemoteMod.resolveFollowUrl(current, html, "download");

        assertEquals("https://cdn.example.com/mod.jar", resolved.toString());
    }

    @Test
    void resolveFollowUrlResolvesRelativeHref() throws Exception {
        URL current = new URL("https://example.com/page");
        String html = "<html><a href=\"files/mod.jar\">download</a></html>";

        URL resolved = UrlRemoteMod.resolveFollowUrl(current, html, "download");

        assertEquals("https://example.com/files/mod.jar", resolved.toString());
    }

    @Test
    void resolveFollowUrlThrowsWhenMarkerMissing() throws Exception {
        URL current = new URL("https://example.com/page");

        InstallException error = assertThrows(InstallException.class,
            () -> UrlRemoteMod.resolveFollowUrl(current, "<html></html>", "missing"));

        assertTrue(error.getMessage().contains("missing"));
    }
}
