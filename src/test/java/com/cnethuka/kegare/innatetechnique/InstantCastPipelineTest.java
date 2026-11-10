package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Instant Cast's key intents, in the order they're sent, on the real cast pipeline: each cast type, base and special. Rime has
 * one of each: Frost Shard (instant, its special Splinter too), Glacial Lance (charged, Glacial Spire too), Frost Field
 * (channelled) and Rime Shell (toggle).
 */
@ExtendWith(EphemeralTestServerProvider.class)
class InstantCastPipelineTest {
    /** Frost Shard, Splinter and the Lance leave entities in the shared test world; discard them so no other test counts them. */
    @org.junit.jupiter.api.AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            java.util.List<net.minecraft.world.entity.Entity> left = new java.util.ArrayList<>();
            server.overworld().getAllEntities().forEach(entity -> {
                if (entity != null && !(entity instanceof net.minecraft.world.entity.player.Player)) left.add(entity);
            });
            left.forEach(net.minecraft.world.entity.Entity::discard);
            TestServer.removePlayers(server);
        });
    }

    private static final int SHARD = 0, LANCE = 1, FIELD = 2, SHELL = 3;

    private static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.player(server, name);
        BirthService.rebirth(p, OptionalInt.of(5000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get()); // Frost Shard, Glacial Lance, Frost Field, Rime Shell
        MaliceService.refill(p);
        return p;
    }

    /** A number key going down. */
    private static void number(ServerPlayer p, int slot) {
        InnateTechniqueService.select(p, slot);
        CastManager.handleIntent(p, CastIntent.CAST_PRESS);
    }

    /** R held, and a number key going down. */
    private static void rNumber(ServerPlayer p, int slot) {
        InnateTechniqueService.select(p, slot);
        CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
    }

    @Test
    void instantFiresOnThePressAndTheReleaseDoesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_instant");
            InnateTechniqueService.select(p, FIELD); // whatever was selected before doesn't matter
            number(p, SHARD);
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD) > 0, "fired at once");
            double after = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertEquals(after, MaliceService.get(p).current(), 1e-6);
        });
    }

    @Test
    void chargedChargesOnThePressAndFiresOnTheRelease(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_charged");
            number(p, LANCE);
            assertSame(Rime.GLACIAL_LANCE, CastManager.state(p).casting().ability());
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, Rime.GLACIAL_LANCE), "nothing's gone off yet");
            TestServer.advanceGameTime(server, 40);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.GLACIAL_LANCE) > 0, "it fired");
        });
    }

    @Test
    void channeledRunsFromThePressToTheRelease(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_channeled");
            number(p, FIELD);
            assertSame(Rime.FROST_FIELD, CastManager.state(p).casting().ability());
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNull(CastManager.state(p).casting());
        });
    }

    @Test
    void toggleSwitchesOnEachPressAndTheReleaseDoesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_toggle");
            number(p, SHELL);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertTrue(CastManager.isToggleActive(p, Rime.RIME_SHELL), "letting go leaves it on");
            number(p, SHELL);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertFalse(CastManager.isToggleActive(p, Rime.RIME_SHELL));
        });
    }

    @Test
    void aSpecialIsCastFromAnotherSlotInOneGo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_special");
            InnateTechniqueService.select(p, SHELL);
            rNumber(p, SHARD);
            assertEquals(SHARD, InnateTechniqueService.get(p).selectedSlot());
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.SPLINTER) > 0, "Splinter fired");
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
        });
    }

    @Test
    void aChargedSpecialFiresOnItsOwnReleaseOnly(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_charged_special");
            // Glacial Spire rises from the ground it's aimed at.
            server.overworld().setBlockAndUpdate(net.minecraft.core.BlockPos.containing(p.getX(), 95, p.getZ()), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            p.moveTo(p.getX(), 96, p.getZ(), 0f, 90f);
            rNumber(p, LANCE);
            assertSame(Rime.GLACIAL_SPIRE, CastManager.state(p).casting().ability());
            TestServer.advanceGameTime(server, 20);
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
            assertNotNull(CastManager.state(p).casting(), "a base release doesn't touch a special's charge");
            CastManager.handleIntent(p, CastIntent.SPECIAL_RELEASE);
            assertNull(CastManager.state(p).casting());
            assertTrue(InnateTechniqueService.cooldownRemaining(p, Rime.GLACIAL_SPIRE) > 0, "it fired");
        });
    }

    @Test
    void aStunLeavesNothingStuck(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_stun");
            number(p, LANCE);
            CombatHelper.stun(p, 10);
            CastManager.tick(p);
            assertNull(CastManager.state(p).casting(), "interrupted");
            double malice = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE); // the key coming up later
            assertNull(CastManager.state(p).casting());
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, Rime.GLACIAL_LANCE), "no cooldown, as ever");
            assertEquals(malice, MaliceService.get(p).current(), 1e-6, "and nothing paid");
        });
    }

    @Test
    void anEmptySlotCastsNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "ic_empty");
            double malice = MaliceService.get(p).current();
            number(p, 8);
            assertEquals(8, InnateTechniqueService.get(p).selectedSlot());
            assertNull(CastManager.state(p).casting());
            assertEquals(malice, MaliceService.get(p).current(), 1e-6);
            rNumber(p, 8);
            assertNull(CastManager.state(p).casting());
            assertEquals(malice, MaliceService.get(p).current(), 1e-6);
        });
    }
}
