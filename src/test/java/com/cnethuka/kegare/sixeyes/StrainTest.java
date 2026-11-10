package com.cnethuka.kegare.sixeyes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.backgroundtask.BackgroundRmt;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskResult;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.registry.KegareItems;
import com.cnethuka.kegare.rmt.Rmt;
import com.cnethuka.kegare.rmt.RmtIds;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.theillusivec4.curios.api.CuriosApi;

/** Eyewear, Strain and the uncovered edge (DESIGN §3.32). */
@ExtendWith(EphemeralTestServerProvider.class)
class StrainTest {
    private static final double EPS = 1.0e-6;

    static void wear(ServerPlayer p, ItemStack stack) {
        var inventory = CuriosApi.getCuriosInventory(p).orElseThrow();
        assertTrue(inventory.getStacksHandler(Eyewear.SLOT).isPresent(), "setup: players have the Eyes slot");
        inventory.setEquippedCurio(Eyewear.SLOT, 0, stack);
    }

    private static double second(ServerPlayer p, boolean inFight) {
        double before = Strain.data(p).strain();
        for (int i = 0; i < 20; i++) {
            if (inFight) CombatHelper.markCombat(p);
            Strain.tick(p);
        }
        return Strain.data(p).strain() - before;
    }

    private static void clearCombat(MinecraftServer server) {
        TestServer.advanceGameTime(server, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) + 1);
    }

    @Test
    void eyewearIsAPlayersSlotAndMeansNothingOnANonBearer(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer plain = SightTest.player(server, "wear_plain", 1000, 0.5, 0.5);
                assertEquals(Eyewear.NONE, Eyewear.of(plain));
                wear(plain, new ItemStack(KegareItems.BLINDFOLD.get()));
                assertEquals(Eyewear.BLINDFOLD, Eyewear.of(plain), "anyone can wear it");
                wear(plain, new ItemStack(KegareItems.SUNGLASSES.get()));
                assertEquals(Eyewear.SUNGLASSES, Eyewear.of(plain));
                assertFalse(SixEyes.isBearer(plain), "and it proves nothing");
                assertTrue(new ItemStack(KegareItems.BLINDFOLD.get()).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("curios", Eyewear.SLOT))), "Curios' slot takes it");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void strainBuildsInAFightSlowerCoveredAndFallsOutOfOneFasterCovered(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "strain_rates", 1000, 0.5, 0.5);
            try {
                SightTest.eyes(server, p, true);
                double uncovered = SixEyesConfig.STRAIN_BUILD.get();
                assertEquals(uncovered, second(p, true), 1e-3, "fastest uncovered");
                wear(p, new ItemStack(KegareItems.SUNGLASSES.get()));
                assertEquals(uncovered * SixEyesConfig.SUNGLASSES_BUILD.get(), second(p, true), 1e-3, "slower with glasses");
                wear(p, new ItemStack(KegareItems.BLINDFOLD.get()));
                assertEquals(uncovered * SixEyesConfig.BLINDFOLD_BUILD.get(), second(p, true), 1e-3, "slowest with a blindfold");
                assertTrue(SixEyesConfig.BLINDFOLD_BUILD.get() < SixEyesConfig.SUNGLASSES_BUILD.get());

                Strain.set(p, 50);
                clearCombat(server);
                wear(p, ItemStack.EMPTY);
                assertEquals(-SixEyesConfig.STRAIN_FALL.get(), second(p, false), 1e-3, "falls out of a fight");
                wear(p, new ItemStack(KegareItems.BLINDFOLD.get()));
                assertEquals(-SixEyesConfig.STRAIN_FALL_COVERED.get(), second(p, false), 1e-3, "fastest covered");
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void eyewearChangesStrainAndNothingElse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "strain_only", 1000, 0.5, 0.5);
            try {
                ServerPlayer other = SightTest.player(server, "strain_only_seen", 600, 4.5, 0.5);
                ServerPlayer voidOne = SightTest.player(server, "strain_only_void", 0, -4.5, 0.5);
                SightTest.eyes(server, p, true);
                var players = Sight.players(p);
                var reads = Reads.build(p).glances();
                for (var item : new ItemStack[] {new ItemStack(KegareItems.BLINDFOLD.get()), new ItemStack(KegareItems.SUNGLASSES.get())}) {
                    wear(p, item);
                    assertEquals(players.dots().size(), Sight.players(p).dots().size(), "the same seen, covered");
                    assertEquals(reads, Reads.build(p).glances(), "the same Reads, covered");
                }
                assertTrue(other.isAlive() && voidOne.isAlive());
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void backgroundRmtHoldsItBackAndChannellingLowersIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "strain_rmt", 20000, 0.5, 0.5);
            try {
                SightTest.eyes(server, p, true);
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                Unlocks.grant(p, BackgroundRmt.ID);
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(RmtIds.ABILITY)));
                double before = Strain.data(p).strain();
                for (int i = 0; i < 20; i++) {
                    CombatHelper.markCombat(p);
                    BackgroundTaskService.tick(p);
                    Strain.tick(p);
                }
                assertEquals(SixEyesConfig.STRAIN_BUILD.get() * SixEyesConfig.BACKGROUND_RMT_BUILD.get(), Strain.data(p).strain() - before, 1e-3, "held back");
                BackgroundTaskService.setOn(p, 0, false);
                BackgroundTaskService.tick(p);

                Strain.set(p, 50);
                InnateTechniqueService.select(p, 1);
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                before = Strain.data(p).strain();
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(before - SixEyesConfig.RMT_STRAIN_RELIEF.get(), Strain.data(p).strain(), 1e-3, "channelling RMT lowers it");
                CastManager.stopAll(p, EndReason.CANCELLED);
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void theEdgeOnlyUncoveredOnlyAgainstSpecialGradeAndGoneAtTheTop(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "edge_bearer", 1000, 0.5, 0.5);
            try {
                ServerPlayer plain = SightTest.player(server, "edge_grade4", 1000, 4.5, 0.5);
                ServerPlayer special = SightTest.player(server, "edge_special", 1000, -4.5, 0.5);
                GradeService.set(special, Grade.SPECIAL_GRADE);
                SightTest.eyes(server, p, true);
                assertFalse(Edge.topTier(p, plain), "a Grade 4 isn't top-tier");
                assertTrue(Edge.topTier(p, special), "a Special Grade is");

                assertTrue(Strain.edgeAvailable(p), "uncovered: the edge is there");
                wear(p, new ItemStack(KegareItems.SUNGLASSES.get()));
                assertFalse(Strain.edgeAvailable(p), "sunglasses: no edge");
                wear(p, new ItemStack(KegareItems.BLINDFOLD.get()));
                assertFalse(Strain.edgeAvailable(p), "blindfold: no edge");
                wear(p, ItemStack.EMPTY);

                // At the top it's lost, until Strain cools below the threshold.
                Strain.set(p, Strain.MAX);
                assertFalse(Strain.edgeAvailable(p), "max Strain: lost");
                Strain.set(p, SixEyesConfig.EDGE_BACK_BELOW.get() + 5);
                assertFalse(Strain.edgeAvailable(p), "still lost above the threshold");
                Strain.set(p, SixEyesConfig.EDGE_BACK_BELOW.get() - 5);
                assertTrue(Strain.edgeAvailable(p), "back below it");

                // The extension hook: anyone a rule says counts.
                Edge.addTopTier((bearer, caster) -> caster == plain);
                assertTrue(Edge.topTier(p, plain), "the Shrine and the Director plug in here");
            } finally {
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    /** The real path: the tick's snapshot, then a cast key's stamp arriving. */
    @Test
    void theSparkGoesOutOnlyWhenItShould(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "spark_bearer", 1000, 0.5, 0.5);
            java.util.List<ServerPlayer> sentTo = new java.util.ArrayList<>();
            try {
                ServerPlayer plain = SightTest.player(server, "spark_grade4", 1000, 4.5, 0.5);
                ServerPlayer special = SightTest.player(server, "spark_special", 1000, -4.5, 0.5);
                GradeService.set(special, Grade.SPECIAL_GRADE);
                SightTest.eyes(server, p, true);
                Edge.sentHook = (to, spark) -> {
                    assertEquals(special.getId(), spark.entityId(), "the one about to cast");
                    sentTo.add(to);
                };
                Edge.snapshot(server);
                Edge.spark(plain);
                assertTrue(sentTo.isEmpty(), "a Grade 4's key press shows nothing");
                Edge.spark(special);
                assertEquals(java.util.List.of(p), sentTo, "a Special Grade's goes to the uncovered bearer, and nobody else");

                sentTo.clear();
                wear(p, new ItemStack(KegareItems.BLINDFOLD.get()));
                Edge.snapshot(server);
                Edge.spark(special);
                assertTrue(sentTo.isEmpty(), "covered: they see the cast when it starts, like everyone");
                wear(p, ItemStack.EMPTY);

                Strain.set(p, Strain.MAX);
                Edge.snapshot(server);
                Edge.spark(special);
                assertTrue(sentTo.isEmpty(), "at the top: lost");
                Strain.set(p, 0);
                special.moveTo(0.5 + SixEyesConfig.EDGE_RANGE.get() + 10, 120, 0.5, 0f, 0f);
                Edge.snapshot(server);
                Edge.spark(special);
                assertTrue(sentTo.isEmpty(), "out of range");
            } finally {
                Edge.sentHook = null;
                Edge.clear();
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void uncoveredAtTheTopTheRiskGrowsAndACollapseStunsAndHurts(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "collapse", 20000, 0.5, 0.5);
            try {
                SightTest.eyes(server, p, true);
                Strain.roll = () -> 1.0; // no collapse yet: watch the risk
                Strain.set(p, Strain.MAX);
                CombatHelper.markCombat(p);
                for (int i = 0; i < 20; i++) {
                    CombatHelper.markCombat(p);
                    Strain.tick(p);
                }
                double grown = Strain.data(p).risk();
                assertEquals(SixEyesConfig.RISK_GROWTH.get(), grown, 1e-6, "the risk grows while uncovered at the top");

                // Covered: it stops growing, and nothing rolls.
                wear(p, new ItemStack(KegareItems.BLINDFOLD.get()));
                Strain.roll = () -> 0.0;
                float hp = p.getHealth();
                for (int i = 0; i < 40; i++) {
                    CombatHelper.markCombat(p);
                    Strain.set(p, Strain.MAX);
                    Strain.tick(p);
                }
                assertEquals(grown, Strain.data(p).risk(), EPS, "covered: no growth");
                assertEquals(hp, p.getHealth(), EPS, "and no collapse");
                wear(p, ItemStack.EMPTY);

                // Channelling RMT lowers it.
                SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
                clearCombat(server);
                assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(RmtIds.ABILITY)));
                InnateTechniqueService.select(p, 1);
                Strain.data(p).risk = 0.5;
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                for (int i = 0; i < 20; i++) CastManager.tick(p);
                assertEquals(0.5 - SixEyesConfig.RMT_RISK_RELIEF.get(), Strain.data(p).risk(), 1e-3, "channelling lowers the risk");
                CastManager.stopAll(p, EndReason.CANCELLED);

                // A collapse: stunned, can't move, hurt through armour, and the risk starts over.
                p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
                p.setHealth(p.getMaxHealth());
                hp = p.getHealth();
                Strain.collapse(p, Strain.data(p));
                assertEquals(hp - SixEyesConfig.COLLAPSE_DAMAGE.get(), p.getHealth(), 1e-3, "damage armour doesn't stop");
                assertTrue(CombatHelper.isStunned(p), "no casting");
                assertTrue(Strain.moveHeld(p), "no moving");
                assertEquals(0, Strain.data(p).risk(), EPS, "the risk starts over");
                TestServer.advanceGameTime(server, (int) Math.round(SixEyesConfig.COLLAPSE_STUN.get() * 20) + 1);
                Strain.release(p);
                assertFalse(Strain.moveHeld(p), "and then they can move");
            } finally {
                Strain.roll = Math::random;
                SightTest.eyes(server, p, false);
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void strainGoesWithTheEyes(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = SightTest.player(server, "strain_lost", 1000, 0.5, 0.5);
            try {
                SightTest.eyes(server, p, true);
                Strain.set(p, 80);
                SightTest.eyes(server, p, false);
                assertEquals(0, Strain.data(p).strain(), EPS, "wiped when they go");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
