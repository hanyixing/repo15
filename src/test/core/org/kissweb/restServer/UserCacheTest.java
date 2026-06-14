package org.kissweb.restServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the REST server's session/user tracking: {@link UserCache} (the
 * UUID-keyed table of logged-in users) and the {@link UserData} it holds.
 * <br><br>
 * This test lives in the {@code org.kissweb.restServer} package so it can reach
 * the package-private {@code findUser}/{@code removeUser} entry points the
 * servlet uses on each request. The global logout handler is reset after every
 * test so state does not leak across the shared JVM.
 */
class UserCacheTest {

    @AfterEach
    void clearLogoutHandler() {
        UserCache.setLogoutHandler(null);
    }

    // ------------------------------------------------------------------
    // UserCache lifecycle
    // ------------------------------------------------------------------

    @Test
    void newUser_populatesFieldsAndAssignsUuid() {
        UserData ud = UserCache.newUser("alice", "secret", 42);
        assertEquals("alice", ud.getUsername());
        assertEquals("secret", ud.getPassword());
        assertEquals(42, ud.getUserId());
        assertNotNull(ud.getUuid());
        assertFalse(ud.getUuid().isEmpty());
        assertNotNull(ud.getLastAccessDate());
    }

    @Test
    void newUser_assignsDistinctUuids() {
        UserData a = UserCache.newUser("a", "p", 1);
        UserData b = UserCache.newUser("b", "p", 2);
        assertNotEquals(a.getUuid(), b.getUuid());
    }

    @Test
    void findUser_returnsTheRegisteredInstance() {
        UserData ud = UserCache.newUser("bob", "pw", 7);
        assertSame(ud, UserCache.findUser(ud.getUuid()));
    }

    @Test
    void findUser_nullOrEmptyUuidReturnsNull() {
        assertNull(UserCache.findUser(null));
        assertNull(UserCache.findUser(""));
    }

    @Test
    void findUser_unknownUuidReturnsNull() {
        assertNull(UserCache.findUser("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void removeUser_evictsTheUser() {
        UserData ud = UserCache.newUser("carol", "pw", 9);
        String uuid = ud.getUuid();
        assertSame(ud, UserCache.findUser(uuid));
        UserCache.removeUser(uuid);
        assertNull(UserCache.findUser(uuid));
    }

    @Test
    void removeUser_invokesGlobalLogoutHandler() {
        AtomicReference<UserData> seen = new AtomicReference<>();
        UserCache.setLogoutHandler(seen::set);

        UserData ud = UserCache.newUser("dave", "pw", 11);
        UserCache.removeUser(ud.getUuid());

        assertSame(ud, seen.get(), "logout handler must receive the removed user");
    }

    @Test
    void removeUser_stillEvictsWhenLogoutHandlerThrows() {
        UserCache.setLogoutHandler(u -> { throw new RuntimeException("boom"); });

        UserData ud = UserCache.newUser("erin", "pw", 13);
        String uuid = ud.getUuid();

        // The handler throwing must not propagate, and the user must still be removed.
        assertDoesNotThrow(() -> UserCache.removeUser(uuid));
        assertNull(UserCache.findUser(uuid));
    }

    // ------------------------------------------------------------------
    // UserData per-user storage
    // ------------------------------------------------------------------

    @Test
    void userData_storesAndRetrievesByKey() {
        UserData ud = UserCache.newUser("frank", "pw", 15);
        ud.putUserData("role", "admin");
        assertEquals("admin", ud.getUserData("role"));
    }

    @Test
    void userData_putNullRemovesKey() {
        UserData ud = UserCache.newUser("grace", "pw", 17);
        ud.putUserData("temp", "value");
        assertEquals("value", ud.getUserData("temp"));
        ud.putUserData("temp", null);
        assertNull(ud.getUserData("temp"));
    }

    @Test
    void userData_lastAccessDateRoundTrips() {
        UserData ud = UserCache.newUser("heidi", "pw", 19);
        LocalDateTime when = LocalDateTime.of(2020, 1, 2, 3, 4, 5);
        ud.setLastAccessDate(when);
        assertEquals(when, ud.getLastAccessDate());
    }
}
