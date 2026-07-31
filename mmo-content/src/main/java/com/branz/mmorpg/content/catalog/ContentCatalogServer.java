package com.branz.mmorpg.content.catalog;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.IdentifierErrorCode;
import com.branz.mmorpg.api.result.Result;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loopback-only, read-only HTTP projection of one immutable compiled content snapshot.
 *
 * <p>The server never writes source files and must be restarted to load a different snapshot.
 */
public final class ContentCatalogServer implements AutoCloseable {
    private static final String API_PREFIX = "/api/v1";
    private static final String DEFINITIONS_PATH = API_PREFIX + "/definitions/";

    private final ContentCatalog catalog;
    private final ContentCatalogJson json;
    private final HttpServer server;
    private final ExecutorService executor;

    private ContentCatalogServer(ContentCatalog catalog, HttpServer server) {
        this.catalog = catalog;
        this.json = new ContentCatalogJson();
        this.server = server;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public static ContentCatalogServer start(ContentCatalog catalog, int port) throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        HttpServer httpServer = HttpServer.create(address, 0);
        ContentCatalogServer catalogServer = new ContentCatalogServer(catalog, httpServer);
        httpServer.start();
        return catalogServer;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public URI baseUri() {
        return URI.create("http://localhost:" + port());
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "The catalog API is read-only.");
            return;
        }

        String path = exchange.getRequestURI().getRawPath();
        if ("/".equals(path)) {
            send(exchange, 200, index());
        } else if ((API_PREFIX + "/health").equals(path)) {
            send(exchange, 200, health());
        } else if ((API_PREFIX + "/catalog").equals(path)) {
            send(exchange, 200, json.catalog(catalog));
        } else if ((API_PREFIX + "/search").equals(path)) {
            send(
                    exchange,
                    200,
                    json.search(catalog, queryParameters(exchange).getOrDefault("q", "")));
        } else if (path.startsWith(DEFINITIONS_PATH)) {
            sendDefinitionRoute(exchange, path.substring(DEFINITIONS_PATH.length()));
        } else {
            sendError(exchange, 404, "ROUTE_NOT_FOUND", "No catalog route matches this path.");
        }
    }

    private void sendDefinitionRoute(HttpExchange exchange, String rawRoute) throws IOException {
        boolean references = rawRoute.endsWith("/references");
        String rawId =
                references
                        ? rawRoute.substring(0, rawRoute.length() - "/references".length())
                        : rawRoute;
        String value = decode(rawId);
        Result<DefinitionId, IdentifierErrorCode> parsed = DefinitionId.parse(value);
        if (!(parsed instanceof Result.Success<DefinitionId, IdentifierErrorCode> success)) {
            sendError(exchange, 400, "INVALID_STABLE_ID", "Invalid definition ID: " + value);
            return;
        }

        Optional<ContentCatalogEntry> entry = catalog.find(success.value());
        if (entry.isEmpty()) {
            sendError(
                    exchange,
                    404,
                    "DEFINITION_NOT_FOUND",
                    "Definition is not present in this snapshot: " + value);
            return;
        }
        send(
                exchange,
                200,
                references
                        ? json.references(entry.orElseThrow())
                        : json.definition(entry.orElseThrow()));
    }

    private ObjectNode index() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("service", "mmo-content-catalog");
        root.put("readOnly", true);
        root.put("contentVersion", catalog.contentVersion());
        ArrayNode endpoints = root.putArray("endpoints");
        endpoints.add(API_PREFIX + "/health");
        endpoints.add(API_PREFIX + "/catalog");
        endpoints.add(API_PREFIX + "/search?q={query}");
        endpoints.add(DEFINITIONS_PATH + "{stable-id}");
        endpoints.add(DEFINITIONS_PATH + "{stable-id}/references");
        return root;
    }

    private ObjectNode health() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("status", "UP");
        root.put("readOnly", true);
        root.put("bind", "loopback");
        root.put("snapshotMode", "immutable-startup");
        root.put("contentVersion", catalog.contentVersion());
        root.put("definitions", catalog.entries().size());
        return root;
    }

    private void sendError(HttpExchange exchange, int status, String code, String detail)
            throws IOException {
        ObjectNode error = JsonNodeFactory.instance.objectNode();
        error.put("error", code);
        error.put("detail", detail);
        send(exchange, status, error);
    }

    private void send(HttpExchange exchange, int status, ObjectNode document) throws IOException {
        byte[] body = json.bytes(document);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static Map<String, String> queryParameters(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String parameter : rawQuery.split("&")) {
            String[] parts = parameter.split("=", 2);
            values.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
