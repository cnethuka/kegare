package com.cnethuka.kegare.dominion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.progression.FightTracker;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The Dominion Meter (DESIGN §3.35): it fills from fight damage both ways, drains only out of combat, and is saved. */
@ExtendWith(EphemeralTestServerProvider.class)
class DominionMeterTest {
    private static final double EPS = 1.0e-6;

    @Test
    void itFillsFromDamageDealtAndTakenAndDrainsOutOfCombat(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer a = DominionTest.at(server, "meter_a", 8, 8);
                ServerPlayer b = DominionTest.at(server, "meter_b", 12, 8);
                DominionMeter.set(a, 0);
                DominionMeter.set(b, 0);
                FightTracker.damage(a, b, 4);
                assertEquals(4 * DominionConfig.METER_PER_DAMAGE_DEALT.get(), DominionMeter.get(a), EPS, "dealt");
                assertEquals(4 * DominionConfig.METER_PER_DAMAGE_TAKEN.get(), DominionMeter.get(b), EPS, "taken");
                FightTracker.damage(a, b, 1000);
                assertEquals(DominionData.MAX, DominionMeter.get(a), EPS, "it stops at 100");

                CombatHelper.markCombat(a);
                DominionMeter.tick(a);
                assertEquals(DominionData.MAX, DominionMeter.get(a), EPS, "no drain in a fight");
                TestServer.advanceGameTime(server, 20 * 60);
                DominionMeter.tick(a);
                assertEquals(DominionData.MAX - DominionConfig.METER_DRAIN.get(), DominionMeter.get(a), EPS, "a second's drain out of it");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void itsSavedAsItsNumber() {
        DominionData data = new DominionData();
        data.meter = 73.5;
        var json = DominionData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(73.5, DominionData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().meter(), EPS);
    }
}
