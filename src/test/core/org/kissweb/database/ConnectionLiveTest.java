package org.kissweb.database;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kissweb.database.Connection.ConnectionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Connection} methods that require a live database
 * connection — uses SQLite in-memory.
 */
class ConnectionLiveTest {

    private static Connection db;

    @BeforeAll
    static void openDb() throws Exception {
        db = new Connection(ConnectionType.SQLite, "jdbc:sqlite::memory:");
    }

    @AfterAll
    static void closeDb() throws Exception {
        if (db != null)
            db.close();
    }

    // ------------------------------------------------------------------
    // limit (package-private, accessible from same package)
    // ------------------------------------------------------------------

    @Test
    void testLimit_SQLite() {
        String result = db.limit(10, "select * from users");
        assertEquals("select * from users limit 10", result);
    }

    @Test
    void testLimit_zeroMeansAll() {
        String sql = "select * from users";
        assertEquals(sql, db.limit(0, sql));
    }

    @Test
    void testLimit_negativeMeansAll() {
        String sql = "select * from users";
        assertEquals(sql, db.limit(-1, sql));
    }

    // ------------------------------------------------------------------
    // page (package-private)
    // ------------------------------------------------------------------

    @Test
    void testPage_SQLite_firstPage() {
        String result = db.page(0, 10, "select * from users");
        assertEquals("select * from users limit 10", result);
    }

    @Test
    void testPage_SQLite_secondPage() {
        String result = db.page(1, 10, "select * from users");
        assertEquals("select * from users limit 10 offset 10", result);
    }

    @Test
    void testPage_SQLite_thirdPage() {
        String result = db.page(2, 10, "select * from users");
        assertEquals("select * from users limit 10 offset 20", result);
    }

    @Test
    void testPage_zeroMaxMeansAll() {
        String sql = "select * from users";
        assertEquals(sql, db.page(1, 0, sql));
    }

    // ------------------------------------------------------------------
    // isOpen / close
    // ------------------------------------------------------------------

    @Test
    void testIsOpen_trueAfterOpen() {
        assertTrue(db.isOpen());
    }

    @Test
    void testGetDBType() {
        assertEquals(ConnectionType.SQLite, db.getDBType());
    }

    // ------------------------------------------------------------------
    // newRecord
    // ------------------------------------------------------------------

    @Test
    void testNewRecord_createsInstance() {
        Record rec = db.newRecord("test_table");
        assertNotNull(rec);
        assertEquals("test_table", rec.getTableName());
    }

    // ------------------------------------------------------------------
    // execute / fetchOne / fetchAll / exists
    // ------------------------------------------------------------------

    @Test
    void testExecuteAndFetchOne() throws Exception {
        db.execute("CREATE TABLE IF NOT EXISTS test_live (id INTEGER PRIMARY KEY, name TEXT)");
        db.execute("INSERT INTO test_live (id, name) VALUES (?, ?)", 1, "Alice");
        db.commit();

        Record rec = db.fetchOne("SELECT * FROM test_live WHERE id = ?", 1);
        assertNotNull(rec);
        assertEquals("Alice", rec.getString("name"));
    }

    @Test
    void testFetchAll_multipleRows() throws Exception {
        db.execute("CREATE TABLE IF NOT EXISTS test_multi (id INTEGER PRIMARY KEY, val TEXT)");
        db.execute("INSERT INTO test_multi (id, val) VALUES (?, ?)", 1, "a");
        db.execute("INSERT INTO test_multi (id, val) VALUES (?, ?)", 2, "b");
        db.execute("INSERT INTO test_multi (id, val) VALUES (?, ?)", 3, "c");
        db.commit();

        List<Record> rows = db.fetchAll("SELECT * FROM test_multi ORDER BY id");
        assertEquals(3, rows.size());
        assertEquals("a", rows.get(0).getString("val"));
        assertEquals("c", rows.get(2).getString("val"));
    }

    @Test
    void testExists_trueWhenRowPresent() throws Exception {
        db.execute("CREATE TABLE IF NOT EXISTS test_exists (id INTEGER PRIMARY KEY)");
        db.execute("INSERT INTO test_exists (id) VALUES (?)", 1);
        db.commit();

        assertTrue(db.exists("SELECT * FROM test_exists WHERE id = ?", 1));
    }

    @Test
    void testExists_falseWhenEmpty() throws Exception {
        db.execute("CREATE TABLE IF NOT EXISTS test_empty (id INTEGER PRIMARY KEY)");
        db.commit();

        assertFalse(db.exists("SELECT * FROM test_empty WHERE id = ?", 999));
    }

    @Test
    void testCommitAndRollback() throws Exception {
        db.execute("CREATE TABLE IF NOT EXISTS test_txn (id INTEGER PRIMARY KEY, val TEXT)");
        db.commit();

        db.execute("INSERT INTO test_txn (id, val) VALUES (?, ?)", 1, "committed");
        db.commit();

        Record rec = db.fetchOne("SELECT * FROM test_txn WHERE id = ?", 1);
        assertNotNull(rec);
        assertEquals("committed", rec.getString("val"));

        // Insert then rollback
        db.execute("INSERT INTO test_txn (id, val) VALUES (?, ?)", 2, "rolled_back");
        db.rollback();

        assertFalse(db.exists("SELECT * FROM test_txn WHERE id = ?", 2));
    }
}
