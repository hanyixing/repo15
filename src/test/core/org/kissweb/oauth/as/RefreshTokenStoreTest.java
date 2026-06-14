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
 * Tests for {@link RefreshTokenStore} — persistent refresh tokens with
 * rotation, family revocation, and pruning. Backed by SQLite.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RefreshTokenStoreTest {

    private static Path tempDir;

    @BeforeAll
    void setupOnce() throws IOException {
        tempDir = Files.createTempDirectory("kiss-refresh-token-test-");
        MainServlet.setApplicationPath(tempDir.toString() + "/");

        for (String key : new String[]{
                "OAuthAsEnabled", "OAuthAsIssuer", "OAuthAuthorizationServer",
                "OAuthAsSqliteFile", "OAuthAsIniFile"})
            MainServlet.putEnvironment(key, "");

        MainServlet.putEnvironment("OAuthAsEnabled", "true");
        MainServlet.putEnvironment("OAuthAsIssuer", "https://test-as.example");
        MainServlet.putEnvironment("OAuthAsSqliteFile", "oauth.db");
    }

    @AfterAll
    void teardownOnce() {
        if (tempDir != null)
            deleteRecursive(tempDir.toFile());
    }

    @BeforeEach
    void freshState() {
        RefreshTokenStore.reset();
        OAuthSqliteStore.reset();
        AuthorizationServerConfig.reset();
        KeyManager.reset();
        ClientStore.reset();

        final File db = new File(tempDir.toFile(), "oauth.db");
        if (db.exists())
            db.delete();
    }

    private RefreshToken makeToken(String jti, String familyId, long expiresAt, String rotatedTo) {
        return new RefreshToken(
                jti, familyId, "client-1", "user-1",
                new JSONObject(), Collections.singleton("read"),
                "https://rs.example.com",
                System.currentTimeMillis() / 1000L, expiresAt, rotatedTo);
    }

    @Test
    void testStore_and_get_roundTrip() throws IOException {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        RefreshToken token = makeToken("jti-1", "fam-1", futureExpiry, null);
        RefreshTokenStore.get().store(token);

        RefreshToken found = RefreshTokenStore.get().get("jti-1");
        assertNotNull(found);
        assertEquals("jti-1", found.getJti());
        assertEquals("fam-1", found.getFamilyId());
        assertEquals("client-1", found.getClientId());
    }

    @Test
    void testGet_unknownJti_returnsNull() {
        assertNull(RefreshTokenStore.get().get("nonexistent-jti"));
    }

    @Test
    void testGet_nullJti_returnsNull() {
        assertNull(RefreshTokenStore.get().get(null));
    }

    @Test
    void testRotate_marksOldAndStoresNew() throws IOException {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        RefreshToken original = makeToken("old-jti", "fam-1", futureExpiry, null);
        RefreshTokenStore.get().store(original);

        RefreshToken successor = makeToken("new-jti", "fam-1", futureExpiry, null);
        RefreshTokenStore.get().rotate("old-jti", successor);

        RefreshToken oldFound = RefreshTokenStore.get().get("old-jti");
        assertNotNull(oldFound);
        assertTrue(oldFound.isRotated());
        assertEquals("new-jti", oldFound.getRotatedToJti());

        RefreshToken newFound = RefreshTokenStore.get().get("new-jti");
        assertNotNull(newFound);
        assertFalse(newFound.isRotated());
    }

    @Test
    void testRevokeFamily_deletesAllInFamily() throws IOException {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        RefreshTokenStore.get().store(makeToken("fam-tok-1", "family-A", futureExpiry, null));
        RefreshTokenStore.get().store(makeToken("fam-tok-2", "family-A", futureExpiry, null));
        RefreshTokenStore.get().store(makeToken("other-tok", "family-B", futureExpiry, null));

        RefreshTokenStore.get().revokeFamily("family-A");

        assertNull(RefreshTokenStore.get().get("fam-tok-1"));
        assertNull(RefreshTokenStore.get().get("fam-tok-2"));
        assertNotNull(RefreshTokenStore.get().get("other-tok"));
    }

    @Test
    void testRevokeFamily_nullFamilyId_noOp() {
        assertDoesNotThrow(() -> RefreshTokenStore.get().revokeFamily(null));
    }

    @Test
    void testRevokeFamily_emptyFamilyId_noOp() {
        assertDoesNotThrow(() -> RefreshTokenStore.get().revokeFamily(""));
    }

    @Test
    void testPruneExpired_removesExpiredTokens() throws IOException {
        long pastExpiry = System.currentTimeMillis() / 1000L - 100;
        RefreshTokenStore.get().store(makeToken("exp-1", "fam", pastExpiry, null));
        RefreshTokenStore.get().store(makeToken("exp-2", "fam", pastExpiry, null));

        int pruned = RefreshTokenStore.get().pruneExpired();
        assertTrue(pruned >= 2);

        assertNull(RefreshTokenStore.get().get("exp-1"));
        assertNull(RefreshTokenStore.get().get("exp-2"));
    }

    @Test
    void testPruneExpired_keepsValidTokens() throws IOException {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        RefreshTokenStore.get().store(makeToken("valid-1", "fam", futureExpiry, null));

        RefreshTokenStore.get().pruneExpired();

        assertNotNull(RefreshTokenStore.get().get("valid-1"));
    }

    @Test
    void testRoundTrip_withExtraClaims() throws IOException {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        JSONObject extra = new JSONObject();
        extra.put("email", "alice@example.com");
        extra.put("role", "admin");

        RefreshToken token = new RefreshToken(
                "extra-jti", "fam", "client-1", "user-1",
                extra, Collections.singleton("read"),
                null, System.currentTimeMillis() / 1000L, futureExpiry, null);
        RefreshTokenStore.get().store(token);

        RefreshToken found = RefreshTokenStore.get().get("extra-jti");
        assertNotNull(found);
        assertEquals("alice@example.com", found.getUserExtraClaims().getString("email", null));
        assertEquals("admin", found.getUserExtraClaims().getString("role", null));
    }

    @Test
    void testAll_returnsSnapshot() throws IOException {
        long futureExpiry = System.currentTimeMillis() / 1000L + 3600;
        RefreshTokenStore.get().store(makeToken("snap-1", "fam", futureExpiry, null));
        RefreshTokenStore.get().store(makeToken("snap-2", "fam", futureExpiry, null));

        Collection<RefreshToken> all = RefreshTokenStore.get().all();
        assertTrue(all.size() >= 2);
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
