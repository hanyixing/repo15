package org.kissweb.restServer;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UserData} — field access, UUID generation,
 * last access date, and custom user data map.
 * Uses package-private constructor from same package.
 */
class UserDataTest {

    @Test
    void testConstructor_setsFields() {
        UserData ud = new UserData("alice", "secret", 42);
        assertEquals("alice", ud.getUsername());
        assertEquals("secret", ud.getPassword());
        assertEquals(42, ud.getUserId());
    }

    @Test
    void testUuid_isGenerated() {
        UserData ud = new UserData("bob", "pass", 1);
        assertNotNull(ud.getUuid());
        assertFalse(ud.getUuid().isEmpty());
    }

    @Test
    void testUuid_isUnique() {
        UserData ud1 = new UserData("a", "p", 1);
        UserData ud2 = new UserData("b", "p", 2);
        assertNotEquals(ud1.getUuid(), ud2.getUuid());
    }

    @Test
    void testLastAccessDate_setOnCreation() {
        LocalDateTime before = LocalDateTime.now();
        UserData ud = new UserData("charlie", "pass", 1);
        LocalDateTime after = LocalDateTime.now();

        LocalDateTime lad = ud.getLastAccessDate();
        assertNotNull(lad);
        assertFalse(lad.isBefore(before));
        assertFalse(lad.isAfter(after));
    }

    @Test
    void testSetLastAccessDate() {
        UserData ud = new UserData("dave", "pass", 1);
        LocalDateTime custom = LocalDateTime.of(2025, 6, 15, 10, 30);
        ud.setLastAccessDate(custom);
        assertEquals(custom, ud.getLastAccessDate());
    }

    @Test
    void testPutUserData_and_getUserData() {
        UserData ud = new UserData("eve", "pass", 1);
        ud.putUserData("theme", "dark");
        ud.putUserData("language", "en");

        assertEquals("dark", ud.getUserData("theme"));
        assertEquals("en", ud.getUserData("language"));
    }

    @Test
    void testPutUserData_null_removesKey() {
        UserData ud = new UserData("frank", "pass", 1);
        ud.putUserData("pref", "value");
        assertEquals("value", ud.getUserData("pref"));

        ud.putUserData("pref", null);
        assertNull(ud.getUserData("pref"));
    }

    @Test
    void testGetUserData_unknownKey() {
        UserData ud = new UserData("grace", "pass", 1);
        assertNull(ud.getUserData("nonexistent"));
    }
}
