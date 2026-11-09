package com.cnethuka.kegare.fists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.rmt.RmtFatigue;
import com.cnethuka.kegare.rmt.RmtOutput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The zone (DESIGN §3.28): 120% damage and RMT, fatigue ignored and restored, refreshed, wider, and the chain ending with it. */
@ExtendWith(EphemeralTestServerProvider.class)
class ZoneTest {
    private static final double EPS = 1.0e-3;

    @Test
    void theZoneAndItsChain(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            IronGolem golem = null;
            List<Integer> chains = new ArrayList<>();
            BlackSpark.Listener listener = (attacker, target, chain) -> chains.add(chain);
            BlackSpark.listen(listener);
            try {
                ServerPlayer p = FistsTest.player(server, "zone_p");
                golem = BlackSparkTest.place(level, new IronGolem(EntityType.IRON_GOLEM, level), 4.5, 8.5);
                int zoneTicks = (int) Math.round(FistsConfig.ZONE_SECONDS.get() * 20);
                float zone = FistsConfig.ZONE_DAMAGE.get().floatValue();

                // Out of it: a plain hit, and RMT dragged down by its injuries.
                assertFalse(Zone.in(p));
                assertEquals(2f, BlackSparkTest.swing(p, golem, 2f), EPS, "a plain hit");
                RmtFatigue.set(p, 0.5);
                assertEquals(RmtOutput.base(p) * 0.5, RmtOutput.of(p), EPS, "fatigued");

                // A Black Spark: in the zone for zoneSeconds, the chain at 1.
                BlackSpark.force(p);
                BlackSparkTest.swing(p, golem, 1f);
                assertTrue(Zone.in(p), "in the zone");
                assertEquals(zoneTicks, Zone.ticksLeft(p));
                assertEquals(List.of(1), chains, "it starts the chain");
                assertEquals(0.5, RmtFatigue.of(p), EPS, "the first one restores nothing");
                assertEquals(1f, BlackSparkTest.swing(p, golem, 1f), EPS, "the swing that put them there isn't one of the zone's");
                TestServer.advanceGameTime(server, 1);

                // 120%: damage of every kind, and RMT, which ignores the fatigue.
                assertEquals(2f * zone, BlackSparkTest.swing(p, golem, 2f), EPS, "melee x1.2");
                golem.invulnerableTime = 0;
                float hp = golem.getHealth();
                golem.hurt(com.cnethuka.kegare.innate_technique.rime.RimeDamage.source(level, p, p), 2f);
                assertEquals(2f * zone, hp - golem.getHealth(), EPS, "a technique x1.2 too");
                assertEquals(RmtOutput.base(p) * FistsConfig.ZONE_RMT.get(), RmtOutput.of(p), EPS, "RMT x1.2, fatigue ignored");

                // Another one in the zone: refreshed, the chain at 2, and some fatigue gone for good.
                TestServer.advanceGameTime(server, 59);
                assertEquals(zoneTicks - 60, Zone.ticksLeft(p));
                BlackSpark.force(p);
                BlackSparkTest.swing(p, golem, 1f);
                assertEquals(zoneTicks, Zone.ticksLeft(p), "refreshed");
                assertEquals(List.of(1, 2), chains);
                assertEquals(0.5 - FistsConfig.RESTORE_PER_SPARK.get(), RmtFatigue.of(p), EPS, "restorePerSpark");

                // It ends: the chain with it, and the next one starts over at 1.
                TestServer.advanceGameTime(server, zoneTicks);
                Zone.tick(p);
                assertFalse(Zone.in(p), "over");
                assertEquals(0, Zone.chain(p));
                assertEquals(RmtOutput.base(p) * (1 - RmtFatigue.of(p)), RmtOutput.of(p), EPS, "RMT back to its fatigue, less what was restored");
                assertEquals(2f, BlackSparkTest.swing(p, golem, 2f), EPS, "a plain hit again");
                BlackSpark.force(p);
                BlackSparkTest.swing(p, golem, 1f);
                assertEquals(List.of(1, 2, 1), chains, "a new chain");

                // The command: in the zone with no chain, and out again.
                Zone.start(p, 0);
                assertFalse(Zone.in(p));
                Zone.start(p, 100);
                assertTrue(Zone.in(p));
                assertEquals(0, Zone.chain(p), "a zone from the command starts no chain");
                Zone.start(p, 0);
            } finally {
                BlackSpark.LISTENERS_FOR_TESTS.remove(listener);
                if (golem != null) golem.discard();
                BlackSpark.clear();
                Zone.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
