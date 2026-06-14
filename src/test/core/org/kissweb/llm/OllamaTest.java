package org.kissweb.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Ollama}, the client for a local Ollama LLM server.
 * <br><br>
 * A lightweight {@link HttpServer} stands in for a real Ollama instance so the
 * full request/response round trip (version probe, model listing, text
 * generation, and embeddings) can be exercised without network access. The
 * pure helper {@link Ollama#toHtml(String)} and the input-validation guards are
 * tested directly.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OllamaTest {

    private static HttpServer server;
    private static String     baseUrl;   // e.g. http://127.0.0.1:PORT

    /** Switchable body for the /api/version endpoint so "server down" can be simulated. */
    private static final AtomicReference<Integer> versionStatus = new AtomicReference<>(200);

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/api/version", ex -> {
            int status = versionStatus.get();
            respondJson(ex, status, "{\"version\":\"0.0.0-test\"}");
        });
        server.createContext("/api/tags", ex ->
                respondJson(ex, 200,
                        "{\"models\":[{\"model\":\"llama3:latest\"},{\"model\":\"mistral:latest\"}]}"));
        // Ollama streams generate responses as concatenated JSON objects (NDJSON
        // without separators). Ollama.send must stitch "}{" into "},{".
        server.createContext("/api/generate", ex ->
                respondJson(ex, 200,
                        "{\"response\":\"Hello\",\"done\":false}{\"response\":\" world\",\"done\":true}"));
        server.createContext("/api/embed", ex ->
                respondJson(ex, 200,
                        "{\"embeddings\":[[0.1,0.2,0.3,0.4]]}"));
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null)
            server.stop(0);
    }

    @BeforeEach
    void resetState() {
        versionStatus.set(200);
    }

    // ------------------------------------------------------------------
    // toHtml: pure string transformation
    // ------------------------------------------------------------------

    @Test
    void toHtml_nullReturnsEmptyString() {
        assertEquals("", Ollama.toHtml(null));
    }

    @Test
    void toHtml_stripsBoxedWrapper() {
        assertEquals("42", Ollama.toHtml("\\boxed{42}"));
    }

    @Test
    void toHtml_convertsNewlinesToBreaks() {
        assertEquals("line1<br>line2", Ollama.toHtml("line1\nline2"));
    }

    @Test
    void toHtml_convertsDoubleAsteriskToBold() {
        assertEquals("a <b>bold</b> word", Ollama.toHtml("a **bold** word"));
    }

    @Test
    void toHtml_removesStrayBackslashes() {
        assertEquals("frac12", Ollama.toHtml("\\frac\\12"));
    }

    // ------------------------------------------------------------------
    // Constructors and diagnostics defaults
    // ------------------------------------------------------------------

    @Test
    void newClient_hasZeroResponseCodeAndNullResponseBeforeAnyCall() {
        Ollama ai = new Ollama();
        assertEquals(0, ai.getResponseCode());
        assertNull(ai.getResponseString());
    }

    @Test
    void urlOnlyConstructor_leavesModelUnset_soSendReports() {
        // A "http..." argument is treated as a server URL, not a model name,
        // so the model remains unset.
        Ollama ai = new Ollama(baseUrl);
        Exception ex = assertThrows(Exception.class, () -> ai.send("hi"));
        assertEquals("Model not set", ex.getMessage());
    }

    @Test
    void getEmbeddings_withoutModel_reports() {
        Ollama ai = new Ollama(baseUrl);
        Exception ex = assertThrows(Exception.class, () -> ai.getEmbeddings("some text"));
        assertEquals("Model not set", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // Server round trips
    // ------------------------------------------------------------------

    @Test
    void isOllamaUp_trueWhenVersionEndpointReturns200() {
        Ollama ai = new Ollama(baseUrl);
        assertTrue(ai.isOllamaUp());
        assertEquals(200, ai.getResponseCode());
    }

    @Test
    void isOllamaUp_falseWhenServerUnreachable() {
        // Port 1 is effectively never open: connection is refused.
        Ollama ai = new Ollama("http://127.0.0.1:1");
        assertFalse(ai.isOllamaUp());
    }

    @Test
    void isOllamaUp_falseWhenVersionEndpointErrors() {
        versionStatus.set(500);
        Ollama ai = new Ollama(baseUrl);
        assertFalse(ai.isOllamaUp());
    }

    @Test
    void getAvailableModels_parsesModelNames() throws IOException {
        Ollama ai = new Ollama(baseUrl);
        List<String> models = ai.getAvailableModels();
        assertEquals(2, models.size());
        assertTrue(models.contains("llama3:latest"));
        assertTrue(models.contains("mistral:latest"));
    }

    @Test
    void send_stitchesConcatenatedJsonAndConcatenatesResponses() throws Exception {
        Ollama ai = new Ollama(baseUrl, "llama3");
        String result = ai.send("anything");
        assertEquals("Hello world", result);
        assertEquals(200, ai.getResponseCode());
    }

    @Test
    void send_afterSelectModel_works() throws Exception {
        Ollama ai = new Ollama(baseUrl);
        ai.selectModel("llama3");
        assertEquals("Hello world", ai.send("anything"));
    }

    @Test
    void getEmbeddings_parsesFirstVector() throws Exception {
        Ollama ai = new Ollama(baseUrl, "embed-model");
        double[] vec = ai.getEmbeddings("hello");
        assertArrayEquals(new double[]{0.1, 0.2, 0.3, 0.4}, vec, 1e-9);
    }

    @Test
    void getEmbeddings_withExplicitModel_parsesVector() throws Exception {
        Ollama ai = new Ollama(baseUrl);
        double[] vec = ai.getEmbeddings("embed-model", "hello");
        assertEquals(4, vec.length);
        assertEquals(0.3, vec[2], 1e-9);
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
