package org.kissweb.database;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kissweb.database.Connection.ConnectionType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Cursor} — iteration, typed getters, and metadata.
 * Uses SQLite in-memory.
 */
class CursorTest {

    private static Connection db;

    @BeforeAll
    static void openDb() throws Exception {
        db = new Connection(ConnectionType.SQLite, "jdbc:sqlite::memory:");
        db.execute("CREATE TABLE test_cursor (id INTEGER PRIMARY KEY, name TEXT, score REAL, active INTEGER)");
        db.execute("INSERT INTO test_cursor VALUES (?, ?, ?, ?)", 1, "Alice", 95.5, 1);
        db.execute("INSERT INTO test_cursor VALUES (?, ?, ?, ?)", 2, "Bob", 87.3, 0);
        db.execute("INSERT INTO test_cursor VALUES (?, ?, ?, ?)", 3, "Charlie", 92.1, 1);
        db.commit();
    }

    @AfterAll
    static void closeDb() throws Exception {
        if (db != null)
            db.close();
    }

    @Test
    void testIsNext_and_next_basicIteration() throws Exception {
        try (Cursor cursor = db.query("SELECT * FROM test_cursor ORDER BY id")) {
            int count = 0;
            while (cursor.isNext()) {
                count++;
                cursor.next();
            }
            assertEquals(3, count);
        }
    }

    @Test
    void testGetStar_typeConverters() throws Exception {
        try (Cursor cursor = db.query("SELECT * FROM test_cursor WHERE id = ?", 1)) {
            assertTrue(cursor.isNext());
            cursor.next();
            assertEquals("Alice", cursor.getString("name"));
            assertEquals(1, cursor.getInt("id"));
            assertEquals(95.5, cursor.getDouble("score"), 0.001);
        }
    }

    @Test
    void testGetLong() throws Exception {
        try (Cursor cursor = db.query("SELECT * FROM test_cursor WHERE id = ?", 2)) {
            assertTrue(cursor.isNext());
            cursor.next();
            assertEquals(2L, cursor.getLong("id"));
        }
    }

    @Test
    void testEmptyResultSet_isNextReturnsFalse() throws Exception {
        try (Cursor cursor = db.query("SELECT * FROM test_cursor WHERE id = ?", 999)) {
            assertFalse(cursor.isNext());
        }
    }

    @Test
    void testSize() throws Exception {
        try (Cursor cursor = db.query("SELECT * FROM test_cursor")) {
            assertEquals(3, cursor.size());
        }
    }
}
