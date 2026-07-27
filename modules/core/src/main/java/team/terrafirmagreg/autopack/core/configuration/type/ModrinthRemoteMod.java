package team.terrafirmagreg.autopack.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import team.terrafirmagreg.autopack.Director;
import lombok.Getter;
import team.terrafirmagreg.autopack.core.configuration.*;
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
import java.util.Map;

public class ModrinthRemoteMod extends RemoteMod {
    private static final String MODRINTH_API_VERSIONS_URL = "https://api.modrinth.com/v2/version/%s";

    private final String versionId;
    private final int fileIndex;
    private final String fileName;
    private ModrinthFileInformation information;

    @JsonCreator
    @Builder
    public ModrinthRemoteMod(
        @JsonProperty(value = "versionId", required = true) String versionId,
        @JsonProperty(value = "fileIndex") int fileIndex,
        @JsonProperty(value = "metadata") RemoteModMetadata metadata,
        @JsonProperty(value = "installationPolicy") InstallationPolicy installationPolicy,
        @JsonProperty(value = "options") Map<String, Object> options,
        @JsonProperty(value = "folder") String folder,
        @JsonProperty(value = "inject") Boolean inject,
        @JsonProperty(value = "fileName") String fileName
    ) {
        super(metadata, installationPolicy, options, folder, inject);
        this.versionId = versionId;
        this.fileIndex = fileIndex;
        this.fileName = fileName;
    }

    @Override
    public String remoteType() {
        return "Modrinth";
    }

    @Override
    public String offlineName() {
        return versionId;
    }

    @Override
    public String remoteUrl() {
        return information.getUrl().toString();
    }

    @Override
    public RemoteModInformation queryInformation() throws InstallException {
        try {
            URL apiUrl = new URL(String.format(MODRINTH_API_VERSIONS_URL, versionId));
            WebGetResponse response = WebClient.get(apiUrl);
            JsonNode jsonObject;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                jsonObject = ConfigurationController.OBJECT_MAPPER.readTree(reader).get("files").get(fileIndex);
            }
            if (jsonObject == null) {
                throw new InstallException("No such file at index " + fileIndex);
            }
            information = ConfigurationController.OBJECT_MAPPER.convertValue(jsonObject, ModrinthFileInformation.class);
        } catch (MalformedURLException e) {
            throw new InstallException("Failed to create modrinth api url", e);
        } catch (JsonParseException e) {
            throw new InstallException("Failed to parse Json response from modrinth", e);
        } catch (JsonMappingException e) {
            throw new InstallException("Failed to map Json response from modrinth, did they change their api?", e);
        } catch (IOException e) {
            throw new InstallException("Failed to open connection to modrinth", e);
        }

        if (fileName != null) {
            return new RemoteModInformation(fileName, fileName);
        } else {
            return new RemoteModInformation(information.filename, information.filename);
        }
    }

    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, Director director, RemoteModInformation information) throws InstallException {
        try (WebGetResponse response = WebClient.get(this.information.getUrl())) {
            progressCallback.setSteps(1);
            IOOperation.copy(response.getInputStream(), Files.newOutputStream(targetFile), progressCallback,
                response.getStreamSize());
        } catch (IOException e) {
            throw new InstallException("Failed to download file", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class ModrinthFileInformation {
        @JsonProperty
        private String filename;

        @JsonProperty
        private URL url;
    }
}
