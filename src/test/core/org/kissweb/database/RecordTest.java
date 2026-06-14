package org.kissweb.database;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kissweb.database.Connection.ConnectionType;
import org.kissweb.json.JSONObject;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Record} — CRUD operations, typed getters/setters,
 * JSON serialization, and utility methods. Uses SQLite in-memory.
 */
class RecordTest {

    private static Connection db;

    @BeforeAll
    static void openDb() throws Exception {
        db = new Connection(ConnectionType.SQLite, "jdbc:sqlite::memory:");
        db.execute("CREATE TABLE test_rec (id INTEGER PRIMARY KEY, name TEXT, age INTEGER, salary REAL, active INTEGER)");
        db.commit();
    }

    @AfterAll
    static void closeDb() throws Exception {
        if (db != null)
            db.close();
    }

    // ------------------------------------------------------------------
    // set / get typed accessors
    // ------------------------------------------------------------------

    @Test
    void testSet_and_getString() {
        Record rec = db.newRecord("test_rec");
        rec.set("name", "Alice");
        assertEquals("Alice", rec.getString("name"));
    }

    @Test
    void testSet_and_getInt() {
        Record rec = db.newRecord("test_rec");
        rec.set("age", 30);
        assertEquals(30, rec.getInt("age"));
    }

    @Test
    void testSet_and_getLong() {
        Record rec = db.newRecord("test_rec");
        rec.set("age", 100000000000L);
        assertEquals(100000000000L, rec.getLong("age"));
    }

    @Test
    void testSet_and_getDouble() {
        Record rec = db.newRecord("test_rec");
        rec.set("salary", 50000.75);
        assertEquals(50000.75, rec.getDouble("salary"), 0.001);
    }

    @Test
    void testSetNull() {
        Record rec = db.newRecord("test_rec");
        rec.set("name", null);
        assertNull(rec.get("name"));
    }

    // ------------------------------------------------------------------
    // addRecord / update / delete — full CRUD cycle
    // ------------------------------------------------------------------

    @Test
    void testAddRecord_persistsRow() throws Exception {
        Record rec = db.newRecord("test_rec");
        rec.set("id", 100);
        rec.set("name", "Bob");
        rec.set("age", 25);
        rec.set("salary", 45000.0);
        rec.set("active", 1);
        rec.addRecord();
        db.commit();

        Record fetched = db.fetchOne("SELECT * FROM test_rec WHERE id = ?", 100);
        assertNotNull(fetched);
        assertEquals("Bob", fetched.getString("name"));
        assertEquals(25, fetched.getInt("age"));
    }

    @Test
    void testUpdate_modifiesRow() throws Exception {
        Record rec = db.newRecord("test_rec");
        rec.set("id", 200);
        rec.set("name", "Charlie");
        rec.set("age", 30);
        rec.addRecord();
        db.commit();

        Record fetched = db.fetchOne("SELECT * FROM test_rec WHERE id = ?", 200);
        assertNotNull(fetched);
        fetched.set("name", "Charles");
        fetched.update();
        db.commit();

        Record updated = db.fetchOne("SELECT * FROM test_rec WHERE id = ?", 200);
        assertEquals("Charles", updated.getString("name"));
    }

    @Test
    void testDelete_removesRow() throws Exception {
        Record rec = db.newRecord("test_rec");
        rec.set("id", 300);
        rec.set("name", "ToDelete");
        rec.addRecord();
        db.commit();

        assertTrue(db.exists("SELECT * FROM test_rec WHERE id = ?", 300));

        Record fetched = db.fetchOne("SELECT * FROM test_rec WHERE id = ?", 300);
        assertNotNull(fetched);
        fetched.delete();
        db.commit();

        assertFalse(db.exists("SELECT * FROM test_rec WHERE id = ?", 300));
    }

    // ------------------------------------------------------------------
    // columnExists
    // ------------------------------------------------------------------

    @Test
    void testColumnExists_true() {
        Record rec = db.newRecord("test_rec");
        rec.set("name", "test");
        assertTrue(rec.columnExists("name"));
    }

    @Test
    void testColumnExists_false() {
        Record rec = db.newRecord("test_rec");
        assertFalse(rec.columnExists("nonexistent_column"));
    }

    // ------------------------------------------------------------------
    // clear
    // ------------------------------------------------------------------

    @Test
    void testClear_resetsValues() {
        Record rec = db.newRecord("test_rec");
        rec.set("name", "Alice");
        rec.set("age", 30);
        rec.clear();
        assertFalse(rec.columnExists("name"));
        assertFalse(rec.columnExists("age"));
    }

    // ------------------------------------------------------------------
    // copy
    // ------------------------------------------------------------------

    @Test
    void testCopy_createsIndependentCopy() {
        Record src = db.newRecord("test_rec");
        src.set("name", "Alice");
        src.set("age", 30);

        Record dst = db.newRecord("test_rec");
        dst.copy(src);

        assertEquals("Alice", dst.getString("name"));
        assertEquals(30, dst.getInt("age"));

        // Modifying source should not affect copy
        src.set("name", "Bob");
        assertEquals("Alice", dst.getString("name"));
    }

    // ------------------------------------------------------------------
    // toJSON
    // ------------------------------------------------------------------

    @Test
    void testToJSON_serializesAllColumns() {
        Record rec = db.newRecord("test_rec");
        rec.set("id", 1);
        rec.set("name", "Alice");
        rec.set("age", 30);

        JSONObject json = rec.toJSON();
        assertNotNull(json);
        assertEquals(1, json.getInt("id", -1));
        assertEquals("Alice", json.getString("name", null));
        assertEquals(30, json.getInt("age", -1));
    }

    // ------------------------------------------------------------------
    // getTableName / getConnection
    // ------------------------------------------------------------------

    @Test
    void testGetTableName() {
        Record rec = db.newRecord("my_table");
        assertEquals("my_table", rec.getTableName());
    }

    @Test
    void testGetConnection() {
        Record rec = db.newRecord("my_table");
        assertSame(db, rec.getConnection());
    }
}
