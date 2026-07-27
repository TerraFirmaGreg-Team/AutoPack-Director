package team.terrafirmagreg.autopack.core.pakku;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.core.manage.ProgressCallback;
import team.terrafirmagreg.autopack.core.util.IOOperation;
import team.terrafirmagreg.autopack.core.util.WebClient;
import team.terrafirmagreg.autopack.core.util.WebGetResponse;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class PakkuLockSync {
    private PakkuLockSync() {
    }

    public static void applyConfigs(PakkuDiff diff, LoggerDelegate logger) throws IOException {
        if (diff == null || !diff.hasConfigDrift()) {
            return;
        }
        ObjectMapper mapper = ConfigurationController.OBJECT_MAPPER;
        for (PakkuConfigChange change : diff.getConfigUpdates()) {
            Path target = change.getTargetPath();
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            ObjectNode node;
            if (Files.isRegularFile(target)) {
                try (InputStream stream = Files.newInputStream(target)) {
                    JsonNode existing = mapper.readTree(stream);
                    node = existing.isObject() ? (ObjectNode) existing : mapper.createObjectNode();
                }
            } else {
                node = mapper.createObjectNode();
            }

            Object addonId = change.getAddonId();
            if (addonId instanceof Integer) {
                node.put("addonId", (Integer) addonId);
            } else {
                node.put("addonId", String.valueOf(addonId));
            }

            if ("curseforge".equals(change.getPlatform())) {
                try {
                    node.put("fileId", Integer.parseInt(change.getFileId()));
                } catch (NumberFormatException e) {
                    node.put("fileId", change.getFileId());
                }
            } else {
                node.put("fileId", change.getFileId());
            }
            node.put("comment", change.getComment());

            mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), node);
            logger.info("Pakku config {0}: {1}", change.isCreate() ? "created" : "updated", target.getFileName());
        }
    }

    public static void fetchMissingMods(
        PakkuDiff diff,
        Path modsDirectory,
        ProgressCallback progress,
        LoggerDelegate logger
    ) throws IOException {
        if (diff == null || !diff.hasMissingMods()) {
            return;
        }
        Files.createDirectories(modsDirectory);
        progress.setSteps(diff.getMissingMods().size());

        for (PakkuMissingMod missing : diff.getMissingMods()) {
            Path target = modsDirectory.resolve(missing.getFileName());
            Path temp = modsDirectory.resolve(missing.getFileName() + ".tmp");
            progress.message(missing.getFileName());
            progress.title(missing.getFileName());

            try (WebGetResponse response = WebClient.get(missing.getDownloadUrl())) {
                IOOperation.copy(response.getInputStream(), Files.newOutputStream(temp), progress, response.getStreamSize());
            } catch (IOException e) {
                Files.deleteIfExists(temp);
                throw new IOException("Failed to download " + missing.getFileName() + " from pakku-lock", e);
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Downloaded missing mod from pakku-lock: {0}", missing.getFileName());
            progress.step();
        }
        progress.done();
    }
}
