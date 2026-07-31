package com.branz.mmorpg.content.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentManifestParserTest {
    private final ContentManifestParser parser = new ContentManifestParser();

    @Test
    void parsesValidManifestIntoImmutableModel() throws URISyntaxException {
        Result<ContentManifest, ContentManifestErrorCode> result =
                parser.parse(fixture("content-manifest.valid.json"));

        assertTrue(result.isSuccess());
        ContentManifest manifest =
                ((Result.Success<ContentManifest, ContentManifestErrorCode>) result).value();
        assertEquals("v1.0.0-content.1", manifest.contentVersion());
        assertEquals(120, manifest.definitions().get("items"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> manifest.definitions().put("spells", 12));
    }

    @Test
    void returnsTypedFailureForInvalidFields() throws URISyntaxException {
        Result<ContentManifest, ContentManifestErrorCode> result =
                parser.parse(fixture("content-manifest.invalid.json"));

        assertFalse(result.isSuccess());
        assertEquals(
                ContentManifestErrorCode.CONTENT_MANIFEST_INVALID_FIELD,
                ((Result.Failure<ContentManifest, ContentManifestErrorCode>) result).error());
    }

    @Test
    void returnsTypedFailureForMissingFile() {
        Result<ContentManifest, ContentManifestErrorCode> result =
                parser.parse(Path.of("does-not-exist.json"));

        assertFalse(result.isSuccess());
        assertEquals(
                ContentManifestErrorCode.CONTENT_MANIFEST_NOT_FOUND,
                ((Result.Failure<ContentManifest, ContentManifestErrorCode>) result).error());
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(ContentManifestParserTest.class.getResource("/fixtures/" + name).toURI());
    }
}
