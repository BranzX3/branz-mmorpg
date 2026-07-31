package com.branz.mmorpg.content.manifest;

import com.branz.mmorpg.api.result.Result;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ContentManifestParser {
    private final JsonMapper mapper;

    public ContentManifestParser() {
        this(JsonMapper.builder().build());
    }

    ContentManifestParser(JsonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public Result<ContentManifest, ContentManifestErrorCode> parse(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            return Result.failure(
                    ContentManifestErrorCode.CONTENT_MANIFEST_NOT_FOUND,
                    "Manifest file does not exist: " + path);
        }
        try {
            return parse(Files.readAllBytes(path));
        } catch (IOException exception) {
            return Result.failure(
                    ContentManifestErrorCode.CONTENT_MANIFEST_IO,
                    "Cannot read manifest: " + exception.getMessage());
        }
    }

    public Result<ContentManifest, ContentManifestErrorCode> parse(byte[] json) {
        Objects.requireNonNull(json, "json");
        try {
            return Result.success(mapper.readValue(json, ContentManifest.class));
        } catch (JsonParseException exception) {
            return Result.failure(
                    ContentManifestErrorCode.CONTENT_MANIFEST_INVALID_JSON,
                    locationMessage(exception));
        } catch (JsonMappingException exception) {
            return Result.failure(
                    ContentManifestErrorCode.CONTENT_MANIFEST_INVALID_FIELD,
                    locationMessage(exception));
        } catch (IOException exception) {
            return Result.failure(
                    ContentManifestErrorCode.CONTENT_MANIFEST_IO, exception.getMessage());
        }
    }

    private static String locationMessage(IOException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
