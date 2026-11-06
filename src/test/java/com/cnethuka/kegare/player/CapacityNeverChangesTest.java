package com.cnethuka.kegare.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.item.PurificationTalismanItem;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Malice capacity is fixed at birth: only birth, {@code /kegare rebirth} and {@code /kegare malice capacity} may change
 * it. This drives every reroll path through the real server code, many times, on a throwaway server, and checks that
 * capacity, tier and the Severed flag never move:
 * <ul>
 *   <li>Purification Talisman (the confirm handler the client's Reroll button triggers)</li>
 *   <li>admin reroll ({@code /kegare innate reroll})</li>
 *   <li>replacement roll (login with an innate technique an update removed)</li>
 * </ul>
 */
@ExtendWith(EphemeralTestServerProvider.class)
class CapacityNeverChangesTest {
    private static final int REROLLS_PER_PATH = 200;
    /** Admin-set capacities, including values between tiers (120) and at tier edges (140). */
    private static final int[] FORCED_CAPACITIES = {1, 50, 120, 140, 160, 250, 10_000};
    private static final int ROLLED_BIRTHS = 6;
    private static final String REMOVED_INNATE_TECHNIQUE = "{\"innate_technique\": \"kegare:removed_by_update\"}";

    private record Snapshot(int capacity, String tier, boolean severed) {
        static Snapshot of(ServerPlayer player) {
            MaliceData malice = MaliceService.get(player);
            return new Snapshot(malice.capacity(), malice.tier().serializedName(), malice.isSevered());
        }
    }

    @Test
    void noRerollPathChangesCapacity(MinecraftServer server) throws Exception {
        onServerThread(server, () -> {
            ensureOverworld(server);
            List<ServerPlayer> players = new ArrayList<>();
            for (int i = 0; i < ROLLED_BIRTHS; i++) {
                ServerPlayer player = fakePlayer(server, "rolled_" + i);
                BirthService.birth(player); // the real first-login birth roll
                players.add(player);
            }
            for (int capacity : FORCED_CAPACITIES) {
                ServerPlayer player = fakePlayer(server, "forced_" + capacity);
                BirthService.rebirth(player, OptionalInt.of(capacity));
                players.add(player);
            }

            for (ServerPlayer player : players) {
                if (MaliceService.isSevered(player)) continue; // a rolled birth can land on Severed; covered below
                Snapshot before = Snapshot.of(player);
                assertTrue(before.capacity() > 0, "setup: " + player.getScoreboardName() + " should hold Malice");

                for (int i = 0; i < REROLLS_PER_PATH; i++) {
                    // Admin reroll.
                    InnateTechnique was = currentOrNull(player);
                    assertEquals(RollService.RerollResult.SUCCESS, RollService.reroll(player, RollReason.ADMIN));
                    assertNotEquals(was, currentOrNull(player), "admin reroll must change the innate technique");
                    assertEquals(before, Snapshot.of(player), "admin reroll #" + i + " changed Malice for " + player.getScoreboardName());

                    // Purification Talisman: the server side of the Reroll button. It consumes the talisman only
                    // after a successful reroll, so an empty hand proves the reroll ran.
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KegareItems.PURIFICATION_TALISMAN.get()));
                    PurificationTalismanItem.confirm(player, InteractionHand.MAIN_HAND);
                    assertTrue(player.getMainHandItem().isEmpty(), "talisman reroll #" + i + " didn't go through");
                    assertEquals(before, Snapshot.of(player), "talisman reroll #" + i + " changed Malice for " + player.getScoreboardName());

                    // Replacement: an update removed their innate technique, then they log in.
                    player.setData(KegareAttachments.INNATE_TECHNIQUE, InnateTechniqueData.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(REMOVED_INNATE_TECHNIQUE)).getOrThrow());
                    PlayerEvents.handleLogin(player);
                    assertTrue(InnateTechniqueService.current(player).isPresent(), "replacement roll #" + i + " didn't go through");
                    assertEquals(before, Snapshot.of(player), "replacement roll #" + i + " changed Malice for " + player.getScoreboardName());
                }
            }

            // Severed players can't reroll at all, and stay at 0.
            ServerPlayer severed = fakePlayer(server, "severed");
            BirthService.rebirth(severed, OptionalInt.of(0));
            Snapshot before = Snapshot.of(severed);
            assertEquals(new Snapshot(0, "zero", true), before);
            for (int i = 0; i < 20; i++) {
                assertEquals(RollService.RerollResult.SEVERED, RollService.reroll(severed, RollReason.ADMIN));
                severed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KegareItems.PURIFICATION_TALISMAN.get()));
                PurificationTalismanItem.confirm(severed, InteractionHand.MAIN_HAND);
                assertEquals(1, severed.getMainHandItem().getCount(), "a refused talisman must not be consumed");
                PlayerEvents.handleLogin(severed);
                assertEquals(before, Snapshot.of(severed));
            }
        });
    }

    /**
     * The testframework's ephemeral server defines FLAT dimensions but never loads them, and a player needs a world.
     * Creates the levels the way server startup does (MinecraftServer#createLevels is protected; test-only reflection).
     */
    private static void ensureOverworld(MinecraftServer server) {
        if (server.overworld() != null) return;
        try {
            Method createLevels = MinecraftServer.class.getDeclaredMethod("createLevels", ChunkProgressListener.class);
            createLevels.setAccessible(true);
            createLevels.invoke(server, NO_PROGRESS);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't create the test server's levels", e);
        }
        assertTrue(server.overworld() != null, "test server has no overworld");
    }

    private static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos center) {}
        @Override public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    };

    private static InnateTechnique currentOrNull(ServerPlayer player) {
        return InnateTechniqueService.current(player).orElse(null);
    }

    private static ServerPlayer fakePlayer(MinecraftServer server, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("kegare-capacity-test:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(server.overworld(), new GameProfile(uuid, name));
    }

    /** Runs on the server thread (like real gameplay) and rethrows assertion failures here. */
    private static void onServerThread(MinecraftServer server, Runnable task) throws Exception {
        try {
            server.submit(task).get(5, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) throw error;
            if (e.getCause() instanceof Exception exception) throw exception;
            throw e;
        }
    }
}
