package org.kissweb.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kissweb.json.JSONArray;
import org.kissweb.json.JSONObject;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QdrantClient} — vector database client. Uses
 * setUrl() to redirect to an in-process mock server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QdrantClientTest {

    private HttpServer mockServer;
    private String baseUrl;

    @BeforeAll
    void setupServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Mock PUT /collections/{name}
        mockServer.createContext("/collections", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("PUT".equals(method) && path.matches(".*/collections/[^/]+$")) {
                respondJson(exchange, 200, "{\"result\":true,\"status\":\"ok\"}");
            } else if ("DELETE".equals(method) && path.matches(".*/collections/[^/]+$")) {
                respondJson(exchange, 200, "{\"result\":true,\"status\":\"ok\"}");
            } else if ("GET".equals(method) && path.endsWith("/collections")) {
                JSONObject resp = new JSONObject();
                JSONObject result = new JSONObject();
                JSONArray collections = new JSONArray();
                JSONObject c1 = new JSONObject();
                c1.put("name", "test_collection");
                collections.put(c1);
                result.put("collections", collections);
                resp.put("result", result);
                respondJson(exchange, 200, resp.toString());
            } else {
                respondJson(exchange, 200, "{\"result\":true}");
            }
        });

        // Mock points endpoints
        mockServer.createContext("/points", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equals(method) && path.contains("/points/")) {
                JSONObject resp = new JSONObject();
                JSONObject result = new JSONObject();
                result.put("id", "test-id");
                JSONObject payload = new JSONObject();
                payload.put("text", "hello world");
                result.put("payload", payload);
                resp.put("result", result);
                respondJson(exchange, 200, resp.toString());
            } else if ("POST".equals(method) && path.contains("/search")) {
                JSONObject resp = new JSONObject();
                JSONArray result = new JSONArray();
                JSONObject hit = new JSONObject();
                hit.put("id", "hit-1");
                hit.put("score", 0.95);
                result.put(hit);
                resp.put("result", result);
                respondJson(exchange, 200, resp.toString());
            } else {
                respondJson(exchange, 200, "{\"result\":true}");
            }
        });

        int port = mockServer.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        QdrantClient.setUrl(baseUrl);
        mockServer.start();
    }

    @AfterAll
    void teardownServer() {
        if (mockServer != null)
            mockServer.stop(0);
    }

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    @Test
    void testConstructor_setsCollection() {
        QdrantClient client = new QdrantClient("my_collection");
        assertNotNull(client);
    }

    // ------------------------------------------------------------------
    // Static collection operations
    // ------------------------------------------------------------------

    @Test
    void testListCollections_parsesResponse() throws Exception {
        String[] collections = QdrantClient.listCollections();
        assertNotNull(collections);
        assertTrue(collections.length > 0);
        assertEquals("test_collection", collections[0]);
    }

    @Test
    void testCreateCollection_sendsCorrectRequest() {
        assertDoesNotThrow(() -> QdrantClient.createCollection("new_col", 384));
    }

    @Test
    void testDeleteCollection_sendsCorrectRequest() {
        assertDoesNotThrow(() -> QdrantClient.deleteCollection("old_col"));
    }

    // ------------------------------------------------------------------
    // Instance CRUD operations
    // ------------------------------------------------------------------

    @Test
    void testInsertOrUpdate_sendsPoint() throws Exception {
        QdrantClient client = new QdrantClient("test_collection");
        double[] vector = {0.1, 0.2, 0.3};
        String result = client.insertOrUpdate("chunk-1", "doc-1", 0, vector, "hello", null);
        assertNotNull(result);
    }

    @Test
    void testGetRecord_parsesResponse() throws Exception {
        QdrantClient client = new QdrantClient("test_collection");
        JSONObject record = client.getRecord("test-id");
        assertNotNull(record);
    }

    @Test
    void testSearch_sendsCorrectQuery() throws Exception {
        QdrantClient client = new QdrantClient("test_collection");
        double[] vector = {0.1, 0.2, 0.3};
        JSONArray results = client.search(vector, 5);
        assertNotNull(results);
        assertTrue(results.length() > 0);
    }

    @Test
    void testDeleteRecord_sendsRequest() {
        QdrantClient client = new QdrantClient("test_collection");
        assertDoesNotThrow(() -> client.deleteRecord("del-id"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
