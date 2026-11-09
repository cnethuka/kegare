package com.cnethuka.kegare.fists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import com.cnethuka.kegare.player.BirthService;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Reinforced Fists' and Black Spark's advancements (DESIGN §3.28): hidden, announced, Challenge, players only. */
@ExtendWith(EphemeralTestServerProvider.class)
class FistsAdvancementsTest {
    static final List<ResourceLocation> ALL = List.of(FistsAdvancements.LOCKED_IN, FistsAdvancements.ONE_TWENTY_PERCENT,
            FistsAdvancements.NOTHING_TO_CELEBRATE, FistsAdvancements.BLESSED_BY_THE_SPARKS, FistsAdvancements.LIGHTS_OUT);

    static ServerPlayer player(MinecraftServer server, String name, int capacity) {
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(capacity));
        if (capacity > 0) {
            com.cnethuka.kegare.innatetechnique.InnateTechniqueService.setInnateTechnique(p, com.cnethuka.kegare.innate_technique.rime.Rime.RIME.get());
            com.cnethuka.kegare.malice.MaliceService.refill(p);
        }
        p.moveTo(2.5, 120, 8.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static boolean has(MinecraftServer server, ServerPlayer p, ResourceLocation id) {
        return TestServer.hasAdvancement(server, p, id);
    }

    private static void strike(ServerPlayer attacker, LivingEntity target) {
        ReinforcedStrike.earn(attacker);
        hit(attacker, target);
        assertFalse(ReinforcedStrike.isReady(attacker), "setup: the Strike went off");
    }

    private static void hit(ServerPlayer attacker, LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurt(attacker.damageSources().playerAttack(attacker), 0.05f);
        target.setHealth(target.getMaxHealth());
    }

    /** A Black Spark on {@code target}, forced, and a tick on. */
    private static void spark(MinecraftServer server, ServerPlayer attacker, LivingEntity target) {
        BlackSpark.force(attacker);
        BlackSparkTest.swing(attacker, target, 0.01f);
        target.setHealth(target.getMaxHealth());
        TestServer.advanceGameTime(server, 1);
        Zone.tick(attacker);
    }

    @Test
    void everyOneIsHiddenAnnouncedAndAChallenge(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (ResourceLocation id : ALL) {
                AdvancementHolder holder = server.getAdvancements().get(id);
                assertNotNull(holder, id + " loads");
                DisplayInfo display = holder.value().display().orElseThrow();
                assertTrue(display.isHidden(), id + " is hidden");
                assertTrue(display.shouldAnnounceChat(), id + " is announced");
                assertEquals(AdvancementType.CHALLENGE, display.getType(), id + " is a Challenge");
            }
        });
    }

    @Test
    void lockedInIsThreeStrikesInARowOnPlayers(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            IronGolem golem = BlackSparkTest.place(level, new IronGolem(EntityType.IRON_GOLEM, level), 4.5, 6.5);
            try {
                ServerPlayer p = player(server, "locked_p", 1000);
                ServerPlayer q = player(server, "locked_q", 1000);
                q.moveTo(4.5, 120, 8.5, 90f, 0f);

                for (int i = 0; i < 3; i++) strike(p, golem);
                assertFalse(has(server, p, FistsAdvancements.LOCKED_IN), "mobs never count");

                strike(p, q);
                strike(p, q);
                hit(p, q); // a plain hit between
                strike(p, q);
                strike(p, q);
                assertFalse(has(server, p, FistsAdvancements.LOCKED_IN), "the plain hit broke it: two in a row");
                strike(p, q);
                assertTrue(has(server, p, FistsAdvancements.LOCKED_IN), "three in a row");

                // Malice users only: a Severed player never has a Reinforced Strike to land.
                ServerPlayer s = player(server, "locked_s", 0);
                for (int i = 0; i < 3; i++) {
                    ReinforcedStrike.earn(s);
                    assertFalse(ReinforcedStrike.isReady(s), "no Strike for the Severed");
                    hit(s, q);
                }
                assertFalse(has(server, s, FistsAdvancements.LOCKED_IN), "so never Locked in");
            } finally {
                golem.discard();
                FistsAdvancements.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void theChainsCountPlayersAndEndWithTheZone(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            server.setPvpAllowed(true);
            IronGolem golem = BlackSparkTest.place(level, new IronGolem(EntityType.IRON_GOLEM, level), 4.5, 6.5);
            try {
                ServerPlayer p = player(server, "chain_p", 1000);
                ServerPlayer q = player(server, "chain_q", 1000);
                q.moveTo(4.5, 120, 8.5, 90f, 0f);

                // Mobs never count, however long the chain.
                ServerPlayer m = player(server, "chain_m", 1000);
                for (int i = 0; i < 8; i++) spark(server, m, golem);
                assertEquals(8, Zone.chain(m), "setup: a chain of 8 on a mob");
                for (ResourceLocation id : ALL) assertFalse(has(server, m, id), id + ": mobs never count");

                // A chain resets when the zone ends.
                ServerPlayer r = player(server, "chain_r", 1000);
                spark(server, r, q);
                TestServer.advanceGameTime(server, (int) Math.round(FistsConfig.ZONE_SECONDS.get() * 20) + 1);
                Zone.tick(r);
                assertEquals(0, Zone.chain(r), "the zone ended");
                spark(server, r, q);
                assertFalse(has(server, r, FistsAdvancements.ONE_TWENTY_PERCENT), "two, but not in one zone");

                // 2, 4 and 7 on players.
                spark(server, p, q);
                assertFalse(has(server, p, FistsAdvancements.ONE_TWENTY_PERCENT), "one isn't a chain");
                spark(server, p, q);
                assertTrue(has(server, p, FistsAdvancements.ONE_TWENTY_PERCENT), "120%");
                spark(server, p, golem); // a mob in the middle keeps the zone up but doesn't count
                spark(server, p, q);
                assertFalse(has(server, p, FistsAdvancements.NOTHING_TO_CELEBRATE), "three on players");
                spark(server, p, q);
                assertTrue(has(server, p, FistsAdvancements.NOTHING_TO_CELEBRATE), "Nothing to Celebrate");
                spark(server, p, q);
                spark(server, p, q);
                assertFalse(has(server, p, FistsAdvancements.BLESSED_BY_THE_SPARKS), "six");
                spark(server, p, q);
                assertTrue(has(server, p, FistsAdvancements.BLESSED_BY_THE_SPARKS), "Blessed by the Sparks");
            } finally {
                golem.discard();
                BlackSpark.clear();
                Zone.clear();
                FistsAdvancements.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
