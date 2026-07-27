package team.terrafirmagreg.autopack.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;
import team.terrafirmagreg.autopack.core.util.IOOperation;
import team.terrafirmagreg.autopack.core.util.WebClient;
import team.terrafirmagreg.autopack.core.util.WebGetResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Jacksonized
@SuperBuilder
public class ModrinthRemoteMod extends RemoteMod {
    private static final String MODRINTH_API_VERSION_URL = "https://api.modrinth.com/v2/project/%s/version/%s";
    private static final String MODRINTH_API_PROJECT_URL = "https://api.modrinth.com/v2/project/%s";

    @JsonProperty(required = true)
    private final String addonId;

    @JsonProperty(required = true)
    private final String fileId;

    @JsonProperty
    @Builder.Default
    private final int fileIndex = 0;

    @JsonProperty
    private final String fileName;

    private ModrinthAddonFileInformation fileInformation;
    private String projectTitle;

    @Override
    public String remoteType() {
        return "Modrinth";
    }

    @Override
    public String offlineName() {
        return "Project ID: " + addonId + ", File ID: " + fileId;
    }

    @Override
    public String offlineTargetFilename() {
        return fileName;
    }

    @Override
    public String remoteUrl() {
        ModrinthFile file = selectedFile();
        if (file != null && file.url != null) {
            return file.url.toString();
        }
        return String.format(MODRINTH_API_VERSION_URL, addonId, fileId);
    }

    @Override
    public RemoteModInformation queryInformation() throws InstallException {
        queryTitle();
        try {
            URL apiUrl = new URL(String.format(MODRINTH_API_VERSION_URL, addonId, fileId));
            WebGetResponse response = WebClient.get(apiUrl);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                fileInformation = ConfigurationController.OBJECT_MAPPER.readValue(reader, ModrinthAddonFileInformation.class);
            }
        } catch (MalformedURLException e) {
            throw new InstallException("Failed to create Modrinth API URL", e);
        } catch (JsonParseException e) {
            throw new InstallException("Failed to parse JSON response from Modrinth", e);
        } catch (JsonMappingException e) {
            throw new InstallException("Failed to map JSON response from Modrinth, did they change their API?", e);
        } catch (IOException e) {
            throw new InstallException("Failed to open connection to Modrinth", e);
        }

        ModrinthFile file = selectedFile();
        if (file == null) {
            throw new InstallException("No such file at index " + fileIndex);
        }

        String displayName = projectTitle != null ? projectTitle : (fileName != null ? fileName : file.filename);
        String targetFilename = fileName != null ? fileName : file.filename;
        return new RemoteModInformation(displayName, targetFilename);
    }

    private void queryTitle() throws InstallException {
        try {
            URL projectUrl = new URL(String.format(MODRINTH_API_PROJECT_URL, addonId));
            WebGetResponse response = WebClient.get(projectUrl);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                ModrinthProjectInformation projectInformation =
                    ConfigurationController.OBJECT_MAPPER.readValue(reader, ModrinthProjectInformation.class);
                projectTitle = projectInformation.title;
            }
        } catch (MalformedURLException e) {
            throw new InstallException("Failed to create Modrinth project URL", e);
        } catch (JsonParseException e) {
            throw new InstallException("Failed to parse JSON response from Modrinth project", e);
        } catch (JsonMappingException e) {
            throw new InstallException("Failed to map JSON response from Modrinth project", e);
        } catch (IOException e) {
            throw new InstallException("Failed to open connection to Modrinth project", e);
        }
    }

    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, Director director, RemoteModInformation information) throws InstallException {
        ModrinthFile file = selectedFile();
        if (file == null || file.url == null) {
            throw new InstallException("No file available for download");
        }
        try (WebGetResponse response = WebClient.get(file.url)) {
            progressCallback.setSteps(1);
            IOOperation.copy(response.getInputStream(), Files.newOutputStream(targetFile), progressCallback,
                response.getStreamSize());
        } catch (IOException e) {
            throw new InstallException("Failed to download file", e);
        }
    }

    private ModrinthFile selectedFile() {
        if (fileInformation == null || fileInformation.files == null || fileInformation.files.isEmpty()) {
            return null;
        }
        if (fileIndex < 0 || fileIndex >= fileInformation.files.size()) {
            return null;
        }
        return fileInformation.files.get(fileIndex);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    private static class ModrinthAddonFileInformation {
        @JsonProperty("files")
        private List<ModrinthFile> files;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    private static class ModrinthFile {
        @JsonProperty
        private URL url;

        @JsonProperty
        private String filename;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    private static class ModrinthProjectInformation {
        @JsonProperty
        private String title;
    }
}
