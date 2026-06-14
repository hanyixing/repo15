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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Ollama} — pure function toHtml() and HTTP methods
 * backed by an in-process mock server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OllamaTest {

    private HttpServer mockServer;
    private String baseUrl;

    @BeforeAll
    void setupServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Mock /api/version
        mockServer.createContext("/api/version", exchange -> {
            JSONObject resp = new JSONObject();
            resp.put("version", "0.1.0");
            respondJson(exchange, 200, resp.toString());
        });

        // Mock /api/tags
        mockServer.createContext("/api/tags", exchange -> {
            JSONObject resp = new JSONObject();
            JSONArray models = new JSONArray();
            JSONObject m1 = new JSONObject();
            m1.put("model", "llama3:latest");
            JSONObject m2 = new JSONObject();
            m2.put("model", "codellama:13b");
            models.put(m1);
            models.put(m2);
            resp.put("models", models);
            respondJson(exchange, 200, resp.toString());
        });

        // Mock /api/generate
        mockServer.createContext("/api/generate", exchange -> {
            JSONObject resp = new JSONObject();
            resp.put("response", "Hello from Ollama!");
            resp.put("done", true);
            respondJson(exchange, 200, resp.toString());
        });

        // Mock /api/embed
        mockServer.createContext("/api/embed", exchange -> {
            JSONObject resp = new JSONObject();
            JSONArray embeddings = new JSONArray();
            JSONArray vec = new JSONArray();
            vec.put(0.1);
            vec.put(0.2);
            vec.put(0.3);
            embeddings.put(vec);
            resp.put("embeddings", embeddings);
            respondJson(exchange, 200, resp.toString());
        });

        int port = mockServer.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        mockServer.start();
    }

    @AfterAll
    void teardownServer() {
        if (mockServer != null)
            mockServer.stop(0);
    }

    // ------------------------------------------------------------------
    // toHtml — pure function
    // ------------------------------------------------------------------

    @Test
    void testToHtml_convertsNewlines() {
        String result = Ollama.toHtml("line1\nline2\nline3");
        assertTrue(result.contains("<br>"), "Newlines should become <br>");
        assertFalse(result.contains("\n"), "No raw newlines should remain");
    }

    @Test
    void testToHtml_nullInput() {
        String result = Ollama.toHtml(null);
        assertEquals("", result);
    }

    @Test
    void testToHtml_emptyInput() {
        String result = Ollama.toHtml("");
        assertEquals("", result);
    }

    @Test
    void testToHtml_removesThinkTags() {
        String input = "<think>some thinking</think>Visible response";
        String result = Ollama.toHtml(input);
        assertFalse(result.contains("<think>"), "think tags should be removed");
        assertFalse(result.contains("</think>"), "think tags should be removed");
        assertTrue(result.contains("Visible response"));
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------

    @Test
    void testConstructor_default() {
        Ollama o = new Ollama();
        assertNotNull(o);
    }

    @Test
    void testConstructor_urlArg() {
        Ollama o = new Ollama(baseUrl);
        assertNotNull(o);
    }

    @Test
    void testConstructor_urlAndModel() {
        Ollama o = new Ollama(baseUrl, "llama3:latest");
        assertNotNull(o);
    }

    // ------------------------------------------------------------------
    // HTTP methods via mock server
    // ------------------------------------------------------------------

    @Test
    void testIsOllamaUp_serverRunning() {
        Ollama o = new Ollama(baseUrl);
        assertTrue(o.isOllamaUp());
    }

    @Test
    void testIsOllamaUp_serverDown() {
        Ollama o = new Ollama("http://127.0.0.1:1");  // nothing listening
        assertFalse(o.isOllamaUp());
    }

    @Test
    void testGetAvailableModels_parsesResponse() throws Exception {
        Ollama o = new Ollama(baseUrl);
        List<String> models = o.getAvailableModels();
        assertNotNull(models);
        assertEquals(2, models.size());
        assertTrue(models.contains("llama3:latest"));
        assertTrue(models.contains("codellama:13b"));
    }

    @Test
    void testSend_parsesResponse() throws Exception {
        Ollama o = new Ollama(baseUrl, "llama3:latest");
        String response = o.send("Hello");
        assertNotNull(response);
        assertEquals("Hello from Ollama!", response);
    }

    @Test
    void testGetEmbeddings_parsesResponse() throws Exception {
        Ollama o = new Ollama(baseUrl, "llama3:latest");
        double[] embeddings = o.getEmbeddings("test text");
        assertNotNull(embeddings);
        assertEquals(3, embeddings.length);
        assertEquals(0.1, embeddings[0], 0.001);
        assertEquals(0.2, embeddings[1], 0.001);
        assertEquals(0.3, embeddings[2], 0.001);
    }

    @Test
    void testSelectModel() {
        Ollama o = new Ollama(baseUrl);
        o.selectModel("codellama:13b");
        // No exception means success — model is stored internally
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
