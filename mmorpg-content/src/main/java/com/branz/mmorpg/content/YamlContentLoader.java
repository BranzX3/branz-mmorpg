package com.branz.mmorpg.content;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

final class YamlContentLoader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    Map<ContentId, ContentDefinition> load(Path root) throws ContentLoadException {
        List<String> diagnostics = new ArrayList<>();
        Map<ContentId, ContentDefinition> definitions = new LinkedHashMap<>();

        if (!Files.isDirectory(root)) {
            throw new ContentLoadException(List.of("Content directory does not exist: " + root.toAbsolutePath()));
        }

        List<Path> files;
        try (Stream<Path> paths = Files.walk(root)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(this::isYaml)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException exception) {
            throw new ContentLoadException(List.of("Unable to scan content directory: " + exception.getMessage()));
        }

        if (files.isEmpty()) {
            diagnostics.add("No .yml or .yaml definitions found under " + root.toAbsolutePath());
        }

        for (Path file : files) {
            try {
                ContentDefinition definition = parse(file);
                ContentDefinition previous = definitions.putIfAbsent(definition.id(), definition);
                if (previous != null) {
                    diagnostics.add(relative(root, file) + ": duplicate content ID " + definition.id());
                }
            } catch (Exception exception) {
                diagnostics.add(relative(root, file) + ": " + rootCauseMessage(exception));
            }
        }

        if (!diagnostics.isEmpty()) {
            throw new ContentLoadException(diagnostics);
        }
        return definitions;
    }

    private ContentDefinition parse(Path file) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("definition must be a YAML object");
        }
        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException("missing string field 'type'");
        }
        String type = typeNode.textValue().trim().toLowerCase(Locale.ROOT);
        if (!type.equals("material")) {
            throw new IllegalArgumentException("unsupported content type '" + type + "'");
        }
        RawMaterialDefinition raw = mapper.treeToValue(root, RawMaterialDefinition.class);
        return new MaterialDefinition(
                ContentId.parse(raw.id()), raw.displayName(), raw.category(), raw.rarity(),
                raw.tradable(), raw.maxStackSize());
    }

    private boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private String relative(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
