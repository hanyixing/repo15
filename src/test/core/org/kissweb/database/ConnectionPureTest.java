package org.kissweb.database;

import org.junit.jupiter.api.Test;
import org.kissweb.database.Connection.ConnectionType;

import java.sql.Timestamp;
import java.time.*;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for pure static methods of {@link Connection} that do not
 * require a live JDBC connection: makeConnectionString, getDriverName,
 * toTimestamp, toDate, and fixDate.
 */
class ConnectionPureTest {

    // ==================================================================
    // makeConnectionString
    // ==================================================================

    @Test
    void testMakeConnectionString_PostgreSQL() {
        String cs = Connection.makeConnectionString(
                ConnectionType.PostgreSQL, "dbhost", 5433, "mydb", "user1", "pass1");
        assertEquals("jdbc:postgresql://dbhost:5433/mydb?user=user1&password=pass1", cs);
    }

    @Test
    void testMakeConnectionString_PostgreSQL_defaults() {
        String cs = Connection.makeConnectionString(
                ConnectionType.PostgreSQL, null, null, "mydb", "u", "p");
        assertEquals("jdbc:postgresql://localhost:5432/mydb?user=u&password=p", cs);
    }

    @Test
    void testMakeConnectionString_MicrosoftServer_integrated() {
        String cs = Connection.makeConnectionString(
                ConnectionType.MicrosoftServer, "sqlhost", null, "mydb", null, null);
        assertEquals("jdbc:sqlserver://sqlhost:1433;databaseName=mydb;integratedSecurity=true;", cs);
    }

    @Test
    void testMakeConnectionString_MicrosoftServer_withAuth() {
        String cs = Connection.makeConnectionString(
                ConnectionType.MicrosoftServer, null, 1434, "mydb", "sa", "secret");
        assertEquals("jdbc:sqlserver://localhost:1434;databaseName=mydb;user=sa;password=secret;", cs);
    }

    @Test
    void testMakeConnectionString_MySQL() {
        String cs = Connection.makeConnectionString(
                ConnectionType.MySQL, "mysqlhost", null, "mydb", "root", "pass");
        assertEquals("jdbc:mysql://mysqlhost:3306/mydb?user=root&password=pass", cs);
    }

    @Test
    void testMakeConnectionString_SQLite() {
        String cs = Connection.makeConnectionString(
                ConnectionType.SQLite, null, null, "mydata.db", null, null);
        assertEquals("jdbc:sqlite:mydata.db", cs);
    }

    @Test
    void testMakeConnectionString_SQLite_withPassword() {
        String cs = Connection.makeConnectionString(
                ConnectionType.SQLite, null, null, "mydata.db", null, "secret");
        assertEquals("jdbc:sqlite:mydata.db;Password=secret;", cs);
    }

    @Test
    void testMakeConnectionString_Oracle() {
        String cs = Connection.makeConnectionString(
                ConnectionType.Oracle, "orahost", null, "orcl", "scott", "tiger");
        assertEquals("jdbc:oracle:thin:scott/tiger@//orahost:1521/orcl", cs);
    }

    @Test
    void testMakeConnectionString_Oracle_customPort() {
        String cs = Connection.makeConnectionString(
                ConnectionType.Oracle, "orahost", 1522, "orcl", "scott", "tiger");
        assertEquals("jdbc:oracle:thin:scott/tiger@//orahost:1522/orcl", cs);
    }

    // ==================================================================
    // getDriverName
    // ==================================================================

    @Test
    void testGetDriverName_PostgreSQL() {
        assertEquals("org.postgresql.Driver", Connection.getDriverName(ConnectionType.PostgreSQL));
    }

    @Test
    void testGetDriverName_MicrosoftServer() {
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver",
                Connection.getDriverName(ConnectionType.MicrosoftServer));
    }

    @Test
    void testGetDriverName_MySQL() {
        assertEquals("com.mysql.jdbc.Driver", Connection.getDriverName(ConnectionType.MySQL));
    }

    @Test
    void testGetDriverName_SQLite() {
        assertEquals("org.sqlite.JDBC", Connection.getDriverName(ConnectionType.SQLite));
    }

    @Test
    void testGetDriverName_Oracle() {
        assertEquals("oracle.jdbc.driver.OracleDriver", Connection.getDriverName(ConnectionType.Oracle));
    }

    // ==================================================================
    // toTimestamp
    // ==================================================================

    @Test
    void testToTimestamp_fromDate() {
        Date d = new Date(1700000000000L);
        Timestamp ts = Connection.toTimestamp(d);
        assertNotNull(ts);
        assertEquals(1700000000000L, ts.getTime());
    }

    @Test
    void testToTimestamp_fromNull() {
        assertNull(Connection.toTimestamp((Date) null));
    }

    @Test
    void testToTimestamp_fromInt() {
        // 20260115 = January 15, 2026
        Timestamp ts = Connection.toTimestamp(20260115);
        assertNotNull(ts);
    }

    @Test
    void testToTimestamp_fromZero() {
        assertNull(Connection.toTimestamp(0));
    }

    // ==================================================================
    // toDate
    // ==================================================================

    @Test
    void testToDate_fromDate() {
        Date d = new Date(1700000000000L);
        java.sql.Date sqlDate = Connection.toDate(d);
        assertNotNull(sqlDate);
        assertEquals(1700000000000L, sqlDate.getTime());
    }

    @Test
    void testToDate_fromNull() {
        assertNull(Connection.toDate((Date) null));
    }

    @Test
    void testToDate_fromInt() {
        java.sql.Date sqlDate = Connection.toDate(20260115);
        assertNotNull(sqlDate);
    }

    @Test
    void testToDate_fromZero() {
        assertNull(Connection.toDate(0));
    }

    // ==================================================================
    // fixDate
    // ==================================================================

    @Test
    void testFixDate_null() {
        assertNull(Connection.fixDate(null));
    }

    @Test
    void testFixDate_jdbcTimestamp() {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        Object result = Connection.fixDate(ts);
        assertSame(ts, result);
    }

    @Test
    void testFixDate_jdbcDate() {
        java.sql.Date d = new java.sql.Date(System.currentTimeMillis());
        Object result = Connection.fixDate(d);
        assertTrue(result instanceof Timestamp);
    }

    @Test
    void testFixDate_jdbcTime() {
        java.sql.Time t = new java.sql.Time(System.currentTimeMillis());
        Object result = Connection.fixDate(t);
        assertSame(t, result);
    }

    @Test
    void testFixDate_utilDate() {
        Date d = new Date(1700000000000L);
        Object result = Connection.fixDate(d);
        assertTrue(result instanceof Timestamp);
        assertEquals(1700000000000L, ((Timestamp) result).getTime());
    }

    @Test
    void testFixDate_calendar() {
        Calendar cal = new GregorianCalendar();
        cal.setTimeInMillis(1700000000000L);
        Object result = Connection.fixDate(cal);
        assertTrue(result instanceof Timestamp);
        assertEquals(1700000000000L, ((Timestamp) result).getTime());
    }

    @Test
    void testFixDate_instant() {
        Instant inst = Instant.ofEpochMilli(1700000000000L);
        Object result = Connection.fixDate(inst);
        assertTrue(result instanceof Timestamp);
        assertEquals(1700000000000L, ((Timestamp) result).getTime());
    }

    @Test
    void testFixDate_localDateTime() {
        LocalDateTime ldt = LocalDateTime.of(2026, 1, 15, 12, 0, 0);
        Object result = Connection.fixDate(ldt);
        assertTrue(result instanceof Timestamp);
    }

    @Test
    void testFixDate_localDate() {
        LocalDate ld = LocalDate.of(2026, 1, 15);
        Object result = Connection.fixDate(ld);
        assertTrue(result instanceof Timestamp);
    }

    @Test
    void testFixDate_localTime() {
        LocalTime lt = LocalTime.of(14, 30);
        Object result = Connection.fixDate(lt);
        assertTrue(result instanceof java.sql.Time);
    }

    @Test
    void testFixDate_unknownType() {
        String s = "not a date";
        Object result = Connection.fixDate(s);
        assertSame(s, result);
    }

    @Test
    void testFixDate_integer() {
        Integer i = 42;
        Object result = Connection.fixDate(i);
        assertSame(i, result);
    }
}
