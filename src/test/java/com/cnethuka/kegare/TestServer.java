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

    /** A fake player that keeps REAL advancements (NeoForge gives fake players throwaway ones). */
    public static ServerPlayer playerWithAdvancements(MinecraftServer server, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("kegare-test:" + name).getBytes(StandardCharsets.UTF_8));
        return new net.neoforged.neoforge.common.util.FakePlayer(server.overworld(), new GameProfile(uuid, name)) {
            @Override
            public boolean isFakePlayer() {
                return false;
            }
        };
    }

    /** A test player that can be hurt and hurt others (NeoForge's fake players can't), with real advancements. */
    public static ServerPlayer damageable(MinecraftServer server, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("kegare-test:" + name).getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = new net.neoforged.neoforge.common.util.FakePlayer(server.overworld(), new GameProfile(uuid, name)) {
            @Override
            public boolean isFakePlayer() {
                return false;
            }

            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }

            @Override
            public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
                return true;
            }

            /** FakePlayer's death does nothing at all; a real player's first fires LivingDeathEvent (cancellable). */
            @Override
            public void die(net.minecraft.world.damagesource.DamageSource source) {
                if (net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source)) return;
                dead = true;
            }
        };
        try {
            java.lang.reflect.Field spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return player;
    }

    /** Takes every test player out of the overworld again, so one test's players never get in the way of the next. */
    public static void removePlayers(MinecraftServer server) {
        if (server.overworld() == null) return; // nothing in this test touched the world
        for (ServerPlayer p : java.util.List.copyOf(server.overworld().players())) {
            server.overworld().removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    public static boolean hasAdvancement(MinecraftServer server, ServerPlayer player, net.minecraft.resources.ResourceLocation id) {
        var holder = server.getAdvancements().get(id);
        if (holder == null) throw new IllegalStateException("advancement " + id + " isn't loaded");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** The testframework's server defines FLAT dimensions but never loads them; create them as startup does. */
    public static void ensureOverworld(MinecraftServer server) {
        // Tests of an ability's own behaviour start from a fresh player, who has every ability unlocked here. The tests
        // of the unlocks themselves turn this back on (see WithLocks).
        com.cnethuka.kegare.progression.ProgressionConfig.REQUIRE_REFINEMENT.set(false);
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
