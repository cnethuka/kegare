package com.cnethuka.kegare.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.progression.FightTracker;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The Domain Meter (DESIGN §3.35): it fills from fight damage both ways, drains only out of combat, and is saved. */
@ExtendWith(EphemeralTestServerProvider.class)
class DomainMeterTest {
    private static final double EPS = 1.0e-6;

    @Test
    void itFillsFromDamageDealtAndTakenAndDrainsOutOfCombat(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer a = DomainTest.at(server, "meter_a", 8, 8);
                ServerPlayer b = DomainTest.at(server, "meter_b", 12, 8);
                DomainMeter.set(a, 0);
                DomainMeter.set(b, 0);
                FightTracker.damage(a, b, 4);
                assertEquals(4 * DomainConfig.METER_PER_DAMAGE_DEALT.get(), DomainMeter.get(a), EPS, "dealt");
                assertEquals(4 * DomainConfig.METER_PER_DAMAGE_TAKEN.get(), DomainMeter.get(b), EPS, "taken");
                FightTracker.damage(a, b, 1000);
                assertEquals(DomainData.MAX, DomainMeter.get(a), EPS, "it stops at 100");

                CombatHelper.markCombat(a);
                DomainMeter.tick(a);
                assertEquals(DomainData.MAX, DomainMeter.get(a), EPS, "no drain in a fight");
                TestServer.advanceGameTime(server, 20 * 60);
                DomainMeter.tick(a);
                assertEquals(DomainData.MAX - DomainConfig.METER_DRAIN.get(), DomainMeter.get(a), EPS, "a second's drain out of it");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void itsSavedAsItsNumber() {
        DomainData data = new DomainData();
        data.meter = 73.5;
        var json = DomainData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(73.5, DomainData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().meter(), EPS);
    }

    @Test
    void aMeterSavedUnderItsOldNameIsMovedOnLogin(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "meter_old", 8, 8);
                DomainMeter.set(p, 0);
                DomainData old = new DomainData();
                old.meter = 64;
                p.setData(DomainRegistry.OLD_METER, old);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
                assertEquals(64, DomainMeter.get(p), EPS, "carried over");
                assertFalse(p.hasData(DomainRegistry.OLD_METER), "and the old one gone");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
