package org.kissweb.oauth;

import org.junit.jupiter.api.Test;
import org.kissweb.json.JSONArray;
import org.kissweb.json.JSONObject;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ValidatedToken} — exercises the claim-parsing logic
 * (subject, issuer, audience, scopes, timestamps) by constructing
 * JSONObject claims directly.
 */
class ValidatedTokenTest {

    // ------------------------------------------------------------------
    // subject / issuer
    // ------------------------------------------------------------------

    @Test
    void testSubjectFromSubClaim() {
        JSONObject claims = new JSONObject();
        claims.put("sub", "user-123");
        ValidatedToken token = new ValidatedToken(claims);
        assertEquals("user-123", token.getSubject());
    }

    @Test
    void testIssuerFromIssClaim() {
        JSONObject claims = new JSONObject();
        claims.put("iss", "https://as.example.com");
        ValidatedToken token = new ValidatedToken(claims);
        assertEquals("https://as.example.com", token.getIssuer());
    }

    @Test
    void testSubjectNullWhenAbsent() {
        ValidatedToken token = new ValidatedToken(new JSONObject());
        assertNull(token.getSubject());
    }

    // ------------------------------------------------------------------
    // audience (readStringOrArray)
    // ------------------------------------------------------------------

    @Test
    void testAudienceFromString() {
        JSONObject claims = new JSONObject();
        claims.put("aud", "my-resource");
        ValidatedToken token = new ValidatedToken(claims);
        Set<String> aud = token.getAudience();
        assertEquals(1, aud.size());
        assertTrue(aud.contains("my-resource"));
    }

    @Test
    void testAudienceFromArray() {
        JSONObject claims = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.put("resource-a");
        arr.put("resource-b");
        claims.put("aud", arr);
        ValidatedToken token = new ValidatedToken(claims);
        Set<String> aud = token.getAudience();
        assertEquals(2, aud.size());
        assertTrue(aud.contains("resource-a"));
        assertTrue(aud.contains("resource-b"));
    }

    @Test
    void testAudienceAbsent() {
        ValidatedToken token = new ValidatedToken(new JSONObject());
        assertTrue(token.getAudience().isEmpty());
    }

    @Test
    void testAudienceImmutable() {
        JSONObject claims = new JSONObject();
        claims.put("aud", "x");
        ValidatedToken token = new ValidatedToken(claims);
        assertThrows(UnsupportedOperationException.class,
                () -> token.getAudience().add("y"));
    }

    // ------------------------------------------------------------------
    // scopes (readScopes)
    // ------------------------------------------------------------------

    @Test
    void testScopesFromSpaceSeparatedString() {
        JSONObject claims = new JSONObject();
        claims.put("scope", "read write admin");
        ValidatedToken token = new ValidatedToken(claims);
        Set<String> scopes = token.getScopes();
        assertEquals(3, scopes.size());
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
        assertTrue(scopes.contains("admin"));
    }

    @Test
    void testScopesFromScpArray() {
        JSONObject claims = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.put("read");
        arr.put("write");
        claims.put("scp", arr);
        ValidatedToken token = new ValidatedToken(claims);
        Set<String> scopes = token.getScopes();
        assertEquals(2, scopes.size());
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
    }

    @Test
    void testScopesMergedFromBothClaims() {
        JSONObject claims = new JSONObject();
        claims.put("scope", "read");
        JSONArray arr = new JSONArray();
        arr.put("write");
        claims.put("scp", arr);
        ValidatedToken token = new ValidatedToken(claims);
        Set<String> scopes = token.getScopes();
        assertEquals(2, scopes.size());
        assertTrue(scopes.contains("read"));
        assertTrue(scopes.contains("write"));
    }

    @Test
    void testScopesAbsent() {
        ValidatedToken token = new ValidatedToken(new JSONObject());
        assertTrue(token.getScopes().isEmpty());
    }

    // ------------------------------------------------------------------
    // hasScope
    // ------------------------------------------------------------------

    @Test
    void testHasScope_true() {
        JSONObject claims = new JSONObject();
        claims.put("scope", "read write");
        ValidatedToken token = new ValidatedToken(claims);
        assertTrue(token.hasScope("read"));
    }

    @Test
    void testHasScope_false() {
        JSONObject claims = new JSONObject();
        claims.put("scope", "read");
        ValidatedToken token = new ValidatedToken(claims);
        assertFalse(token.hasScope("admin"));
    }

    @Test
    void testHasScope_nullSafe() {
        JSONObject claims = new JSONObject();
        claims.put("scope", "read");
        ValidatedToken token = new ValidatedToken(claims);
        assertFalse(token.hasScope(null));
    }

    // ------------------------------------------------------------------
    // timestamps
    // ------------------------------------------------------------------

    @Test
    void testExpiresAtEpochSeconds() {
        JSONObject claims = new JSONObject();
        claims.put("exp", 1700000000L);
        ValidatedToken token = new ValidatedToken(claims);
        assertEquals(1700000000L, token.getExpiresAtEpochSeconds());
    }

    @Test
    void testIssuedAtEpochSeconds() {
        JSONObject claims = new JSONObject();
        claims.put("iat", 1699990000L);
        ValidatedToken token = new ValidatedToken(claims);
        assertEquals(1699990000L, token.getIssuedAtEpochSeconds());
    }

    // ------------------------------------------------------------------
    // custom claims
    // ------------------------------------------------------------------

    @Test
    void testGetClaimString_providerSpecific() {
        JSONObject claims = new JSONObject();
        claims.put("email", "alice@example.com");
        ValidatedToken token = new ValidatedToken(claims);
        assertEquals("alice@example.com", token.getClaimString("email"));
    }

    @Test
    void testGetClaimString_absent() {
        ValidatedToken token = new ValidatedToken(new JSONObject());
        assertNull(token.getClaimString("nonexistent"));
    }

    @Test
    void testGetClaims_returnsFullJson() {
        JSONObject claims = new JSONObject();
        claims.put("sub", "user-1");
        claims.put("custom", "value");
        ValidatedToken token = new ValidatedToken(claims);
        JSONObject returned = token.getClaims();
        assertNotNull(returned);
        assertEquals("user-1", returned.getString("sub", null));
        assertEquals("value", returned.getString("custom", null));
    }
}
