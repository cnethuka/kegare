package com.cnethuka.kegare.innate_technique.rime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastSlot;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Rime's freeze rules and visual bookkeeping, on a throwaway server with the real code. */
@ExtendWith(EphemeralTestServerProvider.class)
class RimeFreezeTest {

    @Test
    void freezeRules(MinecraftServer server) throws Exception {
        onServerThread(server, () -> {
            ensureOverworld(server);
            int max = RimeConfig.FREEZE_MAX_STACKS.get();

            // A Rime player channeling Frost Field.
            ServerPlayer victim = player(server, "rime_victim");
            BirthService.rebirth(victim, OptionalInt.of(500));
            InnateTechniqueService.setInnateTechnique(victim, Rime.RIME.get());
            InnateTechniqueService.select(victim, 2); // Frost Field
            MaliceService.refill(victim);
            CastManager.tryCast(victim, CastSlot.SELECTED);
            assertNotNull(CastManager.state(victim).casting(), "setup: Frost Field should be channeling");
            assertTrue(victim.getData(KegareAttachments.VISUAL_STATE).loops()
                    .containsKey(VisualService.key(Rime.FROST_FIELD, VisualPhase.CHANNEL_LOOP)), "the channel's visual loop is running");

            // Stacks below the max: no freeze.
            FreezeService.addStacks(victim, max - 1, null);
            assertEquals(max - 1, FreezeService.stacks(victim));
            assertFalse(FreezeService.isFrozen(victim));

            // The last stack freezes solid: a stun from the existing system that interrupts the channel.
            FreezeService.addStacks(victim, 1, null);
            assertTrue(FreezeService.isFrozen(victim), "frozen at max stacks");
            assertTrue(CombatHelper.isStunned(victim), "frozen = stunned");
            assertNull(CastManager.state(victim).casting(), "the freeze interrupted the channel");
            assertFalse(victim.getData(KegareAttachments.VISUAL_STATE).loops()
                    .containsKey(VisualService.key(Rime.FROST_FIELD, VisualPhase.CHANNEL_LOOP)), "and stopped its visual");
            assertTrue(victim.getData(KegareAttachments.VISUAL_STATE).loops().containsKey(RimeIds.FROZEN_LOOP_KEY), "frozen visual on");
            assertEquals(0, FreezeService.stacks(victim), "stacks reset on freezing");
            assertEquals(0.0, victim.getAttributeValue(Attributes.MOVEMENT_SPEED), 1e-9, "can't move while frozen");

            // Stacks are ignored while frozen.
            FreezeService.addStacks(victim, 3, null);
            assertEquals(0, FreezeService.stacks(victim));

            // Break free: thaw after the freeze time, visual off, movement back, then a short immunity.
            FreezeData data = FreezeService.get(victim);
            data.frozenUntil = victim.level().getGameTime(); // time is up
            FreezeService.tick(victim);
            assertFalse(FreezeService.isFrozen(victim));
            assertFalse(victim.getData(KegareAttachments.VISUAL_STATE).loops().containsKey(RimeIds.FROZEN_LOOP_KEY), "frozen visual off");
            assertTrue(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0, "can move again");
            FreezeService.addStacks(victim, 2, null);
            assertEquals(0, FreezeService.stacks(victim), "immune right after breaking free");

            // After immunity, stacks apply again and melt one at a time.
            data.immuneUntil = 0;
            FreezeService.addStacks(victim, 2, null);
            assertEquals(2, FreezeService.stacks(victim));
            data.lastChange -= RimeConfig.ticks(RimeConfig.FREEZE_DECAY_SECONDS);
            FreezeService.tick(victim);
            assertEquals(1, FreezeService.stacks(victim), "one stack melted");

            // Rime Shell: on, then damage is reduced by the configured fraction.
            ServerPlayer shell = player(server, "rime_shell");
            BirthService.rebirth(shell, OptionalInt.of(500));
            InnateTechniqueService.setInnateTechnique(shell, Rime.RIME.get());
            InnateTechniqueService.select(shell, 3);
            MaliceService.refill(shell);
            CastManager.tryCast(shell, CastSlot.SELECTED);
            assertTrue(Rime.RIME_SHELL.isActive(shell), "setup: shell on");
            assertTrue(shell.getData(KegareAttachments.VISUAL_STATE).loops()
                    .containsKey(VisualService.key(Rime.RIME_SHELL, VisualPhase.TOGGLE_LOOP)), "shell aura loop running");
            // Fake players are invulnerable, so post the real damage event through NeoForge's bus (runs RimeEvents).
            var event = new net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre(shell,
                    new net.neoforged.neoforge.common.damagesource.DamageContainer(shell.damageSources().generic(), 10f));
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            assertEquals(10f * (1f - RimeConfig.SHELL_DAMAGE_REDUCTION.get().floatValue()), event.getNewDamage(), 0.01f, "shell reduced the damage");
            assertEquals(10.0 * RimeConfig.SHELL_DAMAGE_REDUCTION.get(), shell.getData(RimeRegistry.STATS).shellBlocked(), 0.01,
                    "blocked damage counts toward Thin Ice");
        });
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("kegare-rime-test:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(server.overworld(), new GameProfile(uuid, name));
    }

    /** The testframework's server defines FLAT dimensions but never loads them; create them as startup does. */
    private static void ensureOverworld(MinecraftServer server) {
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
