package org.kissweb.oauth.as;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AuthorizationCodeStore} — in-memory store for
 * single-use authorization codes with expiry.
 */
class AuthorizationCodeStoreTest {

    private AuthorizationCodeStore store;

    @BeforeEach
    void freshStore() {
        store = AuthorizationCodeStore.get();
        // Drain any leftover codes from previous tests
        store.pruneExpired();
    }

    private AuthorizationCode makeCode(String codeValue, long expiresAtEpochSeconds) {
        return new AuthorizationCode(
                codeValue, "client-1", new AuthenticatedUser("user-1"),
                "https://app.example.com/callback",
                Collections.singleton("read"), null,
                "challenge", "S256", null, expiresAtEpochSeconds);
    }

    @Test
    void testStore_and_consume_roundTrip() {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        AuthorizationCode code = makeCode("code-abc", futureExpiry);
        store.store(code);

        AuthorizationCode consumed = store.consume("code-abc");
        assertNotNull(consumed);
        assertEquals("code-abc", consumed.getCode());
        assertEquals("client-1", consumed.getClientId());
    }

    @Test
    void testConsume_removesCode() {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        store.store(makeCode("code-once", futureExpiry));

        assertNotNull(store.consume("code-once"));
        assertNull(store.consume("code-once"));
    }

    @Test
    void testConsume_unknownCode_returnsNull() {
        assertNull(store.consume("nonexistent-code"));
    }

    @Test
    void testPruneExpired_removesExpiredCodes() {
        long pastExpiry = System.currentTimeMillis() / 1000L - 100;
        store.store(makeCode("expired-1", pastExpiry));
        store.store(makeCode("expired-2", pastExpiry));

        int pruned = store.pruneExpired();
        assertEquals(2, pruned);

        assertNull(store.consume("expired-1"));
        assertNull(store.consume("expired-2"));
    }

    @Test
    void testPruneExpired_keepsValidCodes() {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        store.store(makeCode("valid-1", futureExpiry));

        store.pruneExpired();

        assertNotNull(store.consume("valid-1"));
    }
}
