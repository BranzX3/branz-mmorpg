package com.branz.mmorpg.content.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.content.snapshot.ContentLoadFailure;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.content.snapshot.ContentSnapshotLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentCatalogServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void servesSnapshotSearchDefinitionsAndReferencesWhileRejectingWrites() throws Exception {
        ContentCatalog catalog = ContentCatalog.from(loadFixture());

        try (ContentCatalogServer server = ContentCatalogServer.start(catalog, 0)) {
            HttpResponse<String> health = get(server.baseUri().resolve("/api/v1/health"));
            HttpResponse<String> search = get(server.baseUri().resolve("/api/v1/search?q=iron"));
            HttpResponse<String> definition =
                    get(
                            server.baseUri()
                                    .resolve(
                                            "/api/v1/definitions/" + "node.frostpeak.iron_common"));
            HttpResponse<String> references =
                    get(
                            server.baseUri()
                                    .resolve(
                                            "/api/v1/definitions/"
                                                    + "material.iron_ore/references"));
            HttpResponse<String> mutation =
                    client.send(
                            HttpRequest.newBuilder(server.baseUri().resolve("/api/v1/catalog"))
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());

            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"snapshotMode\" : \"immutable-startup\""));
            assertTrue(health.body().contains("\"definitions\" : 2"));
            assertEquals(200, search.statusCode());
            assertTrue(search.body().contains("\"count\" : 2"));
            assertEquals(200, definition.statusCode());
            assertTrue(definition.body().contains("\"type\" : \"LIFESKILL_NODE\""));
            assertEquals(200, references.statusCode());
            assertTrue(references.body().contains("node.frostpeak.iron_common"));
            assertEquals(405, mutation.statusCode());
            assertEquals("GET", mutation.headers().firstValue("Allow").orElseThrow());
            assertTrue(mutation.body().contains("METHOD_NOT_ALLOWED"));
        }
    }

    @Test
    void returnsStableClientErrorsForUnknownRoutesAndDefinitionIds() throws Exception {
        ContentCatalog catalog = ContentCatalog.from(loadFixture());

        try (ContentCatalogServer server = ContentCatalogServer.start(catalog, 0)) {
            HttpResponse<String> invalid =
                    get(server.baseUri().resolve("/api/v1/definitions/NOT_VALID"));
            HttpResponse<String> missing =
                    get(server.baseUri().resolve("/api/v1/definitions/item.missing"));
            HttpResponse<String> route = get(server.baseUri().resolve("/api/v1/missing"));

            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.body().contains("INVALID_STABLE_ID"));
            assertEquals(404, missing.statusCode());
            assertTrue(missing.body().contains("DEFINITION_NOT_FOUND"));
            assertEquals(404, route.statusCode());
            assertTrue(route.body().contains("ROUTE_NOT_FOUND"));
        }
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private ContentSnapshot loadFixture() throws Exception {
        Path fixture = Path.of(getClass().getResource("/fixtures/catalog-valid").toURI());
        Result<ContentSnapshot, ContentLoadFailure> loaded =
                new ContentSnapshotLoader().load(fixture);
        assertTrue(loaded.isSuccess());
        return ((Result.Success<ContentSnapshot, ContentLoadFailure>) loaded).value();
    }
}
