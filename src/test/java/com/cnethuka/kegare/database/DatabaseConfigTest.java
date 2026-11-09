package com.cnethuka.kegare.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.config.KegareServerConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** The settings file (DESIGN §3.33): off by default, and nothing in it can ever reach a client. */
@ExtendWith(EphemeralTestServerProvider.class)
class DatabaseConfigTest {
    @Test
    void theFileIsGeneratedOffWithAnEmptyPassword(@TempDir Path dir) throws Exception {
        DatabaseConfig config = DatabaseConfig.load(dir, name -> null);
        assertTrue(Files.exists(dir.resolve(DatabaseConfig.FILE_NAME)));
        assertFalse(config.enabled(), "off by default");
        assertEquals("", config.password(), "no password");
        assertFalse(config.serverId().isEmpty());
        assertEquals(config.serverId(), DatabaseConfig.load(dir, name -> null).serverId(), "the file's kept: the same server next time");
    }

    @Test
    void theEnvironmentVariableWins(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(DatabaseConfig.FILE_NAME), "enabled = true\npassword = \"from-file\"\n");
        assertEquals("from-file", DatabaseConfig.load(dir, name -> null).password());
        assertEquals("from-env", DatabaseConfig.load(dir, name -> name.equals(DatabaseConfig.PASSWORD_ENV) ? "from-env" : null).password());
    }

    @Test
    void thePasswordIsNotInToStringOrErrorText() {
        DatabaseConfig config = new DatabaseConfig(true, "jdbc:postgresql://db/kegare?password=hunter2", "kegare", "s3cret-value", 1, "a", 1, 1, 1, 1, 1, 1, 1, 1);
        assertFalse(config.toString().contains("s3cret-value"), "the record's own string");
        assertFalse(config.toString().contains("hunter2"), "nor the url");
        String described = DatabaseLog.describe(new java.sql.SQLException("no: s3cret-value at jdbc:postgresql://db/kegare?password=hunter2&x=1"), config);
        assertFalse(described.contains("s3cret-value"), described);
        assertFalse(described.contains("hunter2"), described);
    }

    private static void keys(UnmodifiableConfig config, String prefix, List<String> out) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            if (entry.getValue() instanceof Config inner) keys(inner, prefix + entry.getKey() + ".", out);
            else out.add((prefix + entry.getKey()).toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void thePasswordCantBeInAnySyncedConfig() {
        // A SERVER config goes to every client that joins. Our settings file must never be one, and the synced spec can't hold its keys.
        assertFalse(ModConfigs.getConfigSet(ModConfig.Type.SERVER).isEmpty(), "setup: the server config is there");
        for (String file : ModConfigs.getFileMap().keySet()) {
            assertFalse(file.contains("database"), file + " is registered: the settings file is read by our own code");
        }
        List<String> keys = new ArrayList<>();
        keys(KegareServerConfig.SPEC.getSpec(), "", keys);
        assertFalse(keys.isEmpty(), "setup: the synced config has keys");
        for (String key : keys) {
            assertFalse(key.contains("password") || key.contains("jdbc") || key.endsWith(".username") || key.endsWith(".url") || key.contains("serverid"), key + " is synced to clients");
        }
    }

    @Test
    void offMeansNoStorageAndNoConnection() {
        assertNull(KegareDatabase.current(), "off in a server with no settings saying otherwise");
        long before = ConnectionPool.OPENED.get();
        KegareDatabase.serverStarting(); // writes the file if it has to; it's off, so nothing else
        assertNull(KegareDatabase.current(), "still off");
        assertEquals(before, ConnectionPool.OPENED.get(), "no connection was opened");
    }
}
