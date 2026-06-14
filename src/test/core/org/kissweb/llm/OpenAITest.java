package org.kissweb.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kissweb.json.JSONArray;
import org.kissweb.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OpenAI}, the client for OpenAI's Chat Completions API.
 * <br><br>
 * {@link OpenAI#setUrl(String)} (provided expressly for testing) redirects the
 * client at a local {@link HttpServer} that returns a canned Server-Sent-Events
 * stream. This exercises the streaming parser ({@code data:} framing, the
 * {@code [DONE]} sentinel, and {@code choices[0].delta.content} extraction) and
 * lets the test capture and assert on the JSON request body the client builds.
 * <br><br>
 * The embeddings endpoint is hard-coded to the public OpenAI URL and is
 * therefore intentionally not covered here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAITest {

    private static final String DEFAULT_URL = "https://api.openai.com/v1/chat/completions";

    private static HttpServer server;
    private static String     endpoint;

    /** Captures the most recent request body the client sent. */
    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";

        server.createContext("/v1/chat/completions", ex -> {
            lastRequestBody.set(readBody(ex));
            // A minimal OpenAI-style SSE stream: two content deltas then [DONE].
            String sse =
                    "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n" +
                    "data: [DONE]\n\n";
            respondSse(ex, sse);
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        OpenAI.setUrl(DEFAULT_URL); // don't leak the test endpoint into other classes
        if (server != null)
            server.stop(0);
    }

    @BeforeEach
    void redirectClient() {
        OpenAI.setUrl(endpoint);
        lastRequestBody.set(null);
    }

    // ------------------------------------------------------------------
    // Streaming response parsing
    // ------------------------------------------------------------------

    @Test
    void send_concatenatesContentDeltas() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4o", false);
        assertEquals("Hello world", ai.send("What is up?"));
    }

    @Test
    void send_clearsLastFullResponse_becauseStreamingHasNoSingleJson() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4o", false);
        ai.send("hi");
        assertNull(ai.getLastFullResponse());
    }

    @Test
    void stream_invokesOnTokenForEachDeltaAndOnDoneOnce() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4o", false);
        List<String> tokens = new ArrayList<>();
        int[] doneCount = {0};
        ai.stream("hi", tokens::add, () -> doneCount[0]++);
        assertEquals(List.of("Hello", " world"), tokens);
        assertEquals(1, doneCount[0], "[DONE] must fire onDone exactly once");
    }

    // ------------------------------------------------------------------
    // Request body construction
    // ------------------------------------------------------------------

    @Test
    void requestBody_nonReasoning_includesModelStreamTemperatureAndTopP() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4o-mini", false);
        ai.send("hi");

        JSONObject body = new JSONObject(lastRequestBody.get());
        assertEquals("gpt-4o-mini", body.getString("model"));
        assertTrue(body.getBoolean("stream"));
        assertTrue(body.has("temperature"), "standard models send temperature");
        assertTrue(body.has("top_p"), "standard models send top_p");
        assertFalse(body.has("reasoning_effort"), "standard models must not send reasoning_effort");
    }

    @Test
    void requestBody_reasoningModel_includesReasoningEffort_notSamplingParams() throws Exception {
        OpenAI ai = new OpenAI("test-key", "o1-preview", true);
        ai.setReasoningEffort("high");
        ai.send("hi");

        JSONObject body = new JSONObject(lastRequestBody.get());
        assertEquals("high", body.getString("reasoning_effort"));
        assertFalse(body.has("temperature"), "reasoning models must not send temperature");
        assertFalse(body.has("top_p"), "reasoning models must not send top_p");
    }

    @Test
    void requestBody_reflectsTemperatureAndSamplingSetters() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4o", false);
        ai.setTemperature(0.9f);
        ai.setSampling(0.95f);
        ai.send("hi");

        JSONObject body = new JSONObject(lastRequestBody.get());
        assertEquals(0.9, body.getDouble("temperature"), 1e-3);
        assertEquals(0.95, body.getDouble("top_p"), 1e-3);
    }

    @Test
    void requestBody_buildsSystemAndUserMessages() throws Exception {
        OpenAI ai = new OpenAI("test-key", "gpt-4o", false);
        ai.send("Explain recursion");

        JSONObject body = new JSONObject(lastRequestBody.get());
        JSONArray messages = body.getJSONArray("messages");
        assertEquals(2, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));

        JSONArray content = messages.getJSONObject(1).getJSONArray("content");
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("Explain recursion", content.getJSONObject(0).getString("text"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respondSse(HttpExchange exchange, String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
