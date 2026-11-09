package com.cnethuka.kegare.database;

import com.cnethuka.kegare.Kegare;
import java.io.IOException;
import java.sql.SQLException;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Kegare's optional Postgres storage (DESIGN §3.33). Off by default: then this is never more than a null check, and no connection is ever opened.
 * On a client, even a singleplayer one, it's never on.
 */
public final class KegareDatabase {
    @Nullable
    private static volatile KegareDatabase current;

    private final DatabaseConfig config;
    private final ConnectionPool pool;

    KegareDatabase(DatabaseConfig config) {
        this.config = config;
        this.pool = new ConnectionPool(config);
    }

    @Nullable
    public static KegareDatabase current() {
        return current;
    }

    public static boolean isOn() {
        return current != null;
    }

    /** Reads the settings, writing the file if it isn't there; starts the storage if it's switched on and this is a dedicated server. */
    public static void serverStarting() {
        if (!FMLEnvironment.dist.isDedicatedServer()) return;
        DatabaseConfig config;
        try {
            config = DatabaseConfig.load(FMLPaths.CONFIGDIR.get(), System::getenv);
        } catch (IOException | RuntimeException e) {
            Kegare.LOGGER.error("Couldn't read {}. Kegare's storage stays in the world.", DatabaseConfig.FILE_NAME);
            return;
        }
        if (!config.enabled()) return;
        KegareDatabase database = new KegareDatabase(config);
        current = database;
        database.start();
    }

    public static void serverStopped() {
        KegareDatabase database = current;
        if (database == null) return;
        current = null;
        database.pool.close();
    }

    private void start() {
        try {
            int version = Schema.migrate(pool);
            Kegare.LOGGER.info("Kegare's storage is on Postgres, as {}, schema version {}", config.serverId(), version);
        } catch (SQLException e) {
            Kegare.LOGGER.error("Kegare can't start its storage: {}", DatabaseLog.describe(e, config));
        }
    }

    /** A player's file is about to be written. With storage on, Kegare's keys don't go in it. */
    public static void beforePlayerFileWritten(CompoundTag file) {
        if (isOn()) PlayerDataCodec.stripFromPlayerFile(file);
    }
}
