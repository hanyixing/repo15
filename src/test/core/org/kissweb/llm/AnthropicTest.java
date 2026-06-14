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
 * Tests for {@link Anthropic} — constructor, setters, and HTTP methods
 * backed by an in-process mock server. Uses setUrl() to redirect.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnthropicTest {

    private HttpServer mockServer;

    @BeforeAll
    void setupServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Mock /v1/messages — Anthropic uses SSE for send()
        mockServer.createContext("/v1/messages", exchange -> {
            // Anthropic send() uses SSE; simulate a simple SSE response
            String sseBody = "event: message_start\n" +
                    "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg-1\",\"model\":\"claude-3\"}}\n\n" +
                    "event: content_block_delta\n" +
                    "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello from Claude mock!\"}}\n\n" +
                    "event: message_stop\n" +
                    "data: {\"type\":\"message_stop\"}\n\n";
            byte[] bytes = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        int port = mockServer.getAddress().getPort();
        Anthropic.setUrl("http://127.0.0.1:" + port);
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
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        assertNotNull(ai);
    }

    @Test
    void testSetTemperature() {
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        ai.setTemperature(0.5f);
    }

    @Test
    void testSetSampling() {
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        ai.setSampling(0.9f);
    }

    @Test
    void testSetMaxTokens() {
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        ai.setMaxTokens(4096);
    }

    // ------------------------------------------------------------------
    // HTTP methods via mock server
    // ------------------------------------------------------------------

    @Test
    void testSend_mockServer_parsesResponse() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        ai.setMaxTokens(1024);
        String response = ai.send("Hello Claude!");
        assertNotNull(response);
        assertTrue(response.contains("Hello from Claude mock!"),
                "Response should contain mock text, got: " + response);
    }

    @Test
    void testSend_mockServer_recordsLastResponse() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        ai.setMaxTokens(1024);
        ai.send("Hello");
        JSONObject lastResp = ai.getLastFullResponse();
        assertNotNull(lastResp);
    }

    @Test
    void testResponseCode_afterCall() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-3-opus");
        ai.setMaxTokens(1024);
        ai.send("Hello");
        assertEquals(200, ai.getResponseCode());
    }

    @Test
    void testSetVersion() {
        // setVersion is static — just verify no exception
        Anthropic.setVersion("2024-01-01");
    }
}
