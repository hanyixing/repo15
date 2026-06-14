package org.kissweb.oauth.as;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kissweb.restServer.MainServlet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AuthorizationServerConfig} — exercises all
 * configuration getters and their parsing helpers (getBool, getInt,
 * trimTrailingSlash) through the public API using environment injection.
 */
class AuthorizationServerConfigTest {

    private static final String[] ALL_KEYS = {
        "OAuthAuthorizationServer", "OAuthAsEnabled", "OAuthAsIssuer",
        "OAuthAsSqliteFile", "OAuthAsIniFile",
        "OAuthAccessTokenTtlSeconds", "OAuthRefreshTokenTtlSeconds",
        "OAuthAuthCodeTtlSeconds", "OAuthPruneIntervalSeconds",
        "OAuthAllowDynamicRegistration", "OAuthSessionTtlSeconds",
        "OAuthKeyId"
    };

    @BeforeEach
    void freshConfig() {
        AuthorizationServerConfig.reset();
        for (String key : ALL_KEYS)
            MainServlet.putEnvironment(key, "");
    }

    // ------------------------------------------------------------------
    // isEnabled (exercises getBool)
    // ------------------------------------------------------------------

    @Test
    void testDisabledByDefault() {
        assertFalse(AuthorizationServerConfig.get().isEnabled());
    }

    @Test
    void testEnabledExplicitTrue() {
        MainServlet.putEnvironment("OAuthAsEnabled", "true");
        assertTrue(AuthorizationServerConfig.get().isEnabled());
    }

    @Test
    void testEnabledYesVariant() {
        MainServlet.putEnvironment("OAuthAsEnabled", "yes");
        assertTrue(AuthorizationServerConfig.get().isEnabled());
    }

    @Test
    void testEnabledOneVariant() {
        MainServlet.putEnvironment("OAuthAsEnabled", "1");
        assertTrue(AuthorizationServerConfig.get().isEnabled());
    }

    @Test
    void testEnabledFalse() {
        MainServlet.putEnvironment("OAuthAsEnabled", "false");
        assertFalse(AuthorizationServerConfig.get().isEnabled());
    }

    @Test
    void testEnabledInvalidString() {
        MainServlet.putEnvironment("OAuthAsEnabled", "maybe");
        assertFalse(AuthorizationServerConfig.get().isEnabled());
    }

    // ------------------------------------------------------------------
    // getIssuer
    // ------------------------------------------------------------------

    @Test
    void testIssuerFromExplicitKey() {
        MainServlet.putEnvironment("OAuthAsIssuer", "https://as.example.com");
        assertEquals("https://as.example.com", AuthorizationServerConfig.get().getIssuer());
    }

    @Test
    void testIssuerFallsBackToResourceServer() {
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://rs.example.com");
        assertEquals("https://rs.example.com", AuthorizationServerConfig.get().getIssuer());
    }

    @Test
    void testIssuerExplicitTakesPrecedence() {
        MainServlet.putEnvironment("OAuthAsIssuer", "https://as.example.com");
        MainServlet.putEnvironment("OAuthAuthorizationServer", "https://rs.example.com");
        assertEquals("https://as.example.com", AuthorizationServerConfig.get().getIssuer());
    }

    @Test
    void testIssuerTrimsTrailingSlash() {
        MainServlet.putEnvironment("OAuthAsIssuer", "https://as.example.com/");
        assertEquals("https://as.example.com", AuthorizationServerConfig.get().getIssuer());
    }

    @Test
    void testIssuerDefaultWhenNothingSet() {
        assertEquals("", AuthorizationServerConfig.get().getIssuer());
    }

    // ------------------------------------------------------------------
    // getSqliteFile
    // ------------------------------------------------------------------

    @Test
    void testSqliteFileDefault() {
        assertEquals("oauth.db", AuthorizationServerConfig.get().getSqliteFile());
    }

    @Test
    void testSqliteFileExplicit() {
        MainServlet.putEnvironment("OAuthAsSqliteFile", "/data/my-oauth.db");
        assertEquals("/data/my-oauth.db", AuthorizationServerConfig.get().getSqliteFile());
    }

    // ------------------------------------------------------------------
    // getIniFile
    // ------------------------------------------------------------------

    @Test
    void testIniFileNullWhenUnset() {
        assertNull(AuthorizationServerConfig.get().getIniFile());
    }

    @Test
    void testIniFileExplicit() {
        MainServlet.putEnvironment("OAuthAsIniFile", "legacy-oauth.ini");
        assertEquals("legacy-oauth.ini", AuthorizationServerConfig.get().getIniFile());
    }

    // ------------------------------------------------------------------
    // TTL defaults and explicit values
    // ------------------------------------------------------------------

    @Test
    void testAccessTokenTtlDefault() {
        assertEquals(3600, AuthorizationServerConfig.get().getAccessTokenTtlSeconds());
    }

    @Test
    void testAccessTokenTtlExplicit() {
        MainServlet.putEnvironment("OAuthAccessTokenTtlSeconds", "1800");
        assertEquals(1800, AuthorizationServerConfig.get().getAccessTokenTtlSeconds());
    }

    @Test
    void testAccessTokenTtlInvalidFallback() {
        MainServlet.putEnvironment("OAuthAccessTokenTtlSeconds", "abc");
        assertEquals(3600, AuthorizationServerConfig.get().getAccessTokenTtlSeconds());
    }

    @Test
    void testRefreshTokenTtlDefault() {
        assertEquals(2592000, AuthorizationServerConfig.get().getRefreshTokenTtlSeconds());
    }

    @Test
    void testAuthCodeTtlDefault() {
        assertEquals(60, AuthorizationServerConfig.get().getAuthCodeTtlSeconds());
    }

    @Test
    void testPruneIntervalDefault() {
        assertEquals(900, AuthorizationServerConfig.get().getPruneIntervalSeconds());
    }

    @Test
    void testSessionTtlDefault() {
        assertEquals(1800, AuthorizationServerConfig.get().getSessionTtlSeconds());
    }

    // ------------------------------------------------------------------
    // Dynamic registration
    // ------------------------------------------------------------------

    @Test
    void testAllowDynamicRegistrationDefault() {
        assertTrue(AuthorizationServerConfig.get().isAllowDynamicRegistration());
    }

    @Test
    void testAllowDynamicRegistrationExplicitFalse() {
        MainServlet.putEnvironment("OAuthAllowDynamicRegistration", "false");
        assertFalse(AuthorizationServerConfig.get().isAllowDynamicRegistration());
    }

    // ------------------------------------------------------------------
    // Key ID
    // ------------------------------------------------------------------

    @Test
    void testKeyIdDefault() {
        assertEquals("kiss-key-1", AuthorizationServerConfig.get().getKeyId());
    }

    @Test
    void testKeyIdExplicit() {
        MainServlet.putEnvironment("OAuthKeyId", "my-custom-key");
        assertEquals("my-custom-key", AuthorizationServerConfig.get().getKeyId());
    }

    // ------------------------------------------------------------------
    // reset
    // ------------------------------------------------------------------

    @Test
    void testResetClearsState() {
        MainServlet.putEnvironment("OAuthAsEnabled", "true");
        assertTrue(AuthorizationServerConfig.get().isEnabled());

        AuthorizationServerConfig.reset();
        MainServlet.putEnvironment("OAuthAsEnabled", "");
        assertFalse(AuthorizationServerConfig.get().isEnabled());
    }
}
