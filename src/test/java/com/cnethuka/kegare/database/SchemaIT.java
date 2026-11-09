package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Versioned migrations against a real database (DESIGN §3.33). */
class SchemaIT {
    @BeforeEach
    void wipe() throws Exception {
        TestDb.assumeAvailable();
        TestDb.wipe();
    }

    @Test
    void migratingTwiceChangesNothingAndIsRecorded() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(TestDb.config("schema"))) {
            assertEquals(Schema.current(), Schema.migrate(pool));
            assertEquals(Schema.current(), Schema.migrate(pool), "safe to run again");
            int rows = pool.transaction(c -> {
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM kegare.schema_version")) {
                    r.next();
                    return r.getInt(1);
                }
            });
            assertEquals(Schema.current(), rows, "one row a version");
        }
    }

    @Test
    void aDatabaseNewerThanTheModRefusesToStart() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(TestDb.config("schema"))) {
            Schema.migrate(pool);
            pool.transaction(c -> {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO kegare.schema_version (version) VALUES (?)")) {
                    p.setInt(1, Schema.current() + 1);
                    p.executeUpdate();
                }
                return null;
            });
            Schema.TooNewException refused = assertThrows(Schema.TooNewException.class, () -> Schema.migrate(pool));
            assertTrue(refused.getMessage().contains("Update Kegare"), "a clear message");
        }
    }

    @Test
    void aFailedMigrationLeavesNothingBehind() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(TestDb.config("schema"))) {
            assertThrows(java.sql.SQLException.class, () -> pool.transaction(c -> {
                try (Statement s = c.createStatement()) {
                    s.execute("CREATE SCHEMA half_done");
                    s.execute("SELECT * FROM no_such_table");
                }
                return null;
            }));
            boolean there = pool.transaction(c -> {
                try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'half_done'")) {
                    r.next();
                    return r.getInt(1) > 0;
                }
            });
            assertTrue(!there, "one transaction: all or nothing");
        }
    }

    @Test
    void jsonbKeepsWhatTheCodecWrote() throws Exception {
        try (ConnectionPool pool = new ConnectionPool(TestDb.config("schema"))) {
            Schema.migrate(pool);
            String json = "{\"big\":1152921504606846979,\"half\":0.5,\"one\":1.0,\"small\":1.0E-4,\"list\":[1,2,3],\"empty\":[],\"text\":\"12\"}";
            String back = pool.transaction(c -> {
                try (PreparedStatement p = c.prepareStatement("SELECT ?::jsonb::text")) {
                    p.setString(1, json);
                    try (ResultSet r = p.executeQuery()) {
                        r.next();
                        return r.getString(1);
                    }
                }
            });
            var tag = (net.minecraft.nbt.CompoundTag) PlayerDataCodec.fromJson(com.google.gson.JsonParser.parseString(back));
            assertEquals(1152921504606846979L, tag.getLong("big"), "exact, through Postgres");
            assertEquals(0.5, tag.getDouble("half"));
            assertEquals(1.0, tag.getDouble("one"));
            assertEquals(1.0E-4, tag.getDouble("small"), 1e-12);
            assertEquals(3, tag.getList("list", net.minecraft.nbt.Tag.TAG_INT).size());
            assertEquals("12", tag.getString("text"));
        }
    }
}
