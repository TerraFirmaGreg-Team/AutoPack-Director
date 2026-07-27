package team.terrafirmagreg.autopack.core.pakku;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.logging.JavaLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PakkuLockDifferTest {
    @TempDir
    Path root;
    Path configDir;
    Path modsDir;
    JavaLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        configDir = root.resolve("config").resolve("mod-director");
        modsDir = root.resolve("mods");
        Files.createDirectories(configDir);
        Files.createDirectories(modsDir);
        logger = new JavaLogger(Logger.getLogger("PakkuLockDifferTest"));
    }

    @Test
    void detectsMissingConfigForSinglePlatformCurse() throws Exception {
        copyLock("pakku/lock-single-curse.json");

        PakkuDiff diff = PakkuLockDiffer.detect(root, configDir, logger);

        assertTrue(diff.hasConfigDrift());
        assertEquals(1, diff.getConfigUpdates().size());
        PakkuConfigChange change = diff.getConfigUpdates().get(0);
        assertTrue(change.isCreate());
        assertEquals("ftb-library-forge.curse.json", change.getTargetPath().getFileName().toString());
        assertEquals(404465, change.getAddonId());
        assertEquals("8226927", change.getFileId());
        assertEquals("ftb-library-forge-2001.2.13.jar", change.getFileName());
        assertEquals("FTB Library", change.getComment());
    }

    @Test
    void detectsFileIdDrift() throws Exception {
        copyLock("pakku/lock-single-curse.json");
        copyResource("pakku/config-outdated-curse.json", configDir.resolve("ftb-library-forge.curse.json"));

        PakkuDiff diff = PakkuLockDiffer.detect(root, configDir, logger);

        assertTrue(diff.hasConfigDrift());
        assertFalse(diff.getConfigUpdates().get(0).isCreate());
        assertEquals("8226927", diff.getConfigUpdates().get(0).getFileId());
    }

    @Test
    void skipsDualPlatformProjectsForConfigs() throws Exception {
        copyLock("pakku/lock-dual-platform.json");

        PakkuDiff diff = PakkuLockDiffer.detect(root, configDir, logger);
        assertFalse(diff.hasConfigDrift());
    }

    @Test
    void detectsMissingJarInMods() throws Exception {
        copyLock("pakku/lock-missing-jar.json");

        PakkuDiff diff = PakkuLockDiffer.detect(root, configDir, logger);
        assertTrue(diff.hasMissingMods());
        assertEquals("Placebo-1.20.1-8.6.3.jar", diff.getMissingMods().get(0).getFileName());
    }

    @Test
    void applyConfigsMergesExtraKeys() throws Exception {
        Path target = configDir.resolve("ftb-library-forge.curse.json");
        copyResource("pakku/config-with-extra-keys.json", target);

        PakkuDiff diff = PakkuDiff.builder()
            .configUpdates(List.of(
                PakkuConfigChange.builder()
                    .targetPath(target)
                    .platform("curseforge")
                    .addonId(404465)
                    .fileId("8226927")
                    .comment("FTB Library")
                    .create(false)
                    .build()
            ))
            .build();

        PakkuLockSync.applyConfigs(diff, logger);

        ObjectMapper mapper = ConfigurationController.OBJECT_MAPPER;
        JsonNode node = mapper.readTree(target.toFile());
        assertEquals(404465, node.get("addonId").asInt());
        assertEquals(8226927, node.get("fileId").asInt());
        assertEquals("FTB Library", node.get("comment").asText());
        assertEquals("CLIENT", node.get("metadata").get("side").asText());
        assertEquals("mods", node.get("folder").asText());
    }

    @Test
    void applyConfigsWritesFileName() throws Exception {
        Path target = configDir.resolve("ftb-library-forge.curse.json");

        PakkuDiff diff = PakkuDiff.builder()
            .configUpdates(List.of(
                PakkuConfigChange.builder()
                    .targetPath(target)
                    .platform("curseforge")
                    .addonId(404465)
                    .fileId("8226927")
                    .fileName("ftb-library-forge-2001.2.13.jar")
                    .comment("FTB Library")
                    .create(true)
                    .build()
            ))
            .build();

        PakkuLockSync.applyConfigs(diff, logger);

        JsonNode node = ConfigurationController.OBJECT_MAPPER.readTree(target.toFile());
        assertEquals("ftb-library-forge-2001.2.13.jar", node.get("fileName").asText());
    }

    @Test
    void detectsMissingFileNameAsConfigDrift() throws Exception {
        copyLock("pakku/lock-single-curse.json");
        Files.writeString(configDir.resolve("ftb-library-forge.curse.json"),
            "{\n  \"addonId\": 404465,\n  \"fileId\": 8226927,\n  \"comment\": \"FTB Library\"\n}\n");

        PakkuDiff diff = PakkuLockDiffer.detect(root, configDir, logger);

        assertTrue(diff.hasConfigDrift());
        assertEquals("ftb-library-forge-2001.2.13.jar", diff.getConfigUpdates().get(0).getFileName());
    }

    @Test
    void createsModrinthConfig() throws Exception {
        copyLock("pakku/lock-single-modrinth.json");

        PakkuDiff diff = PakkuLockDiffer.detect(root, configDir, logger);
        assertTrue(diff.hasConfigDrift());
        PakkuConfigChange change = diff.getConfigUpdates().get(0);
        assertEquals("cc-tweaked.modrinth.json", change.getTargetPath().getFileName().toString());
        assertEquals("cc-tweaked", change.getAddonId());
        assertEquals("OMIJHNkd", change.getFileId());

        PakkuLockSync.applyConfigs(diff, logger);
        JsonNode node = ConfigurationController.OBJECT_MAPPER.readTree(change.getTargetPath().toFile());
        assertEquals("cc-tweaked", node.get("addonId").asText());
        assertEquals("OMIJHNkd", node.get("fileId").asText());
        assertEquals("cc-tweaked.jar", node.get("fileName").asText());
    }

    private void copyLock(String resource) throws IOException {
        copyResource(resource, root.resolve("pakku-lock.json"));
    }

    private void copyResource(String resource, Path target) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing test resource: " + resource);
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
