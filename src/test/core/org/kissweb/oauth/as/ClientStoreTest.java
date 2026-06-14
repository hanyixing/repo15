package org.kissweb.oauth.as;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.kissweb.restServer.MainServlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ClientStore} — persistent client registry backed
 * by SQLite. Uses a temp directory for the SQLite database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientStoreTest {

    private static Path tempDir;

    @BeforeAll
    void setupOnce() throws IOException {
        tempDir = Files.createTempDirectory("kiss-client-store-test-");
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
    void freshState() throws IOException {
        ClientStore.reset();
        OAuthSqliteStore.reset();
        AuthorizationServerConfig.reset();
        KeyManager.reset();
        RefreshTokenStore.reset();

        final File db = new File(tempDir.toFile(), "oauth.db");
        if (db.exists())
            db.delete();
    }

    private RegisteredClient makeClient(String id, String name, boolean publicClient) {
        return new RegisteredClient(
                id,
                publicClient ? null : "secret-hash",
                name,
                Arrays.asList("https://app.example.com/callback"),
                new LinkedHashSet<>(Arrays.asList("read", "write")),
                new LinkedHashSet<>(Arrays.asList("authorization_code", "refresh_token")),
                System.currentTimeMillis() / 1000L);
    }

    @Test
    void testRegister_and_lookup() throws IOException {
        RegisteredClient client = makeClient("client-1", "Test App", true);
        ClientStore.get().register(client);

        RegisteredClient found = ClientStore.get().get("client-1");
        assertNotNull(found);
        assertEquals("client-1", found.getClientId());
        assertEquals("Test App", found.getClientName());
    }

    @Test
    void testRegister_publicClient() throws IOException {
        RegisteredClient client = makeClient("pub-1", "Public App", true);
        ClientStore.get().register(client);

        RegisteredClient found = ClientStore.get().get("pub-1");
        assertNotNull(found);
        assertTrue(found.isPublicClient());
    }

    @Test
    void testRegister_confidentialClient() throws IOException {
        RegisteredClient client = makeClient("conf-1", "Server App", false);
        ClientStore.get().register(client);

        RegisteredClient found = ClientStore.get().get("conf-1");
        assertNotNull(found);
        assertFalse(found.isPublicClient());
        assertEquals("secret-hash", found.getClientSecretHash());
    }

    @Test
    void testRemove_deletesClient() throws IOException {
        ClientStore.get().register(makeClient("to-remove", "Remove Me", true));
        assertNotNull(ClientStore.get().get("to-remove"));

        ClientStore.get().remove("to-remove");
        assertNull(ClientStore.get().get("to-remove"));
    }

    @Test
    void testGet_unknownClient_returnsNull() {
        assertNull(ClientStore.get().get("nonexistent-client"));
    }

    @Test
    void testAll_returnsSnapshot() throws IOException {
        ClientStore.get().register(makeClient("a", "App A", true));
        ClientStore.get().register(makeClient("b", "App B", false));

        Collection<RegisteredClient> all = ClientStore.get().all();
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
