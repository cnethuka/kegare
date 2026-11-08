package com.cnethuka.kegare.innatetechnique;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.rmt.Rmt;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Burnout (DESIGN §3.25): blocks the innate technique and its ultimate, never a shared ability; counts down; saved; the hook. */
@ExtendWith(EphemeralTestServerProvider.class)
class BurnoutTest {
    private static final double EPS = 1.0e-4;

    @Test
    void burnoutBlocksTheTechniqueButNotSharedAbilities(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.player(server, "burnout");
            BirthService.rebirth(p, OptionalInt.of(3000));
            InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
            TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);
            MaliceService.refill(p);
            assertTrue(SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE));
            assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 4, 0, Optional.of(Rmt.REVERSE_MALICE_TECHNIQUE.id())));

            Burnout.apply(p, 200);
            assertTrue(Burnout.isBurntOut(p));
            assertEquals(200, Burnout.remaining(p));

            // An ability of the technique: fizzles, nothing paid, no cooldown.
            InnateTechniqueService.select(p, 0);
            double before = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertEquals(before, MaliceService.get(p).current(), EPS, "nothing paid");
            assertEquals(0, InnateTechniqueService.cooldownRemaining(p, Rime.FROST_SHARD), "no cooldown");
            // Its special too.
            CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
            assertEquals(before, MaliceService.get(p).current(), EPS);

            // A shared ability still casts.
            InnateTechniqueService.select(p, 4);
            p.setHealth(6f);
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertNotNull(CastManager.state(p).casting(), "Reverse Malice Technique still works");
            CastManager.handleIntent(p, CastIntent.CAST_RELEASE);

            // It counts down, a tick at a time, and never shortens when applied again shorter.
            Burnout.apply(p, 50);
            assertEquals(200, Burnout.remaining(p), "the longer one stands");
            for (int i = 0; i < 199; i++) Burnout.tick(p);
            assertTrue(Burnout.isBurntOut(p));
            Burnout.tick(p);
            assertFalse(Burnout.isBurntOut(p), "over");
            InnateTechniqueService.clearCooldowns(p);
            InnateTechniqueService.select(p, 0);
            before = MaliceService.get(p).current();
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
            assertTrue(MaliceService.get(p).current() < before, "casts again once it's over");
            TestServer.removePlayers(server);
        });
    }

    @Test
    void itIsSavedNotCopiedOnDeathAndHasADurationHook(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.player(server, "burnout_saved");
            Burnout.apply(p, 300);
            var saved = Burnout.Data.CODEC.encodeStart(NbtOps.INSTANCE, p.getData(KegareAttachments.BURNOUT)).getOrThrow();
            Burnout.Data read = Burnout.Data.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
            p.setData(KegareAttachments.BURNOUT, read);
            assertEquals(300, Burnout.remaining(p), "saved and read back (it survives logging out)");
            assertFalse(copiedOnDeath(KegareAttachments.BURNOUT.get()), "not copied to the respawned player: dying clears it");

            Burnout.clear(p);
            Burnout.Modifier halve = (player, ticks) -> ticks / 2;
            Burnout.addModifier(halve);
            try {
                Burnout.apply(p, 400);
                assertEquals(200, Burnout.remaining(p), "a duration modifier changes it as it's applied");
            } finally {
                Burnout.removeModifier(halve);
            }
            Burnout.shorten(p, 50);
            assertEquals(150, Burnout.remaining(p));
            Burnout.clear(p);
            TestServer.removePlayers(server);
        });
    }

    private static boolean copiedOnDeath(net.neoforged.neoforge.attachment.AttachmentType<?> type) {
        try {
            java.lang.reflect.Field field = net.neoforged.neoforge.attachment.AttachmentType.class.getDeclaredField("copyOnDeath");
            field.setAccessible(true);
            return field.getBoolean(type);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
