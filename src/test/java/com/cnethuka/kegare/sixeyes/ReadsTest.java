package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.reinforcement.ReinforcementIds;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Six Eyes' Reads (DESIGN §3.32): compact and full, "Void", and nothing secret. */
@ExtendWith(EphemeralTestServerProvider.class)
class ReadsTest {
    @Test
    void compactAndFullReadsAndAVoidOnlySaysVoid(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer target = SightTest.player(server, "reads_target", 3000, 0.5, 6.5); // straight ahead (yaw 0 faces +z)
                ServerPlayer aside = SightTest.player(server, "reads_aside", 800, 12.5, 0.5);
                ServerPlayer severed = SightTest.player(server, "reads_void", 0, -12.5, 0.5);
                SightTest.eyes(server, bearer, true);
                bearer.moveTo(0.5, 120, 0.5, 0f, 0f);
                bearer.setYHeadRot(0f);

                ReadsPayload reads = Reads.build(bearer);
                assertEquals(3, reads.glances().size(), "everyone near enough, at a glance");
                ReadsPayload.Glance t = reads.glances().stream().filter(g -> g.entityId() == target.getId()).findFirst().orElseThrow();
                assertEquals(Optional.of(Rime.RIME.get().id()), t.technique(), "their innate technique");
                assertEquals((int) MaliceService.get(target).current(), t.malice(), "their current Malice");
                ReadsPayload.Glance v = reads.glances().stream().filter(g -> g.entityId() == severed.getId()).findFirst().orElseThrow();
                assertTrue(v.severed(), "a void");
                assertEquals(Optional.empty(), v.technique(), "says nothing");
                assertEquals(0, v.malice(), "nothing at all");

                // The one at the crosshair gets full detail: a charge in progress, a toggle, a cooldown.
                assertSame(target, Reads.focus(bearer), "the focus is the one at the crosshair");
                assertEquals(LoadoutResult.OK, LoadoutService.edit(target, LoadoutService.Action.SET, 3, 0, Optional.of(ReinforcementIds.ABILITY)));
                assertEquals(LoadoutResult.OK, LoadoutService.edit(target, LoadoutService.Action.SET, 4, 0, Optional.of(Rime.GLACIAL_LANCE.id())));
                InnateTechniqueService.select(target, 3);
                CastManager.handleIntent(target, CastIntent.CAST_PRESS); // Reinforcement on
                InnateTechniqueService.startCooldown(target, Rime.RIME_SHELL);
                InnateTechniqueService.select(target, 4);
                CastManager.handleIntent(target, CastIntent.CAST_PRESS); // charging the lance
                for (int i = 0; i < 5; i++) CastManager.tick(target);
                ReadsPayload.Focus full = Reads.detail(target);
                assertTrue(full.toggles().contains(ReinforcementIds.ABILITY), "active toggles");
                assertTrue(full.cooldowns().stream().anyMatch(c -> c.seconds() > 0), "cooldowns");
                ReadsPayload.Cast charging = full.cast().orElseThrow();
                assertEquals(Rime.GLACIAL_LANCE.id(), charging.ability(), "what they're charging");
                assertTrue(charging.progress() > 0f && charging.progress() <= 1f, "and how far: " + charging.progress());
                CastManager.stopAll(target, EndReason.CANCELLED);

                // Turn to the void: their Read is empty.
                bearer.moveTo(0.5, 120, 0.5, 90f, 0f); // yaw 90 faces -x
                assertSame(severed, Reads.focus(bearer));
                ReadsPayload.Focus voidRead = Reads.detail(severed);
                assertTrue(voidRead.severed() && voidRead.cast().isEmpty() && voidRead.cooldowns().isEmpty() && voidRead.toggles().isEmpty(), "Void");

                bearer.moveTo(0.5, 120, 0.5, 180f, 0f);
                assertNull(Reads.focus(bearer), "nobody at the crosshair: no focus");
                assertTrue(aside.isAlive());
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }

    /** What a Read carries, field by field: never Grade, never traits. */
    @Test
    void nothingSecretIsEverCarried() {
        for (Class<?> c : new Class<?>[] {ReadsPayload.Glance.class, ReadsPayload.Focus.class, ReadsPayload.Cast.class, ReadsPayload.Cooldown.class,
                SightPayloads.Players.Dot.class, SightPayloads.Mobs.Dot.class}) {
            for (RecordComponent rc : c.getRecordComponents()) {
                String name = rc.getName().toLowerCase();
                assertFalse(name.contains("grade") || name.contains("trait") || name.contains("refinement") || name.contains("insight"),
                        c.getSimpleName() + "." + rc.getName() + " must not be there");
            }
        }
        assertTrue(Arrays.stream(Grade.values()).count() > 0);
    }

    @Test
    void gradeNeverReachesARead(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer bearer = SightTest.player(server, "reads_grade_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer target = SightTest.player(server, "reads_grade_target", 1000, 0.5, 6.5);
                SightTest.eyes(server, bearer, true);
                ReadsPayload before = Reads.build(bearer);
                GradeService.set(target, Grade.SPECIAL_GRADE);
                ReadsPayload after = Reads.build(bearer);
                assertEquals(before.glances(), after.glances(), "a Special Grade reads the same as a Grade 4");
            } finally {
                SightTest.eyes(server, bearer, false);
                TestServer.removePlayers(server);
            }
        });
    }
}
