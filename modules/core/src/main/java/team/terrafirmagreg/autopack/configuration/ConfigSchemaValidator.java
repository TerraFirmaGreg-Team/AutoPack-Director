package team.terrafirmagreg.autopack.configuration;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class ConfigSchemaValidator {
    private static final SchemaRegistry REGISTRY =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final Map<ConfigFileType, Schema> SCHEMAS = new EnumMap<>(ConfigFileType.class);

    private ConfigSchemaValidator() {}

    public static List<String> validateNode(JsonNode node, ConfigFileType type) {
        if (type == null) {
            return Collections.emptyList();
        }
        Schema schema = SCHEMAS.computeIfAbsent(type, ConfigSchemaValidator::loadSchema);
        List<Error> errors = schema.validate(node.toString(), InputFormat.JSON);
        if (errors.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> messages = new ArrayList<>(errors.size());
        for (Error error : errors) {
            messages.add(error.toString());
        }
        return messages;
    }

    private static Schema loadSchema(ConfigFileType type) {
        String resource = type.schemaResource();
        try (InputStream stream = ConfigSchemaValidator.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing schema resource: " + resource);
            }
            return REGISTRY.getSchema(readUtf8(stream), InputFormat.JSON);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schema: " + resource, e);
        }
    }

    private static String readUtf8(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
