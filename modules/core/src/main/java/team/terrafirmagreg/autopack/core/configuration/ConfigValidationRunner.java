package team.terrafirmagreg.autopack.core.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import team.terrafirmagreg.autopack.core.util.JacksonProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Offline config validation for CI: schema + Jackson parse, no downloads or modify side-effects.
 */
public final class ConfigValidationRunner {
    private ConfigValidationRunner() {
    }

    public static int validate(Path configurationDirectory) {
        List<String> errors = new ArrayList<>();

        if (!Files.isDirectory(configurationDirectory)) {
            System.err.println("Configuration directory not found: " + configurationDirectory);
            return 1;
        }

        Path modpackPath = configurationDirectory.resolve(ConfigFileType.MODPACK.getSuffix());
        if (Files.exists(modpackPath)) {
            validateFile(modpackPath, ConfigFileType.MODPACK, errors);
        }

        try (Stream<Path> paths = Files.walk(configurationDirectory)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .filter(p -> !ConfigFileType.isModpackFileName(p.getFileName().toString()))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(path -> validateConfigFile(path, errors));
        } catch (IOException e) {
            errors.add("Failed to iterate configuration directory: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            System.out.println("All configurations are valid.");
            return 0;
        }

        System.err.println("Configuration validation failed with " + errors.size() + " error(s):");
        for (String error : errors) {
            System.err.println("  - " + error);
        }
        return 1;
    }

    private static void validateConfigFile(Path path, List<String> errors) {
        ConfigFileType type = ConfigFileType.fromPath(path);
        if (type == null || type == ConfigFileType.MODPACK) {
            System.out.println("Ignoring unknown json file: " + path.getFileName());
            return;
        }
        if (type == ConfigFileType.BUNDLE) {
            validateBundle(path, errors);
            return;
        }
        validateFile(path, type, errors);
    }

    private static void validateBundle(Path path, List<String> errors) {
        try {
            JsonNode tree = readTree(path);
            List<String> schemaErrors = ConfigSchemaValidator.validateNode(tree, ConfigFileType.BUNDLE);
            if (!schemaErrors.isEmpty()) {
                errors.add(path.getFileName() + ": " + String.join("; ", schemaErrors));
                return;
            }
            for (ConfigFileType entryType : ConfigFileType.bundleEntryTypes()) {
                validateBundleArray(path, tree.get(entryType.getSchemaType()), entryType, errors);
            }
        } catch (IOException e) {
            errors.add(path.getFileName() + ": " + e.getMessage());
        }
    }

    private static void validateBundleArray(
        Path path,
        JsonNode array,
        ConfigFileType type,
        List<String> errors
    ) {
        if (array == null || !array.isArray()) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            String context = path.getFileName() + " → " + type.getSchemaType() + "[" + i + "]";
            try {
                JsonNode entry = ConfigurationController.stripSchema(array.get(i).deepCopy());
                List<String> schemaErrors = ConfigSchemaValidator.validateNode(entry, type);
                if (!schemaErrors.isEmpty()) {
                    errors.add(context + ": " + String.join("; ", schemaErrors));
                    continue;
                }
                JacksonProvider.getObjectMapper().treeToValue(entry, type.getModelClass());
            } catch (IOException e) {
                errors.add(context + ": " + e.getMessage());
            }
        }
    }

    private static void validateFile(Path path, ConfigFileType type, List<String> errors) {
        try {
            JsonNode tree = readTree(path);
            List<String> schemaErrors = ConfigSchemaValidator.validateNode(tree, type);
            if (!schemaErrors.isEmpty()) {
                errors.add(path.getFileName() + ": " + String.join("; ", schemaErrors));
                return;
            }
            JacksonProvider.getObjectMapper().treeToValue(tree, type.getModelClass());
        } catch (IOException e) {
            errors.add(path.getFileName() + ": " + e.getMessage());
        }
    }

    private static JsonNode readTree(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            return ConfigurationController.stripSchema(
                JacksonProvider.getObjectMapper().readTree(stream));
        }
    }
}
