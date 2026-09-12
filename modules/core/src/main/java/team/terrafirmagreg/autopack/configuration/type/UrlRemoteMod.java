package team.terrafirmagreg.autopack.configuration.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.configuration.RemoteMod;
import team.terrafirmagreg.autopack.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.exception.InstallException;
import team.terrafirmagreg.autopack.locale.Messages;
import team.terrafirmagreg.autopack.manage.ProgressCallback;
import team.terrafirmagreg.autopack.util.IOOperation;
import team.terrafirmagreg.autopack.util.WebClient;
import team.terrafirmagreg.autopack.util.WebGetResponse;

@Jacksonized
@SuperBuilder
@Getter
public class UrlRemoteMod extends RemoteMod {
    @JsonProperty
    private final String fileName;

    @JsonProperty(required = true)
    private final URL url;

    @JsonProperty
    @Builder.Default
    private final String[] follows = new String[0];

    @Override
    public String remoteType() {
        return url.getHost();
    }

    @Override
    public String offlineName() {
        return url.getFile().isEmpty() ? "<no name>" : url.getFile();
    }

    @Override
    public String offlineTargetFilename() {
        if (fileName != null) {
            return fileName;
        }
        return Paths.get(url.getFile()).getFileName().toString();
    }

    @Override
    public String remoteUrl() {
        return url.toString();
    }

    // TODO: Move URL following to query instead
    @Override
    public void performInstall(
            Path targetFile, ProgressCallback progressCallback, Director director, RemoteModInformation information)
            throws InstallException {
        byte[] data = null;

        progressCallback.setSteps(follows.length + 1);

        URL urlToFollow = null;
        for (int i = -1; i < follows.length; i++) {
            if (i < 0) {
                urlToFollow = url;
            } else {
                urlToFollow = resolveFollowUrl(urlToFollow, new String(data), follows[i]);
            }

            if (i + 1 == follows.length) {
                progressCallback.message(Messages.get("autopack.progress.download_final"));
            } else {
                progressCallback.message(Messages.get("autopack.progress.follow_redirect", i + 2, follows.length));
            }

            director.checkUrl(urlToFollow);

            try (WebGetResponse response = WebClient.get(urlToFollow)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                IOOperation.copy(response.getInputStream(), outputStream, progressCallback, response.getStreamSize());
                data = outputStream.toByteArray();
            } catch (IOException e) {
                throw new InstallException("Failed to follow URLs to download file", e);
            }

            progressCallback.step();
        }

        try {
            Files.write(targetFile, data);

            if (this.getInstallationPolicy().extract()) {
                try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(data))) {
                    byte[] buffer = new byte[8192];
                    ZipEntry zipEntry = zipInputStream.getNextEntry();
                    while (zipEntry != null) {
                        Path newFilePath = Paths.get(targetFile.getParent().toString(), zipEntry.getName());
                        if (!zipEntry.isDirectory()) {
                            if (Files.exists(newFilePath)) {
                                Path disabledFilePath =
                                        newFilePath.resolveSibling(zipEntry.getName() + ".disabled-by-mod-director");
                                if (Files.exists(disabledFilePath)) {
                                    Files.delete(disabledFilePath);
                                }
                                Files.move(newFilePath, disabledFilePath);
                            }
                            progressCallback.message(
                                    Messages.get("autopack.progress.unzip", newFilePath.getFileName()));
                            try (FileOutputStream fileOutputStream = new FileOutputStream(newFilePath.toFile())) {
                                int length;
                                while ((length = zipInputStream.read(buffer)) > 0) {
                                    fileOutputStream.write(buffer, 0, length);
                                }
                            }
                        } else {
                            Files.createDirectories(newFilePath);
                        }
                        zipEntry = zipInputStream.getNextEntry();
                    }
                }
                if (this.getInstallationPolicy().deleteAfterExtract()) {
                    Files.delete(targetFile);
                }
            }
        } catch (IOException e) {
            throw new InstallException("Failed to write file to disk", e);
        }

        progressCallback.done();
    }

    static URL resolveFollowUrl(URL currentUrl, String html, String followMarker) throws InstallException {
        int startIndex = html.indexOf(followMarker);
        if (startIndex < 0) {
            throw new InstallException("Unable to find follow string " + followMarker + " in html from " + currentUrl);
        }

        int href = html.substring(0, startIndex).lastIndexOf("href=") + 5;
        char hrefEnclose = html.charAt(href);
        int hrefEnd = html.indexOf(hrefEnclose, href + 2);

        String newUrl = html.substring(href + 1, hrefEnd);
        if (newUrl.isEmpty()) {
            throw new InstallException(
                    "Result url was empty when matching " + followMarker + " in html from " + currentUrl);
        }

        try {
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                if (!newUrl.startsWith("/")) {
                    newUrl = "/" + newUrl;
                }
                return new URL(currentUrl.getProtocol(), currentUrl.getHost(), newUrl);
            }
            return new URL(newUrl);
        } catch (MalformedURLException e) {
            throw new InstallException("Failed to create follow url when using follow " + followMarker, e);
        }
    }

    @Override
    public RemoteModInformation queryInformation() {
        if (fileName != null) {
            return new RemoteModInformation(fileName, fileName);
        } else {
            String name = Paths.get(url.getFile()).getFileName().toString();

            return new RemoteModInformation(name, name);
        }
    }
}
