package org.kissweb.oauth.as;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive tests for {@link PkceValidator} — the S256-only PKCE
 * verifier used by the OAuth 2.1 authorization server.
 */
class PkceValidatorTest {

    // RFC 7636 Appendix B test vector
    private static final String RFC_VERIFIER  = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    // ------------------------------------------------------------------
    // verify — S256 happy path
    // ------------------------------------------------------------------

    @Test
    void testS256_RFC7636AppendixBExample() {
        assertTrue(PkceValidator.verify("S256", RFC_VERIFIER, RFC_CHALLENGE));
    }

    @Test
    void testVerifyRoundTrip() {
        String verifier  = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        String challenge = PkceValidator.sha256Base64Url(verifier);
        assertTrue(PkceValidator.verify("S256", verifier, challenge));
    }

    // ------------------------------------------------------------------
    // verify — rejection cases
    // ------------------------------------------------------------------

    @Test
    void testS256_rejectsPlainMethod() {
        assertFalse(PkceValidator.verify("plain", RFC_VERIFIER, RFC_CHALLENGE));
    }

    @Test
    void testS256_rejectsNullMethod() {
        assertFalse(PkceValidator.verify(null, RFC_VERIFIER, RFC_CHALLENGE));
    }

    @Test
    void testS256_rejectsNullVerifier() {
        assertFalse(PkceValidator.verify("S256", null, RFC_CHALLENGE));
    }

    @Test
    void testS256_rejectsNullChallenge() {
        assertFalse(PkceValidator.verify("S256", RFC_VERIFIER, null));
    }

    @Test
    void testS256_rejectsShortVerifier() {
        // 42 characters — one below minimum
        String shortVerifier = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEF";
        String challenge     = PkceValidator.sha256Base64Url(shortVerifier);
        assertFalse(PkceValidator.verify("S256", shortVerifier, challenge));
    }

    @Test
    void testS256_rejectsLongVerifier() {
        // 129 characters — one above maximum
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 129; i++)
            sb.append('A');
        String longVerifier = sb.toString();
        String challenge    = PkceValidator.sha256Base64Url(longVerifier);
        assertFalse(PkceValidator.verify("S256", longVerifier, challenge));
    }

    @Test
    void testS256_rejectsTamperedVerifier() {
        String tampered = RFC_VERIFIER.substring(0, RFC_VERIFIER.length() - 1) + "X";
        assertFalse(PkceValidator.verify("S256", tampered, RFC_CHALLENGE));
    }

    // ------------------------------------------------------------------
    // verify — boundary lengths
    // ------------------------------------------------------------------

    @Test
    void testS256_acceptsMinLengthVerifier() {
        // Exactly 43 characters
        String verifier  = "abcdefghijklmnopqrstuvwxyz01234567890ABCDEF";
        assertEquals(43, verifier.length());
        String challenge = PkceValidator.sha256Base64Url(verifier);
        assertTrue(PkceValidator.verify("S256", verifier, challenge));
    }

    @Test
    void testS256_acceptsMaxLengthVerifier() {
        // Exactly 128 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 128; i++)
            sb.append((char) ('a' + (i % 26)));
        String verifier  = sb.toString();
        assertEquals(128, verifier.length());
        String challenge = PkceValidator.sha256Base64Url(verifier);
        assertTrue(PkceValidator.verify("S256", verifier, challenge));
    }

    // ------------------------------------------------------------------
    // sha256Base64Url output format
    // ------------------------------------------------------------------

    @Test
    void testSha256Base64Url_deterministicOutput() {
        String a = PkceValidator.sha256Base64Url("test-input");
        String b = PkceValidator.sha256Base64Url("test-input");
        assertEquals(a, b);
    }

    @Test
    void testSha256Base64Url_noPadding() {
        String result = PkceValidator.sha256Base64Url(RFC_VERIFIER);
        assertFalse(result.contains("="), "base64url must not contain padding");
    }

    @Test
    void testSha256Base64Url_urlSafeAlphabet() {
        // Verify the output only uses URL-safe characters
        String result = PkceValidator.sha256Base64Url(RFC_VERIFIER);
        assertTrue(result.matches("[A-Za-z0-9_-]+"),
                "base64url must only use URL-safe alphabet, got: " + result);
    }

    @Test
    void testSha256Base64Url_differentInputsDifferentOutputs() {
        String a = PkceValidator.sha256Base64Url("input-alpha");
        String b = PkceValidator.sha256Base64Url("input-bravo");
        assertNotEquals(a, b);
    }

    // ------------------------------------------------------------------
    // constantTimeEquals — different lengths
    // ------------------------------------------------------------------

    @Test
    void testConstantTime_differentLengthsRejected() {
        // Even if one is a prefix of the other, different lengths must fail
        String verifier  = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        String challenge = PkceValidator.sha256Base64Url(verifier);
        assertFalse(PkceValidator.verify("S256", verifier, challenge + "X"));
    }
}
