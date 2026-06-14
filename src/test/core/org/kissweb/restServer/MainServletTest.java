package org.kissweb.restServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for static utility methods of {@link MainServlet} — environment
 * management, path handling, authentication whitelist, and INI parsing.
 * No servlet container required.
 */
class MainServletTest {

    @BeforeEach
    void cleanEnvironment() {
        // Clear known keys to avoid cross-test contamination
        for (String key : new String[]{"TestKey", "IntKey", "AnotherKey",
                "DatabaseType", "MaxWorkerThreads", "SomeSetting"})
            MainServlet.putEnvironment(key, "");
    }

    // ------------------------------------------------------------------
    // putEnvironment / getEnvironment
    // ------------------------------------------------------------------

    @Test
    void testPutAndGetEnvironment() {
        MainServlet.putEnvironment("TestKey", "test-value");
        assertEquals("test-value", MainServlet.getEnvironment("TestKey"));
    }

    @Test
    void testGetEnvironment_absent() {
        // After clearing the key, it should return empty string or null
        Object val = MainServlet.getEnvironment("TestKey");
        assertTrue(val == null || "".equals(val.toString()));
    }

    @Test
    void testGetEnvironment_emptyString() {
        MainServlet.putEnvironment("TestKey", "");
        Object val = MainServlet.getEnvironment("TestKey");
        // Empty string was stored; value depends on implementation
        assertNotNull(val);
    }

    @Test
    void testGetEnvironment_overwriteValue() {
        MainServlet.putEnvironment("TestKey", "first");
        MainServlet.putEnvironment("TestKey", "second");
        assertEquals("second", MainServlet.getEnvironment("TestKey"));
    }

    // ------------------------------------------------------------------
    // getEnvironmentInt (public single-arg version)
    // ------------------------------------------------------------------

    @Test
    void testGetEnvironmentInt_valid() {
        MainServlet.putEnvironment("IntKey", "42");
        Integer val = MainServlet.getEnvironmentInt("IntKey");
        assertNotNull(val);
        assertEquals(42, val);
    }

    @Test
    void testGetEnvironmentInt_emptyString() {
        MainServlet.putEnvironment("IntKey", "");
        Integer val = MainServlet.getEnvironmentInt("IntKey");
        assertNull(val);
    }

    @Test
    void testGetEnvironmentInt_invalid() {
        MainServlet.putEnvironment("IntKey", "not-a-number");
        assertThrows(NumberFormatException.class,
                () -> MainServlet.getEnvironmentInt("IntKey"));
    }

    // ------------------------------------------------------------------
    // setApplicationPath / getApplicationPath
    // ------------------------------------------------------------------

    @Test
    void testSetAndGetApplicationPath() {
        String original = MainServlet.getApplicationPath();
        try {
            MainServlet.setApplicationPath("/tmp/test-app/");
            assertEquals("/tmp/test-app/", MainServlet.getApplicationPath());
        } finally {
            if (original != null)
                MainServlet.setApplicationPath(original);
        }
    }

    // ------------------------------------------------------------------
    // setRootPath / getRootPath
    // ------------------------------------------------------------------

    @Test
    void testSetAndGetRootPath() {
        String original = MainServlet.getRootPath();
        try {
            MainServlet.setRootPath("/opt/myapp");
            assertEquals("/opt/myapp", MainServlet.getRootPath());
        } finally {
            if (original != null)
                MainServlet.setRootPath(original);
        }
    }

    // ------------------------------------------------------------------
    // allowWithoutAuthentication / shouldAllowWithoutAuthentication
    // ------------------------------------------------------------------

    @Test
    void testAllowedWithoutAuthentication() {
        MainServlet.allowWithoutAuthentication("MyService", "publicMethod");
        assertTrue(MainServlet.shouldAllowWithoutAuthentication("MyService", "publicMethod"));
    }

    @Test
    void testNotAllowedByDefault() {
        assertFalse(MainServlet.shouldAllowWithoutAuthentication("UnknownService", "unknownMethod"));
    }

    // ------------------------------------------------------------------
    // readIniFile
    // ------------------------------------------------------------------

    @Test
    void testReadIniFile_loadsSettings() throws IOException {
        Path tempIni = Files.createTempFile("test-app", ".ini");
        try {
            String content = "[General]\nDatabaseType = PostgreSQL\nMaxWorkerThreads = 20\n";
            Files.writeString(tempIni, content);

            MainServlet.readIniFile(tempIni.toString(), "General");

            Object dbType = MainServlet.getEnvironment("DatabaseType");
            assertNotNull(dbType);
            assertEquals("PostgreSQL", dbType.toString());

            Object threads = MainServlet.getEnvironment("MaxWorkerThreads");
            assertNotNull(threads);
            assertEquals("20", threads.toString());
        } finally {
            Files.deleteIfExists(tempIni);
        }
    }

    @Test
    void testReadIniFile_missingFile() {
        // Should not throw for a non-existent file — silently ignores
        assertDoesNotThrow(() ->
                MainServlet.readIniFile("/nonexistent/path/app.ini", "General"));
    }
}
