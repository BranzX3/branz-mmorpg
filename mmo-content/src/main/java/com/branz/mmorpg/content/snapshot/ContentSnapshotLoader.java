package com.branz.mmorpg.content.snapshot;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.IdentifierErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.definition.DefinitionRegistry;
import com.branz.mmorpg.content.diagnostic.ContentDiagnostic;
import com.branz.mmorpg.content.diagnostic.ContentDiagnosticCode;
import com.branz.mmorpg.content.diagnostic.DiagnosticSeverity;
import com.branz.mmorpg.content.diagnostic.ValidationReport;
import com.branz.mmorpg.content.manifest.ContentManifest;
import com.branz.mmorpg.content.manifest.ContentManifestErrorCode;
import com.branz.mmorpg.content.manifest.ContentManifestParser;
import com.branz.mmorpg.content.reference.ContentReference;
import com.branz.mmorpg.content.reference.ReferenceExtraction;
import com.branz.mmorpg.content.reference.ReferenceExtractor;
import com.branz.mmorpg.content.reference.ReferenceIndex;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.validation.ContentValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class ContentSnapshotLoader {
    private final ContentManifestParser manifestParser;
    private final YAMLMapper yamlMapper;
    private final ReferenceExtractor referenceExtractor;
    private final ContentValidator validator;

    public ContentSnapshotLoader() {
        this(
                new ContentManifestParser(),
                YAMLMapper.builder().build(),
                new ReferenceExtractor(),
                new ContentValidator());
    }

    ContentSnapshotLoader(
            ContentManifestParser manifestParser,
            YAMLMapper yamlMapper,
            ReferenceExtractor referenceExtractor,
            ContentValidator validator) {
        this.manifestParser = Objects.requireNonNull(manifestParser, "manifestParser");
        this.yamlMapper = Objects.requireNonNull(yamlMapper, "yamlMapper");
        this.referenceExtractor = Objects.requireNonNull(referenceExtractor, "referenceExtractor");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public Result<ContentSnapshot, ContentLoadFailure> load(Path root) {
        Objects.requireNonNull(root, "root");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<ContentDiagnostic> diagnostics = new ArrayList<>();
        ContentManifest manifest = parseManifest(normalizedRoot, diagnostics);
        if (manifest == null) {
            return failure(diagnostics);
        }

        List<ContentDefinition> definitions = parseDefinitions(normalizedRoot, diagnostics);
        Map<DefinitionId, ContentDefinition> unique = indexUnique(definitions, diagnostics);
        DefinitionRegistry registry = DefinitionRegistry.of(unique.values());
        List<ContentReference> extracted = new ArrayList<>();
        for (ContentDefinition definition : registry.all()) {
            ReferenceExtraction extraction = referenceExtractor.extract(definition);
            extracted.addAll(extraction.references());
            diagnostics.addAll(extraction.diagnostics());
        }
        ReferenceIndex references = ReferenceIndex.of(extracted);
        ValidationReport report = validator.validate(registry, references);
        diagnostics.addAll(report.diagnostics());
        if (diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)) {
            return failure(diagnostics);
        }
        return Result.success(new ContentSnapshot(manifest, registry, references));
    }

    private ContentManifest parseManifest(Path root, List<ContentDiagnostic> diagnostics) {
        Path manifestPath = root.resolve("content-manifest.json");
        Result<ContentManifest, ContentManifestErrorCode> parsed =
                manifestParser.parse(manifestPath);
        if (parsed instanceof Result.Success<ContentManifest, ContentManifestErrorCode> success) {
            return success.value();
        }
        Result.Failure<ContentManifest, ContentManifestErrorCode> failure =
                (Result.Failure<ContentManifest, ContentManifestErrorCode>) parsed;
        diagnostics.add(
                new ContentDiagnostic(
                        ContentDiagnosticCode.CONTENT_SCHEMA_INVALID_DOCUMENT,
                        DiagnosticSeverity.ERROR,
                        root.relativize(manifestPath),
                        1,
                        1,
                        "",
                        failure.error().code() + ": " + failure.detail(),
                        List.of(),
                        "Create or repair content-manifest.json."));
        return null;
    }

    private List<ContentDefinition> parseDefinitions(
            Path root, List<ContentDiagnostic> diagnostics) {
        List<Path> yamlFiles;
        try (Stream<Path> files = Files.walk(root)) {
            yamlFiles =
                    files.filter(Files::isRegularFile)
                            .filter(ContentSnapshotLoader::isYaml)
                            .sorted(Comparator.naturalOrder())
                            .toList();
        } catch (IOException exception) {
            diagnostics.add(
                    invalidDocument(
                            Path.of("."),
                            "",
                            "Cannot scan content directory: " + exception.getMessage()));
            return List.of();
        }

        List<ContentDefinition> definitions = new ArrayList<>();
        for (Path file : yamlFiles) {
            parseDefinition(root, file, definitions, diagnostics);
        }
        return definitions;
    }

    private void parseDefinition(
            Path root,
            Path file,
            List<ContentDefinition> definitions,
            List<ContentDiagnostic> diagnostics) {
        Path source = root.relativize(file);
        List<String> lines;
        JsonNode document;
        try {
            lines = Files.readAllLines(file);
            document = yamlMapper.readTree(file.toFile());
        } catch (JsonProcessingException exception) {
            diagnostics.add(
                    invalidDocument(
                            source,
                            "",
                            "Invalid YAML: " + safeMessage(exception.getOriginalMessage())));
            return;
        } catch (IOException exception) {
            diagnostics.add(
                    invalidDocument(
                            source,
                            "",
                            "Cannot read YAML: " + safeMessage(exception.getMessage())));
            return;
        }
        if (document == null || !document.isObject() || !document.has("definition_id")) {
            return;
        }

        String rawId = document.path("definition_id").asText("");
        Result<DefinitionId, IdentifierErrorCode> parsedId = DefinitionId.parse(rawId);
        if (!(parsedId instanceof Result.Success<DefinitionId, IdentifierErrorCode> success)) {
            diagnostics.add(
                    new ContentDiagnostic(
                            ContentDiagnosticCode.CONTENT_ID_INVALID_FORMAT,
                            DiagnosticSeverity.ERROR,
                            source,
                            findLine(lines, rawId),
                            1,
                            rawId,
                            "Invalid stable definition ID: " + rawId,
                            List.of(),
                            "Use a lowercase dotted namespace."));
            return;
        }
        DefinitionId id = success.value();
        DefinitionType type = DefinitionType.fromId(id).orElse(null);
        if (type == null) {
            diagnostics.add(
                    new ContentDiagnostic(
                            ContentDiagnosticCode.CONTENT_ID_UNKNOWN_TYPE,
                            DiagnosticSeverity.ERROR,
                            source,
                            findLine(lines, rawId),
                            1,
                            rawId,
                            "No definition type owns this stable-ID namespace.",
                            List.of(),
                            "Use a registered definition namespace."));
            return;
        }
        int schemaVersion = document.path("schema_version").asInt(0);
        if (schemaVersion < 1) {
            diagnostics.add(
                    new ContentDiagnostic(
                            ContentDiagnosticCode.CONTENT_SCHEMA_REQUIRED_FIELD,
                            DiagnosticSeverity.ERROR,
                            source,
                            1,
                            1,
                            rawId,
                            "schema_version must be a positive integer.",
                            List.of(),
                            "Add schema_version: 1."));
            return;
        }
        definitions.add(new ContentDefinition(id, type, schemaVersion, source, document, lines));
    }

    private Map<DefinitionId, ContentDefinition> indexUnique(
            List<ContentDefinition> definitions, List<ContentDiagnostic> diagnostics) {
        LinkedHashMap<DefinitionId, ContentDefinition> unique = new LinkedHashMap<>();
        for (ContentDefinition definition : definitions) {
            ContentDefinition previous = unique.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                diagnostics.add(
                        new ContentDiagnostic(
                                ContentDiagnosticCode.CONTENT_ID_DUPLICATE,
                                DiagnosticSeverity.ERROR,
                                definition.source(),
                                findLine(definition.sourceLines(), definition.id().value()),
                                1,
                                definition.id().value(),
                                "Definition ID is already declared in " + previous.source(),
                                List.of(definition.id().value()),
                                "Keep one definition or assign a new stable ID."));
            }
        }
        return unique;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static int findLine(List<String> lines, String value) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).contains(value)) {
                return index + 1;
            }
        }
        return 1;
    }

    private static String safeMessage(String message) {
        return message == null ? "unknown parse error" : message;
    }

    private static ContentDiagnostic invalidDocument(
            Path source, String definitionId, String explanation) {
        return new ContentDiagnostic(
                ContentDiagnosticCode.CONTENT_SCHEMA_INVALID_DOCUMENT,
                DiagnosticSeverity.ERROR,
                source,
                1,
                1,
                definitionId,
                explanation,
                List.of(),
                "Repair the YAML document.");
    }

    private static Result<ContentSnapshot, ContentLoadFailure> failure(
            List<ContentDiagnostic> diagnostics) {
        ContentLoadFailure failure = new ContentLoadFailure(diagnostics);
        return Result.failure(failure, diagnostics.size() + " content validation error(s)");
    }
}
