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
 * Tests for {@link OpenAI} — constructor, setters, and HTTP methods
 * backed by an in-process mock server. Uses setUrl() to redirect.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAITest {

    private HttpServer mockServer;
    private String originalUrl;

    @BeforeAll
    void setupServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Mock /v1/chat/completions
        mockServer.createContext("/v1/chat/completions", exchange -> {
            JSONObject resp = new JSONObject();
            JSONArray choices = new JSONArray();
            JSONObject choice = new JSONObject();
            JSONObject message = new JSONObject();
            message.put("content", "Hello from OpenAI mock!");
            choice.put("message", message);
            choice.put("finish_reason", "stop");
            choices.put(choice);
            resp.put("choices", choices);
            resp.put("model", "gpt-4");
            respondJson(exchange, 200, resp.toString());
        });

        // Mock /v1/embeddings
        mockServer.createContext("/v1/embeddings", exchange -> {
            JSONObject resp = new JSONObject();
            JSONArray data = new JSONArray();
            JSONObject item = new JSONObject();
            JSONArray embedding = new JSONArray();
            embedding.put(0.11);
            embedding.put(0.22);
            embedding.put(0.33);
            item.put("embedding", embedding);
            data.put(item);
            resp.put("data", data);
            respondJson(exchange, 200, resp.toString());
        });

        int port = mockServer.getAddress().getPort();
        OpenAI.setUrl("http://127.0.0.1:" + port);
        mockServer.start();
    }

    @AfterAll
    void teardownServer() {
        if (mockServer != null)
            mockServer.stop(0);
    }

    // ------------------------------------------------------------------
    // Constructor and setters
    // ------------------------------------------------------------------

    @Test
    void testConstructor_setsFields() {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        assertNotNull(ai);
    }

    @Test
    void testSetTemperature() {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        ai.setTemperature(0.7f);
        // No exception means success
    }

    @Test
    void testSetSampling() {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        ai.setSampling(0.9f);
    }

    @Test
    void testSetReasoningEffort() {
        OpenAI ai = new OpenAI("test-key", "o1-preview", true);
        ai.setReasoningEffort("high");
    }

    @Test
    void testSetImageDetail() {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        ai.setImageDetail("high");
    }

    // ------------------------------------------------------------------
    // HTTP methods via mock server
    // ------------------------------------------------------------------

    @Test
    void testSend_mockServer_parsesResponse() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        String response = ai.send("What is 2+2?");
        assertNotNull(response);
        assertEquals("Hello from OpenAI mock!", response);
    }

    @Test
    void testSend_mockServer_recordsLastResponse() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        ai.send("Hello");
        JSONObject lastResp = ai.getLastFullResponse();
        assertNotNull(lastResp);
    }

    @Test
    void testGetEmbeddings_mockServer() throws Exception {
        OpenAI ai = new OpenAI("test-key", "text-embedding-3-small", false);
        double[] embeddings = ai.getEmbeddings("test text");
        assertNotNull(embeddings);
        assertEquals(3, embeddings.length);
        assertEquals(0.11, embeddings[0], 0.001);
        assertEquals(0.22, embeddings[1], 0.001);
        assertEquals(0.33, embeddings[2], 0.001);
    }

    @Test
    void testResponseCode_afterCall() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4", false);
        ai.send("Hello");
        assertEquals(200, ai.getResponseCode());
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
