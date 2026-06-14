package org.kissweb;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kissweb.json.JSONObject;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RestClient} — HTTP client with JSON/string calls,
 * retry logic, and timeouts. Backed by an in-process mock server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestClientTest {

    private HttpServer mockServer;
    private String baseUrl;

    @BeforeAll
    void setupServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        // Mock JSON echo endpoint
        mockServer.createContext("/json", exchange -> {
            String method = exchange.getRequestMethod();
            JSONObject resp = new JSONObject();
            resp.put("method", method);
            resp.put("status", "ok");
            respondJson(exchange, 200, resp.toString());
        });

        // Mock string endpoint
        mockServer.createContext("/text", exchange -> {
            byte[] body = "Hello World".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Mock error endpoint (returns 500)
        mockServer.createContext("/error", exchange -> {
            respondJson(exchange, 500, "{\"error\":\"server error\"}");
        });

        // Mock basic auth endpoint
        mockServer.createContext("/auth", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            JSONObject resp = new JSONObject();
            resp.put("auth", auth != null ? auth : "none");
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
    // jsonCall
    // ------------------------------------------------------------------

    @Test
    void testJsonCall_post_sendsAndReceivesJson() throws Exception {
        RestClient client = new RestClient();
        JSONObject body = new JSONObject();
        body.put("key", "value");
        JSONObject resp = client.jsonCall("POST", baseUrl + "/json", body);
        assertNotNull(resp);
        assertEquals("POST", resp.getString("method", null));
        assertEquals("ok", resp.getString("status", null));
    }

    @Test
    void testJsonCall_get_sendsGet() throws Exception {
        RestClient client = new RestClient();
        JSONObject resp = client.jsonCall("GET", baseUrl + "/json");
        assertNotNull(resp);
        assertEquals("GET", resp.getString("method", null));
    }

    // ------------------------------------------------------------------
    // strCall
    // ------------------------------------------------------------------

    @Test
    void testStrCall_post_sendsString() throws Exception {
        RestClient client = new RestClient();
        String resp = client.strCall("POST", baseUrl + "/text", "hello");
        assertNotNull(resp);
        assertEquals("Hello World", resp);
    }

    @Test
    void testStrCall_get() throws Exception {
        RestClient client = new RestClient();
        String resp = client.strCall("GET", baseUrl + "/text");
        assertNotNull(resp);
        assertEquals("Hello World", resp);
    }

    // ------------------------------------------------------------------
    // Response code / string
    // ------------------------------------------------------------------

    @Test
    void testResponseCode_setAfterCall() throws Exception {
        RestClient client = new RestClient();
        client.jsonCall("GET", baseUrl + "/json");
        assertEquals(200, client.getResponseCode());
    }

    @Test
    void testResponseString_setAfterCall() throws Exception {
        RestClient client = new RestClient();
        client.jsonCall("GET", baseUrl + "/json");
        String respStr = client.getResponseString();
        assertNotNull(respStr);
        assertFalse(respStr.isEmpty());
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    @Test
    void testSetTimeouts() {
        RestClient client = new RestClient();
        client.setTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(10));
        // No exception means success
    }

    @Test
    void testSetRetryPolicy() {
        RestClient client = new RestClient();
        client.setRetryPolicy(3, 100);
        // No exception means success
    }

    // ------------------------------------------------------------------
    // basicAuthenticationHeader (static utility)
    // ------------------------------------------------------------------

    @Test
    void testBasicAuthenticationHeader() {
        JSONObject headers = RestClient.basicAuthenticationHeader("user", "pass");
        assertNotNull(headers);
        String auth = headers.getString("Authorization", null);
        assertNotNull(auth);
        assertTrue(auth.startsWith("Basic "));
    }

    // ------------------------------------------------------------------
    // Error handling
    // ------------------------------------------------------------------

    @Test
    void testJsonCall_serverError_returnsResponse() throws Exception {
        RestClient client = new RestClient();
        client.setRetryPolicy(1, 10);
        // Server returns 500 — RestClient should still return a response
        JSONObject resp = client.jsonCall("GET", baseUrl + "/error");
        // Response may be null or contain error info depending on implementation
        assertEquals(500, client.getResponseCode());
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
