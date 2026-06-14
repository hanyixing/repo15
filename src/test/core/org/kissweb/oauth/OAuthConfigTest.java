package org.kissweb.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kissweb.restServer.MainServlet;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OAuthConfig} — exercises all configuration getters
 * and their parsing helpers (splitList, trimTrailingSlash, getInt)
 * through the public API using environment injection.
 */
class OAuthConfigTest {

    private static final String[] ALL_KEYS = {
        "OAuthAuthorizationServer", "OAuthResourceIdentifier",
        "OAuthJwksUri", "OAuthRequiredScopes", "OAuthJwksCacheSeconds",
        "OAuthAllowedAlgorithms", "OAuthClockSkewSeconds"
    };

    @BeforeEach
    void freshConfig() {
        OAuthConfig.reset();
        for (String key : ALL_KEYS)
            MainServlet.putEnvironment(key, "");
    }

    // ------------------------------------------------------------------
    // isEnabled / getAuthorizationServer
    // ------------------------------------------------------------------

    @Test
    void testDisabledWhenNoAuthorizationServer() {
        OAuthConfig cfg = OAuthConfig.get();
        assertFalse(cfg.isEnabled());
        assertNull(cfg.getAuthorizationServer());
    }

    @Test
    void testEnabledWhenAuthorizationServerSet() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://as.example.com");
        OAuthConfig cfg = OAuthConfig.get();
        assertTrue(cfg.isEnabled());
        assertEquals("https://as.example.com", cfg.getAuthorizationServer());
    }

    // ------------------------------------------------------------------
    // trimTrailingSlash (exercised via getAuthorizationServer)
    // ------------------------------------------------------------------

    @Test
    void testTrimTrailingSlash() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://as.example.com/");
        assertEquals("https://as.example.com", OAuthConfig.get().getAuthorizationServer());
    }

    @Test
    void testTrimMultipleTrailingSlashes() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://as.example.com///");
        assertEquals("https://as.example.com", OAuthConfig.get().getAuthorizationServer());
    }

    // ------------------------------------------------------------------
    // getResourceIdentifier
    // ------------------------------------------------------------------

    @Test
    void testResourceIdentifierDefaultsToAuthServer() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://as.example.com");
        assertEquals("https://as.example.com", OAuthConfig.get().getResourceIdentifier());
    }

    @Test
    void testResourceIdentifierExplicit() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://as.example.com");
        MainServlet.putEnvironment("OAuthResourceIdentifier", "https://rs.example.com");
        assertEquals("https://rs.example.com", OAuthConfig.get().getResourceIdentifier());
    }

    // ------------------------------------------------------------------
    // getRequiredScopes (exercises splitList)
    // ------------------------------------------------------------------

    @Test
    void testRequiredScopesCommaSeparated() {
        MainServlet.putEnvironment("OAuthRequiredScopes", "read, write");
        List<String> scopes = OAuthConfig.get().getRequiredScopes();
        assertEquals(2, scopes.size());
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
    }

    @Test
    void testRequiredScopesSpaceSeparated() {
        MainServlet.putEnvironment("OAuthRequiredScopes", "read write admin");
        List<String> scopes = OAuthConfig.get().getRequiredScopes();
        assertEquals(3, scopes.size());
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
        assertTrue(scopes.contains("admin"));
    }

    @Test
    void testRequiredScopesEmpty() {
        List<String> scopes = OAuthConfig.get().getRequiredScopes();
        assertTrue(scopes.isEmpty());
    }

    @Test
    void testRequiredScopesImmutable() {
        MainServlet.putEnvironment("OAuthRequiredScopes", "read");
        List<String> scopes = OAuthConfig.get().getRequiredScopes();
        assertThrows(UnsupportedOperationException.class, () -> scopes.add("write"));
    }

    // ------------------------------------------------------------------
    // getJwksUri
    // ------------------------------------------------------------------

    @Test
    void testJwksUriNullWhenUnset() {
        assertNull(OAuthConfig.get().getJwksUri());
    }

    @Test
    void testJwksUriPassthrough() {
        MainServlet.putEnvironment("OAuthJwksUri", "https://as.example.com/jwks");
        assertEquals("https://as.example.com/jwks", OAuthConfig.get().getJwksUri());
    }

    // ------------------------------------------------------------------
    // getJwksCacheSeconds (exercises getInt)
    // ------------------------------------------------------------------

    @Test
    void testJwksCacheSecondsDefault() {
        assertEquals(3600, OAuthConfig.get().getJwksCacheSeconds());
    }

    @Test
    void testJwksCacheSecondsExplicit() {
        MainServlet.putEnvironment("OAuthJwksCacheSeconds", "7200");
        assertEquals(7200, OAuthConfig.get().getJwksCacheSeconds());
    }

    @Test
    void testJwksCacheSecondsInvalidFallback() {
        MainServlet.putEnvironment("OAuthJwksCacheSeconds", "abc");
        assertEquals(3600, OAuthConfig.get().getJwksCacheSeconds());
    }

    // ------------------------------------------------------------------
    // getAllowedAlgorithms
    // ------------------------------------------------------------------

    @Test
    void testAllowedAlgorithmsDefault() {
        Set<String> algs = OAuthConfig.get().getAllowedAlgorithms();
        assertEquals(1, algs.size());
        assertTrue(algs.contains("RS256"));
    }

    @Test
    void testAllowedAlgorithmsExplicit() {
        MainServlet.putEnvironment("OAuthAllowedAlgorithms", "RS256, ES256");
        Set<String> algs = OAuthConfig.get().getAllowedAlgorithms();
        assertEquals(2, algs.size());
        assertTrue(algs.contains("RS256"));
        assertTrue(algs.contains("ES256"));
    }

    // ------------------------------------------------------------------
    // getClockSkewSeconds
    // ------------------------------------------------------------------

    @Test
    void testClockSkewSecondsDefault() {
        assertEquals(60, OAuthConfig.get().getClockSkewSeconds());
    }

    @Test
    void testClockSkewSecondsExplicit() {
        MainServlet.putEnvironment("OAuthClockSkewSeconds", "120");
        assertEquals(120, OAuthConfig.get().getClockSkewSeconds());
    }

    // ------------------------------------------------------------------
    // reset
    // ------------------------------------------------------------------

    @Test
    void testResetClearsState() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://first.example.com");
        assertTrue(OAuthConfig.get().isEnabled());

        OAuthConfig.reset();
        MainServlet.putEnvironment("OAuthAuthorizationServer", "");
        assertFalse(OAuthConfig.get().isEnabled());
    }
}
