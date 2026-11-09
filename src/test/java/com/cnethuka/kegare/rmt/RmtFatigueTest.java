package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.FightTracker;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.ProgressionConfig;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Injury fatigue (DESIGN §3.17): damage taken drags RMT's output down, fatigueWeight x (taken / max health), up to maxFatigue; healing
 * doesn't take it away; it resets when the fight ends, on death and on logout; and the restore and ignore hooks work.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class RmtFatigueTest {
    private static final double EPS = 1.0e-6;

    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = TestServer.damageable(server, name);
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(500));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        GradeService.set(p, Grade.GRADE_4);
        p.setHealth(p.getMaxHealth());
        p.moveTo(0.5, 120, 0.5);
        return p;
    }

    private static void hit(ServerPlayer p, float amount) {
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().generic(), amount);
    }

    @Test
    void injuriesDragOutputDown(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            try {
                ServerPlayer p = player(server, level, "rmt_fatigue");
                double weight = RmtConfig.FATIGUE_WEIGHT.get(), cap = RmtConfig.MAX_FATIGUE.get();
                assertEquals(0.0, RmtFatigue.of(p), EPS, "fresh");

                // 8 of 20 health taken: 0.15 x 0.4 = 0.06 off, so output 0.94.
                hit(p, 8f);
                assertEquals(weight * 8 / 20, RmtFatigue.of(p), EPS, "from damage taken");
                assertEquals(1.0 - weight * 8 / 20, RmtOutput.of(p), EPS, "output times (1 - fatigue)");

                // Healing doesn't take it away.
                p.setHealth(4f);
                p.heal(16f);
                assertEquals(weight * 8 / 20, RmtFatigue.of(p), EPS, "healing doesn't remove it");

                // Never past maxFatigue, so it never stops RMT.
                for (int i = 0; i < 20; i++) hit(p, 19f);
                assertEquals(cap, RmtFatigue.of(p), EPS, "capped");
                assertEquals(1.0 - cap, RmtOutput.of(p), EPS);

                // The hooks: restore takes some away; ignore pauses it.
                RmtFatigue.restore(p, 0.2);
                assertEquals(cap - 0.2, RmtFatigue.of(p), EPS, "restore");
                RmtFatigue.ignore(p, 40);
                hit(p, 10f);
                assertEquals(cap - 0.2, RmtFatigue.of(p), EPS, "ignored while it lasts");
                TestServer.advanceGameTime(server, 41);
                hit(p, 4f);
                assertEquals(cap - 0.2 + weight * 4 / 20, RmtFatigue.of(p), EPS, "counted again after");

                // The fight ends: reset.
                ServerPlayer other = player(server, level, "rmt_fatigue_other");
                FightTracker.damage(other, p, 2.0);
                assertFalse(FightTracker.of(p).isEmpty(), "setup: in a fight");
                TestServer.advanceGameTime(server, (int) ProgressionConfig.ticks(ProgressionConfig.FIGHT_TIMEOUT) + 1);
                FightTracker.endExpired(server);
                assertEquals(0.0, RmtFatigue.of(p), EPS, "the fight ended");

                // Death resets it.
                hit(p, 8f);
                p.die(p.damageSources().generic());
                assertEquals(0.0, RmtFatigue.of(p), EPS, "death");

                // So does logging out.
                hit(p, 8f);
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
                assertEquals(0.0, RmtFatigue.of(p), EPS, "logout");

                // The admin command's setter.
                RmtFatigue.set(p, 0.3);
                assertEquals(0.3, RmtFatigue.of(p), EPS);
            } finally {
                FightTracker.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
