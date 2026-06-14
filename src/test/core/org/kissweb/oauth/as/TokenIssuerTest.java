package org.kissweb.oauth.as;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kissweb.json.JSONObject;
import org.kissweb.restServer.MainServlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TokenIssuer} — JWT access token signing and random
 * token generation.  Uses a real RSA keypair from KeyManager backed by
 * a temp SQLite database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenIssuerTest {

    private static Path tempDir;

    @BeforeAll
    void setupOnce() throws IOException {
        tempDir = Files.createTempDirectory("kiss-token-issuer-test-");
        MainServlet.setApplicationPath(tempDir.toString() + "/");

        for (String key : new String[]{
                "OAuthAsEnabled", "OAuthAsIssuer", "OAuthAuthorizationServer",
                "OAuthAsSqliteFile", "OAuthAsIniFile", "OAuthKeyId"})
            MainServlet.putEnvironment(key, "");

        MainServlet.putEnvironment("OAuthAsEnabled", "true");
        MainServlet.putEnvironment("OAuthAsIssuer", "https://test-as.example");
        MainServlet.putEnvironment("OAuthAsSqliteFile", "oauth.db");
        MainServlet.putEnvironment("OAuthKeyId", "test-kid");
    }

    @AfterAll
    void teardownOnce() {
        if (tempDir != null)
            deleteRecursive(tempDir.toFile());
    }

    @BeforeEach
    void freshState() {
        KeyManager.reset();
        OAuthSqliteStore.reset();
        AuthorizationServerConfig.reset();

        final File db = new File(tempDir.toFile(), "oauth.db");
        if (db.exists())
            db.delete();
    }

    // ------------------------------------------------------------------
    // issueAccessToken
    // ------------------------------------------------------------------

    @Test
    void testIssueAccessToken_returnsNonEmptyJwt() {
        AuthenticatedUser user = new AuthenticatedUser("user-1");
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write"));

        String jwt = TokenIssuer.issueAccessToken(user, "client-A", scopes, "https://rs.example.com");
        assertNotNull(jwt);
        assertFalse(jwt.isEmpty());

        // JWT has 3 parts separated by dots
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT should have header.payload.signature");
    }

    @Test
    void testIssueAccessToken_containsClaims() throws Exception {
        JSONObject extra = new JSONObject();
        extra.put("email", "alice@example.com");
        AuthenticatedUser user = new AuthenticatedUser("user-2", extra);
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read"));

        String jwt = TokenIssuer.issueAccessToken(user, "client-B", scopes, "https://rs.example.com");
        assertNotNull(jwt);

        // Decode the payload (second part)
        String[] parts = jwt.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JSONObject claims = new JSONObject(payload);

        assertEquals("user-2", claims.getString("sub", null));
        assertEquals("client-B", claims.getString("client_id", null));
        assertEquals("https://rs.example.com", claims.getString("aud", null));
        assertEquals("https://test-as.example", claims.getString("iss", null));
        assertTrue(claims.has("exp"));
        assertTrue(claims.has("iat"));
    }

    // ------------------------------------------------------------------
    // issueRefreshTokenValue
    // ------------------------------------------------------------------

    @Test
    void testIssueRefreshTokenValue_isNonEmpty() {
        String value = TokenIssuer.issueRefreshTokenValue();
        assertNotNull(value);
        assertFalse(value.isEmpty());
    }

    @Test
    void testIssueRefreshTokenValue_isUnique() {
        String a = TokenIssuer.issueRefreshTokenValue();
        String b = TokenIssuer.issueRefreshTokenValue();
        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // issueAuthorizationCodeValue
    // ------------------------------------------------------------------

    @Test
    void testIssueAuthorizationCodeValue_isNonEmpty() {
        String value = TokenIssuer.issueAuthorizationCodeValue();
        assertNotNull(value);
        assertFalse(value.isEmpty());
    }

    @Test
    void testIssueAuthorizationCodeValue_isUnique() {
        String a = TokenIssuer.issueAuthorizationCodeValue();
        String b = TokenIssuer.issueAuthorizationCodeValue();
        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // scopesAsString / scopesAsArray
    // ------------------------------------------------------------------

    @Test
    void testScopesAsString_spaceSeparated() {
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write"));
        String result = TokenIssuer.scopesAsString(scopes);
        assertTrue(result.contains("read"));
        assertTrue(result.contains("write"));
    }

    @Test
    void testScopesAsArray_containsAllScopes() {
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write", "admin"));
        var arr = TokenIssuer.scopesAsArray(scopes);
        assertEquals(3, arr.length());
    }

    // ------------------------------------------------------------------
    // KeyManager.buildJwks
    // ------------------------------------------------------------------

    @Test
    void testBuildJwks_containsKeysArray() {
        JSONObject jwks = KeyManager.get().buildJwks();
        assertNotNull(jwks);
        assertTrue(jwks.has("keys"));
    }

    @Test
    void testBuildJwks_keyHasRequiredFields() {
        JSONObject jwks = KeyManager.get().buildJwks();
        var keys = jwks.getJSONArray("keys");
        assertTrue(keys.length() > 0);

        JSONObject key = keys.getJSONObject(0);
        assertTrue(key.has("kty"));
        assertTrue(key.has("n"));
        assertTrue(key.has("e"));
        assertTrue(key.has("kid"));
        assertTrue(key.has("alg"));
        assertTrue(key.has("use"));
    }

    @Test
    void testBuildJwks_keyTypeIsRSA() {
        JSONObject jwks = KeyManager.get().buildJwks();
        JSONObject key = jwks.getJSONArray("keys").getJSONObject(0);
        assertEquals("RSA", key.getString("kty", null));
    }

    @Test
    void testBuildJwks_algorithmIsRS256() {
        JSONObject jwks = KeyManager.get().buildJwks();
        JSONObject key = jwks.getJSONArray("keys").getJSONObject(0);
        assertEquals("RS256", key.getString("alg", null));
    }

    @Test
    void testBuildJwks_useIsSig() {
        JSONObject jwks = KeyManager.get().buildJwks();
        JSONObject key = jwks.getJSONArray("keys").getJSONObject(0);
        assertEquals("sig", key.getString("use", null));
    }

    @Test
    void testKeyManager_kidMatchesConfig() {
        assertEquals("test-kid", KeyManager.get().getKid());
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null)
                for (File child : children)
                    deleteRecursive(child);
        }
        f.delete();
    }
}
