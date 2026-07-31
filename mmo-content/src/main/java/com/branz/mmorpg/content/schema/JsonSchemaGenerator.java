package com.branz.mmorpg.content.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonSchemaGenerator {
    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, ObjectNode> generate() {
        LinkedHashMap<String, ObjectNode> generated = new LinkedHashMap<>();
        for (DefinitionType type : DefinitionType.values()) {
            generated.put(fileName(type), generate(type, DefinitionSchemas.schema(type)));
        }
        generated.put("content-definition.schema.json", combined(generated));
        return Collections.unmodifiableMap(generated);
    }

    public void write(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        for (Map.Entry<String, ObjectNode> entry : generate().entrySet()) {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outputDirectory.resolve(entry.getKey()).toFile(), entry.getValue());
        }
    }

    private ObjectNode generate(DefinitionType type, DefinitionSchema definitionSchema) {
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", DRAFT_2020_12);
        root.put("$id", fileName(type));
        root.put("title", type.name() + " definition");
        root.put("type", "object");
        root.put("additionalProperties", true);
        for (FieldRule rule : definitionSchema.fieldRules()) {
            apply(root, rule);
        }
        return root;
    }

    private void apply(ObjectNode root, FieldRule rule) {
        ObjectNode current = root;
        for (int index = 0; index < rule.path().size(); index++) {
            String segment = rule.path().get(index);
            if ("*".equals(segment)) {
                String wildcardSchema =
                        "object".equals(current.path("type").asText())
                                ? "additionalProperties"
                                : "items";
                current = objectChild(current, wildcardSchema);
                current.putIfAbsent("type", mapper.getNodeFactory().textNode("object"));
                continue;
            }
            ObjectNode properties = objectChild(current, "properties");
            ObjectNode property = objectChild(properties, segment);
            if (rule.required()) {
                addRequired(current, segment);
            }
            if (index < rule.path().size() - 1) {
                String next = rule.path().get(index + 1);
                property.putIfAbsent(
                        "type",
                        mapper.getNodeFactory().textNode("*".equals(next) ? "array" : "object"));
            }
            current = property;
        }
        current.put("type", rule.type().jsonSchemaType());
        if (!rule.description().isBlank()) {
            current.put("description", rule.description());
        }
        if (!rule.unit().isBlank()) {
            current.put("x-unit", rule.unit());
        }
        if (rule.minimum() != null) {
            current.put("minimum", rule.minimum());
        }
        if (rule.maximum() != null) {
            current.put("maximum", rule.maximum());
        }
        if (rule.minItems() != null) {
            current.put("minItems", rule.minItems());
        }
        if (rule.maxItems() != null) {
            current.put("maxItems", rule.maxItems());
        }
        if (!rule.allowedValues().isEmpty()) {
            ArrayNode values = current.putArray("enum");
            for (String value : rule.allowedValues().stream().sorted().toList()) {
                if (rule.type() == FieldValueType.BOOLEAN) {
                    values.add(Boolean.parseBoolean(value));
                } else {
                    values.add(value);
                }
            }
        }
    }

    private ObjectNode combined(Map<String, ObjectNode> generated) {
        ObjectNode root = mapper.createObjectNode();
        root.put("$schema", DRAFT_2020_12);
        root.put("$id", "content-definition.schema.json");
        root.put("title", "MMO content definition");
        ArrayNode oneOf = root.putArray("oneOf");
        generated.keySet().stream()
                .filter(name -> !"content-definition.schema.json".equals(name))
                .forEach(name -> oneOf.addObject().put("$ref", name));
        return root;
    }

    private static String fileName(DefinitionType type) {
        return type.name().toLowerCase() + ".schema.json";
    }

    private ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ObjectNode object) {
            return object;
        }
        ObjectNode created = mapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private void addRequired(ObjectNode schema, String field) {
        ArrayNode required;
        JsonNode existing = schema.get("required");
        if (existing instanceof ArrayNode array) {
            required = array;
        } else {
            required = schema.putArray("required");
        }
        for (JsonNode value : required) {
            if (field.equals(value.asText())) {
                return;
            }
        }
        required.add(field);
    }
}
