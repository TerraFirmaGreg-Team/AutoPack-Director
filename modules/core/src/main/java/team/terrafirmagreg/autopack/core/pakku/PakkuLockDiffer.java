package team.terrafirmagreg.autopack.core.pakku;

import com.fasterxml.jackson.databind.JsonNode;
import team.terrafirmagreg.autopack.core.configuration.ConfigFileType;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.logging.LoggerDelegate;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PakkuLockDiffer {
    public static final String LOCK_FILE_NAME = "pakku-lock.json";

    private PakkuLockDiffer() {
    }

    public static Path lockPath(Path installationRoot) {
        return installationRoot.resolve(LOCK_FILE_NAME);
    }

    public static PakkuDiff detect(Path installationRoot, Path configurationDirectory, LoggerDelegate logger) {
        Path lockFile = lockPath(installationRoot);
        if (!Files.isRegularFile(lockFile)) {
            return PakkuDiff.builder().build();
        }

        JsonNode root;
        try (InputStream stream = Files.newInputStream(lockFile)) {
            root = ConfigurationController.OBJECT_MAPPER.readTree(stream);
        } catch (IOException e) {
            logger.warn("Failed to read {0}: {1}", LOCK_FILE_NAME, e.getMessage());
            return PakkuDiff.builder().build();
        }

        List<String> mcVersions = stringList(root.get("mc_versions"));
        String loader = firstLoader(root.get("loaders"));
        Path modsDir = installationRoot.resolve("mods");

        List<PakkuConfigChange> configUpdates = new ArrayList<>();
        Map<String, PakkuMissingMod> missingByName = new LinkedHashMap<>();

        JsonNode projects = root.get("projects");
        if (projects == null || !projects.isArray()) {
            return PakkuDiff.builder().build();
        }

        for (JsonNode project : projects) {
            if (!"MOD".equals(text(project.get("type")))) {
                continue;
            }

            JsonNode ids = project.get("id");
            if (ids == null || !ids.isObject()) {
                continue;
            }

            boolean hasCurse = ids.hasNonNull("curseforge");
            boolean hasModrinth = ids.hasNonNull("modrinth");

            if (hasCurse ^ hasModrinth) {
                String platform = hasCurse ? "curseforge" : "modrinth";
                PakkuConfigChange change = configChangeFor(project, platform, configurationDirectory, mcVersions, loader);
                if (change != null) {
                    configUpdates.add(change);
                }
            }

            collectMissingMods(project, mcVersions, loader, modsDir, missingByName, logger);
        }

        return PakkuDiff.builder()
            .configUpdates(configUpdates)
            .missingMods(new ArrayList<>(missingByName.values()))
            .build();
    }

    private static PakkuConfigChange configChangeFor(
        JsonNode project,
        String platform,
        Path configurationDirectory,
        List<String> mcVersions,
        String loader
    ) {
        JsonNode file = selectFile(project.get("files"), platform, mcVersions, loader);
        if (file == null) {
            return null;
        }

        String slug = text(project.path("slug").get(platform));
        String comment = text(project.path("name").get(platform));
        if (slug == null || slug.isEmpty()) {
            return null;
        }

        ConfigFileType configType = ConfigFileType.fromPakkuPlatform(platform);
        if (configType == null) {
            return null;
        }
        Path target = configurationDirectory.resolve(configType.fileName(slug));

        Object addonId;
        String fileId = text(file.get("id"));
        String fileName = text(file.get("file_name"));
        if (fileId == null) {
            return null;
        }

        if ("curseforge".equals(platform)) {
            String projectId = text(project.path("id").get("curseforge"));
            if (projectId == null) {
                return null;
            }
            try {
                addonId = Integer.parseInt(projectId);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            addonId = slug;
        }

        if (!Files.isRegularFile(target)) {
            return PakkuConfigChange.builder()
                .targetPath(target)
                .platform(platform)
                .addonId(addonId)
                .fileId(fileId)
                .fileName(fileName)
                .comment(comment != null ? comment : slug)
                .create(true)
                .build();
        }

        try (InputStream stream = Files.newInputStream(target)) {
            JsonNode existing = ConfigurationController.OBJECT_MAPPER.readTree(stream);
            String existingAddon = existing.has("addonId") ? existing.get("addonId").asText() : null;
            String existingFile = existing.has("fileId") ? existing.get("fileId").asText() : null;
            String existingFileName = existing.has("fileName") ? existing.get("fileName").asText() : null;
            boolean idsMatch = String.valueOf(addonId).equals(existingAddon) && fileId.equals(existingFile);
            boolean fileNameMatch = fileName == null
                ? existingFileName == null || existingFileName.isEmpty()
                : fileName.equals(existingFileName);
            if (idsMatch && fileNameMatch) {
                return null;
            }
        } catch (IOException e) {
            // Treat unreadable config as needing update
        }

        return PakkuConfigChange.builder()
            .targetPath(target)
            .platform(platform)
            .addonId(addonId)
            .fileId(fileId)
            .fileName(fileName)
            .comment(comment != null ? comment : slug)
            .create(false)
            .build();
    }

    private static void collectMissingMods(
        JsonNode project,
        List<String> mcVersions,
        String loader,
        Path modsDir,
        Map<String, PakkuMissingMod> missingByName,
        LoggerDelegate logger
    ) {
        JsonNode files = project.get("files");
        if (files == null || !files.isArray()) {
            return;
        }

        for (JsonNode file : files) {
            if (!matches(file, mcVersions, loader)) {
                continue;
            }
            String fileName = text(file.get("file_name"));
            String urlText = text(file.get("url"));
            String platform = text(file.get("type"));
            if (fileName == null || urlText == null || platform == null) {
                continue;
            }
            if (Files.isRegularFile(modsDir.resolve(fileName))) {
                continue;
            }
            if (missingByName.containsKey(fileName)) {
                continue;
            }
            try {
                missingByName.put(fileName, PakkuMissingMod.builder()
                    .fileName(fileName)
                    .downloadUrl(new URL(urlText))
                    .platform(platform)
                    .build());
            } catch (MalformedURLException e) {
                logger.warn("Invalid download URL in pakku-lock for {0}: {1}", fileName, urlText);
            }
        }
    }

    static JsonNode selectFile(JsonNode files, String platform, List<String> mcVersions, String loader) {
        if (files == null || !files.isArray()) {
            return null;
        }
        JsonNode fallback = null;
        for (JsonNode file : files) {
            if (!platform.equals(text(file.get("type")))) {
                continue;
            }
            if (matches(file, mcVersions, loader)) {
                return file;
            }
            if (fallback == null) {
                fallback = file;
            }
        }
        return fallback;
    }

    static boolean matches(JsonNode file, List<String> mcVersions, String loader) {
        List<String> fileMc = stringList(file.get("mc_versions"));
        List<String> fileLoaders = stringList(file.get("loaders"));
        boolean mcOk = mcVersions.isEmpty() || fileMc.isEmpty() || intersects(mcVersions, fileMc);
        boolean loaderOk = loader == null || loader.isEmpty() || fileLoaders.isEmpty() || fileLoaders.contains(loader);
        return mcOk && loaderOk;
    }

    private static boolean intersects(List<String> a, List<String> b) {
        for (String value : a) {
            if (b.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static String firstLoader(JsonNode loaders) {
        if (loaders == null || !loaders.isObject()) {
            return null;
        }
        Iterator<String> names = loaders.fieldNames();
        return names.hasNext() ? names.next() : null;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isEmpty() ? null : value;
    }
}
