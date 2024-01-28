package net.jan.moddirector.core.configuration.type;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.juanmuscaria.modpackdirector.ModpackDirector;
import net.jan.moddirector.core.configuration.*;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ProgressCallback;
import net.jan.moddirector.core.util.IOOperation;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CurseRemoteMod extends ModDirectorRemoteMod {
    private final int addonId;
    private final int fileId;
    private final String fileName;

    private CurseAddonFileInformation information;

    @JsonCreator
    public CurseRemoteMod(
        @JsonProperty(value = "addonId", required = true) int addonId,
        @JsonProperty(value = "fileId", required = true) int fileId,
        @JsonProperty(value = "metadata") RemoteModMetadata metadata,
        @JsonProperty(value = "installationPolicy") InstallationPolicy installationPolicy,
        @JsonProperty(value = "options") Map<String, Object> options,
        @JsonProperty(value = "folder") String folder,
        @JsonProperty(value = "inject") Boolean inject,
        @JsonProperty(value = "fileName") String fileName
    ) {
        super(metadata, installationPolicy, options, folder, inject);
        this.addonId = addonId;
        this.fileId = fileId;
        this.fileName = fileName;
    }

    @Override
    public String remoteType() {
        return "Curse";
    }

    @Override
    public String offlineName() {
        return addonId + ":" + fileId;
    }

    @Override
    public void performInstall(Path targetFile, ProgressCallback progressCallback, ModpackDirector director, RemoteModInformation information) throws ModDirectorException {

        try (WebGetResponse response = WebClient.get(this.information.downloadUrl)) {
            progressCallback.setSteps(1);
            IOOperation.copy(response.getInputStream(), Files.newOutputStream(targetFile), progressCallback,
                response.getStreamSize());
        } catch (IOException e) {
            throw new ModDirectorException("Failed to download file", e);
        }
    }

    @Override
    public RemoteModInformation queryInformation() throws ModDirectorException {
        try {
            URL apiUrl = new URL(String.format("https://api.curse.tools/v1/cf/mods/%s/files/%s", addonId, fileId));
            WebGetResponse response = WebClient.get(apiUrl);
            JsonNode jsonObject;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8))) {
                jsonObject = ConfigurationController.OBJECT_MAPPER.readTree(reader).get("data");
            }
            information = ConfigurationController.OBJECT_MAPPER.convertValue(jsonObject, CurseAddonFileInformation.class);
        } catch (MalformedURLException e) {
            throw new ModDirectorException("Failed to create curse.tools api url", e);
        } catch (JsonParseException e) {
            throw new ModDirectorException("Failed to parse Json response from curse", e);
        } catch (JsonMappingException e) {
            throw new ModDirectorException("Failed to map Json response from curse, did they change their api?", e);
        } catch (IOException e) {
            throw new ModDirectorException("Failed to open connection to curse", e);
        }

        if (fileName != null) {
            return new RemoteModInformation(fileName, fileName);
        } else {
            return new RemoteModInformation(information.displayName, information.fileName);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CurseAddonFileInformation {
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
