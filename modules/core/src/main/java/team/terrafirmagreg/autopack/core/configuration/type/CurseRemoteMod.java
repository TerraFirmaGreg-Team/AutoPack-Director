package team.terrafirmagreg.autopack.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
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

@Jacksonized
@SuperBuilder
@Getter
public class CurseRemoteMod extends RemoteMod {
    @JsonProperty(required = true)
    private final int addonId;

    @JsonProperty(required = true)
    private final int fileId;

    @JsonProperty
    private final String fileName;

    private CurseAddonFileInformation information;

    @Override
    public String remoteType() {
        return "CurseForge";
    }

    @Override
    public String offlineName() {
        return "Project ID: " + addonId + ", File ID: " + fileId;
    }

    @Override
    public String remoteUrl() {
        return information.getDownloadUrl().toString();
    }

    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, Director director, RemoteModInformation information) throws InstallException {

        try (WebGetResponse response = WebClient.get(this.information.downloadUrl)) {
            progressCallback.setSteps(1);
            IOOperation.copy(response.getInputStream(), Files.newOutputStream(targetFile), progressCallback,
                    response.getStreamSize());
        } catch (IOException e) {
            throw new InstallException("Failed to download file", e);
        }
    }

    @Override
    public RemoteModInformation queryInformation() throws InstallException {
        try {
            URL apiUrl = new URL(String.format("https://api.curse.tools/v1/cf/mods/%s/files/%s", addonId, fileId));
            WebGetResponse response = WebClient.get(apiUrl);
            JsonNode jsonObject;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                jsonObject = ConfigurationController.OBJECT_MAPPER.readTree(reader).get("data");
            }
            information = ConfigurationController.OBJECT_MAPPER.convertValue(jsonObject, CurseAddonFileInformation.class);
        } catch (MalformedURLException e) {
            throw new InstallException("Failed to create curse.tools api url", e);
        } catch (JsonParseException e) {
            throw new InstallException("Failed to parse Json response from curse", e);
        } catch (JsonMappingException e) {
            throw new InstallException("Failed to map Json response from curse, did they change their api?", e);
        } catch (IOException e) {
            throw new InstallException("Failed to open connection to curse", e);
        }

        if (fileName != null) {
            return new RemoteModInformation(fileName, fileName);
        } else {
            return new RemoteModInformation(information.displayName, information.fileName);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class CurseAddonFileInformation {
        @JsonProperty
        private String displayName;

        @JsonProperty
        private String fileName;

        @JsonProperty
        private URL downloadUrl;

        @JsonProperty
        private String[] gameVersions;
    }
}
