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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Anthropic}, the client for Anthropic's Messages API.
 * <br><br>
 * {@link Anthropic#setUrl(String)} and {@link Anthropic#setVersion(String)}
 * (provided expressly for testing) redirect the client at a local
 * {@link HttpServer} that returns a canned Server-Sent-Events stream. This
 * exercises the streaming parser ({@code content_block_delta} text extraction
 * and the {@code message_stop} terminator) and lets the test capture and assert
 * on both the JSON request body and the authentication headers the client
 * builds.
 * <br><br>
 * A dedicated test confirms the documented guarantee that {@code onDone} is
 * invoked even when the server never sends a {@code message_stop} event.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AnthropicTest {

    private static final String DEFAULT_URL     = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_VERSION = "2023-06-01";

    private static HttpServer server;
    private static String     endpoint;

    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private static final AtomicReference<String> lastApiKey      = new AtomicReference<>();
    private static final AtomicReference<String> lastVersion     = new AtomicReference<>();
    /** When false the canned stream omits the message_stop terminator. */
    private static final AtomicBoolean includeStop = new AtomicBoolean(true);

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages";

        server.createContext("/v1/messages", ex -> {
            lastApiKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
            lastVersion.set(ex.getRequestHeaders().getFirst("anthropic-version"));
            lastRequestBody.set(readBody(ex));

            StringBuilder sse = new StringBuilder()
                    .append("data: {\"type\":\"message_start\"}\n\n")
                    .append("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n")
                    .append("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}\n\n");
            if (includeStop.get())
                sse.append("data: {\"type\":\"message_stop\"}\n\n");
            respondSse(ex, sse.toString());
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        Anthropic.setUrl(DEFAULT_URL);          // don't leak test settings
        Anthropic.setVersion(DEFAULT_VERSION);
        if (server != null)
            server.stop(0);
    }

    @BeforeEach
    void redirectClient() {
        Anthropic.setUrl(endpoint);
        Anthropic.setVersion(DEFAULT_VERSION);
        includeStop.set(true);
        lastRequestBody.set(null);
        lastApiKey.set(null);
        lastVersion.set(null);
    }

    // ------------------------------------------------------------------
    // Streaming response parsing
    // ------------------------------------------------------------------

    @Test
    void send_concatenatesTextDeltas() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-sonnet-4-20250514");
        assertEquals("Hello world", ai.send("What is up?"));
    }

    @Test
    void send_clearsLastFullResponse_becauseStreamingHasNoSingleJson() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-sonnet-4-20250514");
        ai.send("hi");
        assertNull(ai.getLastFullResponse());
    }

    @Test
    void stream_invokesOnTokenForEachDeltaAndOnDoneOnce() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-sonnet-4-20250514");
        List<String> tokens = new ArrayList<>();
        int[] doneCount = {0};
        ai.stream("hi", tokens::add, () -> doneCount[0]++);
        assertEquals(List.of("Hello", " world"), tokens);
        assertEquals(1, doneCount[0], "message_stop must fire onDone exactly once");
    }

    @Test
    void stream_onDoneStillFiresWhenServerOmitsMessageStop() throws Exception {
        includeStop.set(false);
        Anthropic ai = new Anthropic("test-key", "claude-sonnet-4-20250514");
        int[] doneCount = {0};
        List<String> tokens = new ArrayList<>();
        ai.stream("hi", tokens::add, () -> doneCount[0]++);
        // Text still arrives, and onDone must be called exactly once via the fallback.
        assertEquals(List.of("Hello", " world"), tokens);
        assertEquals(1, doneCount[0], "onDone must fire even without a message_stop event");
    }

    // ------------------------------------------------------------------
    // Request body + headers
    // ------------------------------------------------------------------

    @Test
    void requestBody_includesModelMaxTokensSamplingAndStream() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-haiku-4-20250514");
        ai.send("hi");

        JSONObject body = new JSONObject(lastRequestBody.get());
        assertEquals("claude-haiku-4-20250514", body.getString("model"));
        assertEquals(4096, body.getInt("max_tokens"), "default max_tokens");
        assertTrue(body.getBoolean("stream"));
        assertTrue(body.has("temperature"));
        assertTrue(body.has("top_p"));
    }

    @Test
    void requestBody_reflectsMaxTokensSetter() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-sonnet-4-20250514");
        ai.setMaxTokens(8192);
        ai.send("hi");

        JSONObject body = new JSONObject(lastRequestBody.get());
        assertEquals(8192, body.getInt("max_tokens"));
    }

    @Test
    void requestBody_buildsSingleUserMessageWithTextContent() throws Exception {
        Anthropic ai = new Anthropic("test-key", "claude-sonnet-4-20250514");
        ai.send("Explain recursion");

        JSONObject body = new JSONObject(lastRequestBody.get());
        JSONArray messages = body.getJSONArray("messages");
        assertEquals(1, messages.length());
        assertEquals("user", messages.getJSONObject(0).getString("role"));

        JSONArray content = messages.getJSONObject(0).getJSONArray("content");
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("Explain recursion", content.getJSONObject(0).getString("text"));
    }

    @Test
    void request_sendsApiKeyAndVersionHeaders() throws Exception {
        Anthropic.setVersion("2099-01-01");
        Anthropic ai = new Anthropic("secret-key", "claude-sonnet-4-20250514");
        ai.send("hi");

        assertEquals("secret-key", lastApiKey.get());
        assertEquals("2099-01-01", lastVersion.get());
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
