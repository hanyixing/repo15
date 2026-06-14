package org.kissweb.oauth.as;

import org.junit.jupiter.api.Test;
import org.kissweb.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OAuth AS model classes:
 * {@link AuthenticatedUser}, {@link AuthorizationCode},
 * {@link RefreshToken}, and {@link RegisteredClient}.
 */
class ModelClassesTest {

    // ==================================================================
    // AuthenticatedUser
    // ==================================================================

    @Test
    void testAuthenticatedUser_subjectOnlyConstructor() {
        AuthenticatedUser user = new AuthenticatedUser("user-1");
        assertEquals("user-1", user.getSubject());
        assertNotNull(user.getExtraClaims());
        assertEquals(0, user.getExtraClaims().length());
    }

    @Test
    void testAuthenticatedUser_subjectWithExtraClaims() {
        JSONObject extra = new JSONObject();
        extra.put("email", "alice@example.com");
        AuthenticatedUser user = new AuthenticatedUser("user-2", extra);
        assertEquals("user-2", user.getSubject());
        assertEquals("alice@example.com", user.getExtraClaims().getString("email", null));
    }

    @Test
    void testAuthenticatedUser_subjectRequired_null() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedUser(null));
    }

    @Test
    void testAuthenticatedUser_subjectRequired_empty() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedUser(""));
    }

    @Test
    void testAuthenticatedUser_nullExtraClaimsBecomesEmpty() {
        AuthenticatedUser user = new AuthenticatedUser("user-3", null);
        assertNotNull(user.getExtraClaims());
        assertEquals(0, user.getExtraClaims().length());
    }

    // ==================================================================
    // AuthorizationCode
    // ==================================================================

    @Test
    void testAuthorizationCode_allFieldsAccessible() {
        AuthenticatedUser user = new AuthenticatedUser("user-1");
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write"));
        long expiry = System.currentTimeMillis() / 1000L + 3600;

        AuthorizationCode code = new AuthorizationCode(
                "code-123", "client-A", user, "https://app.example.com/callback",
                scopes, "https://rs.example.com", "challenge-abc", "S256",
                "nonce-xyz", expiry);

        assertEquals("code-123", code.getCode());
        assertEquals("client-A", code.getClientId());
        assertSame(user, code.getUser());
        assertEquals("https://app.example.com/callback", code.getRedirectUri());
        assertEquals(2, code.getScopes().size());
        assertTrue(code.getScopes().contains("read"));
        assertEquals("https://rs.example.com", code.getAudience());
        assertEquals("challenge-abc", code.getCodeChallenge());
        assertEquals("S256", code.getCodeChallengeMethod());
        assertEquals("nonce-xyz", code.getNonce());
        assertEquals(expiry, code.getExpiresAtEpochSeconds());
    }

    @Test
    void testAuthorizationCode_scopesAreImmutable() {
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write"));
        AuthorizationCode code = new AuthorizationCode(
                "c", "client", new AuthenticatedUser("u"), "uri",
                scopes, null, "ch", "S256", null, 9999999999L);

        Set<String> returned = code.getScopes();
        assertThrows(UnsupportedOperationException.class, () -> returned.add("admin"));
    }

    @Test
    void testAuthorizationCode_isExpired_futureNotExpired() {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        AuthorizationCode code = new AuthorizationCode(
                "c", "client", new AuthenticatedUser("u"), "uri",
                Collections.emptySet(), null, "ch", "S256", null, futureExpiry);
        assertFalse(code.isExpired());
    }

    @Test
    void testAuthorizationCode_isExpired_pastExpired() {
        long pastExpiry = System.currentTimeMillis() / 1000L - 100;
        AuthorizationCode code = new AuthorizationCode(
                "c", "client", new AuthenticatedUser("u"), "uri",
                Collections.emptySet(), null, "ch", "S256", null, pastExpiry);
        assertTrue(code.isExpired());
    }

    // ==================================================================
    // RefreshToken
    // ==================================================================

    @Test
    void testRefreshToken_allFieldsAccessible() {
        JSONObject extra = new JSONObject();
        extra.put("role", "admin");
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write"));

        RefreshToken token = new RefreshToken(
                "jti-1", "family-1", "client-A", "user-1", extra,
                scopes, "https://rs.example.com", 1000L, 2000L, null);

        assertEquals("jti-1", token.getJti());
        assertEquals("family-1", token.getFamilyId());
        assertEquals("client-A", token.getClientId());
        assertEquals("user-1", token.getUserSubject());
        assertEquals("admin", token.getUserExtraClaims().getString("role", null));
        assertEquals(2, token.getScopes().size());
        assertEquals("https://rs.example.com", token.getAudience());
        assertEquals(1000L, token.getCreatedAtEpochSeconds());
        assertEquals(2000L, token.getExpiresAtEpochSeconds());
        assertNull(token.getRotatedToJti());
    }

    @Test
    void testRefreshToken_isRotated_falseWhenNull() {
        RefreshToken token = new RefreshToken(
                "jti", "fam", "c", "u", null,
                Collections.emptySet(), null, 1000L, 2000L, null);
        assertFalse(token.isRotated());
    }

    @Test
    void testRefreshToken_isRotated_trueWhenSet() {
        RefreshToken token = new RefreshToken(
                "jti", "fam", "c", "u", null,
                Collections.emptySet(), null, 1000L, 2000L, "next-jti");
        assertTrue(token.isRotated());
        assertEquals("next-jti", token.getRotatedToJti());
    }

    @Test
    void testRefreshToken_isExpired_futureNotExpired() {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        RefreshToken token = new RefreshToken(
                "jti", "fam", "c", "u", null,
                Collections.emptySet(), null, 1000L, futureExpiry, null);
        assertFalse(token.isExpired());
    }

    @Test
    void testRefreshToken_isExpired_pastExpired() {
        long pastExpiry = System.currentTimeMillis() / 1000L - 100;
        RefreshToken token = new RefreshToken(
                "jti", "fam", "c", "u", null,
                Collections.emptySet(), null, 1000L, pastExpiry, null);
        assertTrue(token.isExpired());
    }

    @Test
    void testRefreshToken_withRotation_createsNewInstance() {
        RefreshToken original = new RefreshToken(
                "jti-1", "fam", "c", "u", null,
                Collections.emptySet(), null, 1000L, 2000L, null);
        RefreshToken rotated = original.withRotation("jti-2");

        assertNotSame(original, rotated);
        assertNull(original.getRotatedToJti());
        assertEquals("jti-2", rotated.getRotatedToJti());
    }

    @Test
    void testRefreshToken_withRotation_preservesFields() {
        JSONObject extra = new JSONObject();
        extra.put("email", "bob@example.com");
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList("read", "write"));

        RefreshToken original = new RefreshToken(
                "jti-1", "fam-1", "client-B", "user-2", extra,
                scopes, "https://rs.example.com", 5000L, 6000L, null);
        RefreshToken rotated = original.withRotation("jti-2");

        assertEquals(original.getJti(), rotated.getJti());
        assertEquals(original.getFamilyId(), rotated.getFamilyId());
        assertEquals(original.getClientId(), rotated.getClientId());
        assertEquals(original.getUserSubject(), rotated.getUserSubject());
        assertEquals(original.getScopes(), rotated.getScopes());
        assertEquals(original.getAudience(), rotated.getAudience());
        assertEquals(original.getCreatedAtEpochSeconds(), rotated.getCreatedAtEpochSeconds());
        assertEquals(original.getExpiresAtEpochSeconds(), rotated.getExpiresAtEpochSeconds());
    }

    @Test
    void testRefreshToken_getUser_reconstitutesAuthenticatedUser() {
        JSONObject extra = new JSONObject();
        extra.put("email", "alice@example.com");

        RefreshToken token = new RefreshToken(
                "jti", "fam", "c", "user-1", extra,
                Collections.emptySet(), null, 1000L, 2000L, null);
        AuthenticatedUser user = token.getUser();

        assertEquals("user-1", user.getSubject());
        assertEquals("alice@example.com", user.getExtraClaims().getString("email", null));
    }

    @Test
    void testRefreshToken_nullExtraClaimsBecomesEmpty() {
        RefreshToken token = new RefreshToken(
                "jti", "fam", "c", "u", null,
                Collections.emptySet(), null, 1000L, 2000L, null);
        assertNotNull(token.getUserExtraClaims());
        assertEquals(0, token.getUserExtraClaims().length());
    }

    // ==================================================================
    // RegisteredClient
    // ==================================================================

    @Test
    void testRegisteredClient_publicClient() {
        RegisteredClient client = new RegisteredClient(
                "client-1", null, "My App",
                Arrays.asList("https://app.example.com/callback"),
                new LinkedHashSet<>(Arrays.asList("read", "write")),
                new LinkedHashSet<>(Arrays.asList("authorization_code", "refresh_token")),
                1000L);

        assertTrue(client.isPublicClient());
        assertNull(client.getClientSecretHash());
        assertEquals("client-1", client.getClientId());
        assertEquals("My App", client.getClientName());
    }

    @Test
    void testRegisteredClient_confidentialClient() {
        RegisteredClient client = new RegisteredClient(
                "client-2", "sha256hexhash", "Server App",
                Arrays.asList("https://server.example.com/callback"),
                new LinkedHashSet<>(Collections.singletonList("read")),
                new LinkedHashSet<>(Arrays.asList("authorization_code", "refresh_token")),
                2000L);

        assertFalse(client.isPublicClient());
        assertEquals("sha256hexhash", client.getClientSecretHash());
    }

    @Test
    void testRegisteredClient_hasRedirectUri_match() {
        RegisteredClient client = new RegisteredClient(
                "c", null, "n",
                Arrays.asList("https://a.com/cb", "https://b.com/cb"),
                Collections.emptySet(), Collections.emptySet(), 0L);

        assertTrue(client.hasRedirectUri("https://a.com/cb"));
        assertTrue(client.hasRedirectUri("https://b.com/cb"));
    }

    @Test
    void testRegisteredClient_hasRedirectUri_noMatch() {
        RegisteredClient client = new RegisteredClient(
                "c", null, "n",
                Arrays.asList("https://a.com/cb"),
                Collections.emptySet(), Collections.emptySet(), 0L);

        assertFalse(client.hasRedirectUri("https://evil.com/cb"));
    }

    @Test
    void testRegisteredClient_hasRedirectUri_nullSafe() {
        RegisteredClient client = new RegisteredClient(
                "c", null, "n",
                Arrays.asList("https://a.com/cb"),
                Collections.emptySet(), Collections.emptySet(), 0L);

        assertFalse(client.hasRedirectUri(null));
    }

    @Test
    void testRegisteredClient_redirectUrisImmutable() {
        RegisteredClient client = new RegisteredClient(
                "c", null, "n",
                Arrays.asList("https://a.com/cb"),
                Collections.emptySet(), Collections.emptySet(), 0L);

        List<String> uris = client.getRedirectUris();
        assertThrows(UnsupportedOperationException.class,
                () -> uris.add("https://evil.com/cb"));
    }

    @Test
    void testRegisteredClient_allowedScopesImmutable() {
        RegisteredClient client = new RegisteredClient(
                "c", null, "n", Collections.emptyList(),
                new LinkedHashSet<>(Arrays.asList("read", "write")),
                Collections.emptySet(), 0L);

        Set<String> scopes = client.getAllowedScopes();
        assertThrows(UnsupportedOperationException.class,
                () -> scopes.add("admin"));
    }
}
