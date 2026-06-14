package org.kissweb.restServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MainServlet}'s application-configuration API
 * ({@code putEnvironment} / {@code getEnvironment} / {@code getEnvironmentInt}).
 * <br><br>
 * These static accessors are the framework-wide mechanism for reading values
 * from {@code application.ini} (and for tests/services to inject configuration),
 * so their contract is exercised directly without standing up a servlet
 * container. All keys used here are uniquely named and cleared in teardown so
 * they do not leak into other test classes sharing the JVM.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MainServletTest {

    private static final String STR_KEY     = "KissTest_StrKey";
    private static final String OVERWRITE   = "KissTest_Overwrite";
    private static final String OBJ_KEY     = "KissTest_ObjKey";
    private static final String INT_KEY     = "KissTest_IntKey";
    private static final String MISSING_KEY = "KissTest_MissingKey_NeverSet";

    @AfterAll
    void clearKeys() {
        for (String k : new String[]{STR_KEY, OVERWRITE, OBJ_KEY, INT_KEY})
            MainServlet.putEnvironment(k, "");
    }

    // ------------------------------------------------------------------
    // putEnvironment / getEnvironment
    // ------------------------------------------------------------------

    @Test
    void putThenGet_returnsStoredValue() {
        MainServlet.putEnvironment(STR_KEY, "hello");
        assertEquals("hello", MainServlet.getEnvironment(STR_KEY));
    }

    @Test
    void getUnknownKey_returnsNull() {
        assertNull(MainServlet.getEnvironment(MISSING_KEY));
    }

    @Test
    void put_overwritesPreviousValue() {
        MainServlet.putEnvironment(OVERWRITE, "first");
        MainServlet.putEnvironment(OVERWRITE, "second");
        assertEquals("second", MainServlet.getEnvironment(OVERWRITE));
    }

    @Test
    void put_storesArbitraryObjectType() {
        Integer boxed = 12345;
        MainServlet.putEnvironment(OBJ_KEY, boxed);
        Object back = MainServlet.getEnvironment(OBJ_KEY);
        assertSame(boxed, back);
    }

    // ------------------------------------------------------------------
    // getEnvironmentInt
    // ------------------------------------------------------------------

    @Test
    void getEnvironmentInt_parsesNumericString() {
        MainServlet.putEnvironment(INT_KEY, "42");
        assertEquals(Integer.valueOf(42), MainServlet.getEnvironmentInt(INT_KEY));
    }

    @Test
    void getEnvironmentInt_trimsSurroundingWhitespace() {
        MainServlet.putEnvironment(INT_KEY, "   7   ");
        assertEquals(Integer.valueOf(7), MainServlet.getEnvironmentInt(INT_KEY));
    }

    @Test
    void getEnvironmentInt_returnsNullForMissingKey() {
        assertNull(MainServlet.getEnvironmentInt(MISSING_KEY));
    }

    @Test
    void getEnvironmentInt_returnsNullForEmptyValue() {
        MainServlet.putEnvironment(INT_KEY, "");
        assertNull(MainServlet.getEnvironmentInt(INT_KEY));
    }

    @Test
    void getEnvironmentInt_returnsNullForWhitespaceOnlyValue() {
        MainServlet.putEnvironment(INT_KEY, "    ");
        assertNull(MainServlet.getEnvironmentInt(INT_KEY));
    }

    @Test
    void getEnvironmentInt_throwsOnNonNumericValue() {
        MainServlet.putEnvironment(INT_KEY, "not-a-number");
        assertThrows(NumberFormatException.class, () -> MainServlet.getEnvironmentInt(INT_KEY));
    }
}
