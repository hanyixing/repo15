package org.kissweb.restServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UserCache} — session lifecycle, lookup, removal,
 * and logout handler. Package-private access from same package.
 */
class UserCacheTest {

    @BeforeEach
    void cleanState() {
        UserCache.setLogoutHandler(null);
        UserCache.setInactiveUserMaxSeconds(3600);
    }

    // ------------------------------------------------------------------
    // newUser / findUser
    // ------------------------------------------------------------------

    @Test
    void testNewUser_createsAndReturns() {
        UserData ud = UserCache.newUser("alice", "secret", 42);
        assertNotNull(ud);
        assertEquals("alice", ud.getUsername());
        assertEquals("secret", ud.getPassword());
        assertEquals(42, ud.getUserId());
        assertNotNull(ud.getUuid());
        assertFalse(ud.getUuid().isEmpty());
    }

    @Test
    void testFindUser_byUuid() {
        UserData ud = UserCache.newUser("bob", "pass", 1);
        String uuid = ud.getUuid();

        UserData found = UserCache.findUser(uuid);
        assertNotNull(found);
        assertEquals("bob", found.getUsername());
        assertSame(ud, found);
    }

    @Test
    void testFindUser_nullUuid() {
        assertNull(UserCache.findUser(null));
    }

    @Test
    void testFindUser_emptyUuid() {
        assertNull(UserCache.findUser(""));
    }

    @Test
    void testFindUser_unknownUuid() {
        assertNull(UserCache.findUser("nonexistent-uuid-12345"));
    }

    // ------------------------------------------------------------------
    // removeUser
    // ------------------------------------------------------------------

    @Test
    void testRemoveUser_removesFromCache() {
        UserData ud = UserCache.newUser("charlie", "pass", 1);
        String uuid = ud.getUuid();

        assertNotNull(UserCache.findUser(uuid));
        UserCache.removeUser(uuid);
        assertNull(UserCache.findUser(uuid));
    }

    @Test
    void testRemoveUser_callsLogoutHandler() {
        UserData ud = UserCache.newUser("dave", "pass", 1);
        String uuid = ud.getUuid();

        AtomicBoolean called = new AtomicBoolean(false);
        AtomicReference<String> handlerUsername = new AtomicReference<>();
        UserCache.setLogoutHandler(userData -> {
            called.set(true);
            handlerUsername.set(userData.getUsername());
        });

        UserCache.removeUser(uuid);
        assertTrue(called.get());
        assertEquals("dave", handlerUsername.get());
    }

    @Test
    void testRemoveUser_handlerExceptionStillRemoves() {
        UserData ud = UserCache.newUser("eve", "pass", 1);
        String uuid = ud.getUuid();

        UserCache.setLogoutHandler(userData -> {
            throw new RuntimeException("handler error");
        });

        // Should not throw, and user should still be removed
        assertDoesNotThrow(() -> UserCache.removeUser(uuid));
        assertNull(UserCache.findUser(uuid));
    }

    @Test
    void testRemoveUser_unknownUuid_noError() {
        assertDoesNotThrow(() -> UserCache.removeUser("unknown-uuid"));
    }

    // ------------------------------------------------------------------
    // UserData put/get
    // ------------------------------------------------------------------

    @Test
    void testUserData_putAndGet() {
        UserData ud = UserCache.newUser("frank", "pass", 1);
        ud.putUserData("theme", "dark");
        assertEquals("dark", ud.getUserData("theme"));
    }

    @Test
    void testUserData_putNullRemoves() {
        UserData ud = UserCache.newUser("grace", "pass", 1);
        ud.putUserData("pref", "value");
        assertEquals("value", ud.getUserData("pref"));

        ud.putUserData("pref", null);
        assertNull(ud.getUserData("pref"));
    }

    @Test
    void testUserData_unknownKey() {
        UserData ud = UserCache.newUser("heidi", "pass", 1);
        assertNull(ud.getUserData("nonexistent"));
    }
}
