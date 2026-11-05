package com.cnethuka.kegare;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Helpers for tests on the testframework's throwaway server. */
public final class TestServer {
    private TestServer() {}

    /** A fake player in the overworld with a stable UUID per name. */
    public static ServerPlayer player(MinecraftServer server, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("kegare-test:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(server.overworld(), new GameProfile(uuid, name));
    }

    /** The testframework's server defines FLAT dimensions but never loads them; create them as startup does. */
    public static void ensureOverworld(MinecraftServer server) {
        if (server.overworld() != null) return;
        try {
            Method createLevels = MinecraftServer.class.getDeclaredMethod("createLevels", ChunkProgressListener.class);
            createLevels.setAccessible(true);
            createLevels.invoke(server, new ChunkProgressListener() {
                @Override public void updateSpawnPos(ChunkPos center) {}
                @Override public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}
                @Override public void start() {}
                @Override public void stop() {}
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't create the test server's levels", e);
        }
    }

    /** Tests run on the server thread, so the world never ticks on its own: move game time forward by hand. */
    public static void advanceGameTime(MinecraftServer server, int ticks) {
        ServerLevelData data = (ServerLevelData) server.overworld().getLevelData();
        data.setGameTime(data.getGameTime() + ticks);
    }

    public static void onServerThread(MinecraftServer server, Runnable task) throws Exception {
        try {
            server.submit(task).get(5, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) throw error;
            if (e.getCause() instanceof Exception exception) throw exception;
            throw e;
        }
    }
}
