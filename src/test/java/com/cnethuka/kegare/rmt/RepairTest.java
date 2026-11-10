package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.backgroundtask.BackgroundRmt;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskResult;
import com.cnethuka.kegare.backgroundtask.BackgroundTaskService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeManipulation;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutResult;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Repairing a burnt-out technique with RMT, and the brain's limit (DESIGN §3.17). */
@ExtendWith(EphemeralTestServerProvider.class)
class RepairTest {
    private static final double EPS = 1.0e-3;
    /** A Domain-length Burnout ({@code dominionBurnoutAssumed}), in ticks. */
    static int domain() {
        return (int) Math.round(RmtConfig.DOMINION_BURNOUT_ASSUMED.get() * 20);
    }

    static ServerPlayer player(MinecraftServer server, String name) {
        return player(server, name, Rime.RIME.get());
    }

    static ServerPlayer player(MinecraftServer server, String name, InnateTechnique technique) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(20000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, technique);
        MaliceService.refill(p);
        p.moveTo(0.5, 120, 0.5, 0f, 0f);
        p.setHealth(p.getMaxHealth());
        SharedAbilityService.unlock(p, Rmt.REVERSE_MALICE_TECHNIQUE);
        assertEquals(LoadoutResult.OK, LoadoutService.edit(p, LoadoutService.Action.SET, 1, 0, Optional.of(RmtIds.ABILITY)));
        InnateTechniqueService.select(p, 1);
        return p;
    }

    private static void channel(ServerPlayer p, int ticks) {
        if (CastManager.currentCast(p).isEmpty()) {
            InnateTechniqueService.clearCooldown(p, Rmt.REVERSE_MALICE_TECHNIQUE.cooldownKey()); // a channel stopped earlier left RMT's cooldown; clear only that
            CastManager.handleIntent(p, CastIntent.CAST_PRESS);
        }
        for (int i = 0; i < ticks; i++) {
            MaliceService.refill(p);
            CastManager.tick(p);
        }
    }

    @Test
    void itRepairsAtItsRateTimesOutputAndTheBodyWaits(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "repair_rate");
                Burnout.apply(p, domain());
                p.setHealth(12f);
                channel(p, 20);
                double perSecond = RmtConfig.REPAIR_RATE.get() * RmtOutput.of(p);
                assertEquals(domain() - perSecond * 20, Burnout.remaining(p), 1.0, "repairRate x output seconds of Burnout a second");
                assertEquals(12f - RmtConfig.REPAIR_DAMAGE.get(), p.getHealth(), EPS, "no healing while repairing; repairDamage instead");
                assertTrue(Repair.isRepairing(p), "the tell is on");

                // Injury fatigue halves the output: half as fast.
                RmtFatigue.set(p, 0.5);
                int before = Burnout.remaining(p);
                channel(p, 20);
                assertEquals(before - perSecond * 0.5 * 20, Burnout.remaining(p), 1.0, "output, fatigue and all");
                RmtFatigue.set(p, 0);

                // Armour doesn't stop the damage.
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
                float hp = p.getHealth();
                channel(p, 20);
                assertEquals(hp - RmtConfig.REPAIR_DAMAGE.get(), p.getHealth(), EPS, "through armour");

                // Repaired: the body heals again, and the tell goes.
                Burnout.clear(p);
                hp = p.getHealth();
                channel(p, 20);
                assertTrue(p.getHealth() > hp, "healing once the Burnout is gone");
                assertFalse(Repair.isRepairing(p), "no nosebleed");
                CastManager.stopAll(p, EndReason.CANCELLED);
            } finally {
                Repair.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void eachRepairCostsTheBrainMoreThanTheLastAndLongerOnesMore(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "repair_brain");
                BrainDamage.set(p, 0);
                Burnout.apply(p, domain());
                channel(p, 400);
                assertFalse(Burnout.isBurntOut(p), "setup: repaired");
                double first = BrainDamage.get(p);
                assertTrue(first > 0, "the meter grew");
                CastManager.stopAll(p, EndReason.CANCELLED);
                Burnout.apply(p, domain());
                channel(p, 400);
                double second = BrainDamage.get(p) - first;
                assertTrue(second > first, "the second repair added more than the first: " + first + " then " + second);
                CastManager.stopAll(p, EndReason.CANCELLED);

                // A share of a Burnout costs that share of a full repair, whatever its length: a second of a short one costs more.
                assertEquals(BrainDamage.grow(0, 15, 60)[0], BrainDamage.grow(0, 5, 20)[0], EPS, "a quarter of 60 s costs what a quarter of 20 s does");
                assertTrue(BrainDamage.grow(0, 10, 20)[0] > BrainDamage.grow(0, 10, 60)[0], "10 s is half of a 20 s Burnout and a sixth of a 60 s one");

                // The anchor: four full repairs fit, and the fifth stops partway (a fifth Domain, not a sixth), whatever the technique.
                for (double length : new double[] {20, RmtConfig.DOMINION_BURNOUT_ASSUMED.get(), 7, 180}) {
                    assertEquals(4, BrainDamage.repairsThatFit(length), "four full repairs of a " + length + " s Burnout");
                }
                double meter = 0;
                for (int i = 0; i < 4; i++) meter = BrainDamage.grow(meter, 20, 20)[0];
                double fifth = BrainDamage.grow(meter, 20, 20)[1];
                assertTrue(fifth > 0 && fifth < 20, "the fifth stops partway: " + fifth + " s of 20");
            } finally {
                Repair.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void backgroundRmtsShorteningCountsToo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "repair_bg");
                Unlocks.grant(p, BackgroundRmt.ID);
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "setup: Background RMT running");
                BrainDamage.set(p, 0);
                Burnout.apply(p, 400);
                assertEquals(300, Burnout.remaining(p), "shortened by a quarter");
                assertEquals(BrainDamage.grow(0, 5, 20)[0], BrainDamage.get(p), EPS, "and the 5 s it took off are on the meter");
                assertEquals(BrainDamage.grow(0, 15, 60)[0], BrainDamage.get(p), EPS, "a quarter of the total, as a quarter of any Burnout");

                // Repairing what's left costs the rest: the whole Burnout, shortening and repair together, costs one full repair.
                channel(p, 200);
                assertFalse(Burnout.isBurntOut(p), "setup: repaired");
                assertEquals(BrainDamage.grow(0, 60, 60)[0], BrainDamage.get(p), EPS, "one full repair's worth, no more");
                CastManager.stopAll(p, EndReason.CANCELLED);

                // Full: no shortening either. Nothing gets around the limit.
                Burnout.clear(p);
                BrainDamage.set(p, BrainDamage.MAX);
                Burnout.apply(p, 400);
                assertEquals(400, Burnout.remaining(p), "full: the whole Burnout");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void everyFullRepairCostsTheSameWhateverItsLength(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "repair_same");
                double[] costs = new double[2];
                int[] lengths = {20 * 20, 60 * 20};
                for (int i = 0; i < 2; i++) {
                    BrainDamage.set(p, 0);
                    Burnout.apply(p, lengths[i]);
                    channel(p, 400);
                    assertFalse(Burnout.isBurntOut(p), "setup: repaired");
                    costs[i] = BrainDamage.get(p);
                    CastManager.stopAll(p, EndReason.CANCELLED);
                }
                assertEquals(BrainDamage.grow(0, 60, 60)[0], costs[1], EPS, "a 60 s Burnout: one full repair of dominionBurnoutAssumed");
                assertEquals(costs[1], costs[0], EPS, "a 20 s Standstill Burnout costs what a 60 s one does");

                // Its own countdown took most of it, and the repair finishes it off: getting the ultimate back still costs a full repair.
                BrainDamage.set(p, 0);
                Burnout.apply(p, lengths[0]);
                for (int i = 0; i < 300; i++) Burnout.tick(p);
                channel(p, 400);
                assertFalse(Burnout.isBurntOut(p), "setup: repaired");
                assertEquals(costs[0], BrainDamage.get(p), EPS, "the share it ran out by itself is paid for when it's finished off");
                CastManager.stopAll(p, EndReason.CANCELLED);

                // Running out by itself costs nothing, and a repair that stops early pays only for what it repaired.
                BrainDamage.set(p, 0);
                Burnout.apply(p, lengths[0]);
                for (int i = 0; i < lengths[0]; i++) Burnout.tick(p);
                assertEquals(0, BrainDamage.get(p), EPS, "ran out by itself: free");
                Burnout.apply(p, lengths[0]);
                for (int i = 0; i < 100; i++) Burnout.tick(p);
                int before = Burnout.remaining(p);
                channel(p, 10);
                CastManager.stopAll(p, EndReason.RELEASED);
                double repaired = (before - Burnout.remaining(p)) / 20.0;
                assertEquals(BrainDamage.grow(0, repaired, 20)[0], BrainDamage.get(p), EPS, "stopped early: its share, not the countdown's");
                double partial = BrainDamage.get(p);
                for (int i = 0; i < lengths[0] && Burnout.isBurntOut(p); i++) Burnout.tick(p);
                assertEquals(partial, BrainDamage.get(p), EPS, "and nothing more when the rest runs out");

                // Four in a row from empty, the fifth stopping partway.
                BrainDamage.set(p, 0);
                for (int i = 1; i <= 4; i++) {
                    Burnout.apply(p, lengths[0]);
                    channel(p, 400);
                    assertFalse(Burnout.isBurntOut(p), "full repair " + i);
                    CastManager.stopAll(p, EndReason.CANCELLED);
                }
                Burnout.apply(p, lengths[0]);
                channel(p, 400);
                assertTrue(BrainDamage.isFull(p) && Burnout.isBurntOut(p) && Burnout.remaining(p) < lengths[0], "the fifth stops partway");
                CastManager.stopAll(p, EndReason.CANCELLED);
            } finally {
                Repair.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    /** Time Manipulation with RMT, Standstill and Point Maker cooling down, and a Burnout. */
    private static ServerPlayer cooling(MinecraftServer server, String name, int burnout) {
        ServerPlayer p = player(server, name, TimeManipulation.TIME_MANIPULATION.get());
        BrainDamage.set(p, 0);
        InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
        InnateTechniqueService.startCooldown(p, TimeManipulation.POINT_MAKER);
        Burnout.apply(p, burnout);
        assertTrue(ultimateCooling(p) && pointMakerCooling(p) > 0, "setup: both cooling");
        return p;
    }

    private static boolean ultimateCooling(ServerPlayer p) {
        return InnateTechniqueService.cooldownRemaining(p, TimeManipulation.STANDSTILL) > 0;
    }

    private static int pointMakerCooling(ServerPlayer p) {
        return InnateTechniqueService.cooldownRemaining(p, TimeManipulation.POINT_MAKER);
    }

    /** The Burnout's own countdown, as the server ticks it, with the channel running or not. */
    private static void runOut(ServerPlayer p, boolean channelling) {
        for (int i = 0; i < 2000 && Burnout.isBurntOut(p); i++) {
            Burnout.tick(p);
            if (channelling) {
                MaliceService.refill(p);
                CastManager.tick(p);
            }
        }
        assertFalse(Burnout.isBurntOut(p), "setup: it ran out");
    }

    @Test
    void aFinishedRepairGivesTheUltimateBackAndNothingElse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = cooling(server, "repair_ult", 400);
                int pointMaker = pointMakerCooling(p);
                channel(p, 200);
                assertFalse(Burnout.isBurntOut(p), "setup: repaired");
                assertFalse(ultimateCooling(p), "the ultimate's cooldown is gone");
                assertEquals(pointMaker, pointMakerCooling(p), "another ability's isn't touched");
                assertTrue(CastManager.currentCast(p).isPresent(), "the channel goes on, healing now");
                CastManager.stopAll(p, EndReason.CANCELLED);

                // Unbroken right to the end while the Burnout's own countdown takes the last tick: the repair still finished it.
                double rate = RmtConfig.REPAIR_RATE.get();
                try {
                    RmtConfig.REPAIR_RATE.set(0.001);
                    InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                    Burnout.apply(p, 10);
                    channel(p, 1);
                    assertTrue(Repair.isRepairing(p), "setup: repairing");
                    BrainDamage.set(p, 0);
                    runOut(p, true);
                    channel(p, 1);
                    assertFalse(ultimateCooling(p), "repaired to the end: the ultimate's back");
                    assertEquals(BrainDamage.grow(0, 0.5, 0.5)[0], BrainDamage.get(p), EPS, "for a full repair's price");
                } finally {
                    RmtConfig.REPAIR_RATE.set(rate);
                    CastManager.stopAll(p, EndReason.CANCELLED);
                }
            } finally {
                Repair.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aBurnoutThatRunsOutOrARepairThatStopsEarlyLeavesTheCooldown(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                // Runs out by itself.
                ServerPlayer p = cooling(server, "repair_none", 60);
                runOut(p, false);
                assertTrue(ultimateCooling(p), "a Burnout that runs out leaves the cooldown");

                // Let go partway, and then it runs out.
                InnateTechniqueService.clearCooldowns(p);
                InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                Burnout.apply(p, 400);
                channel(p, 10);
                assertTrue(Repair.isRepairing(p), "setup: repairing");
                CastManager.stopAll(p, EndReason.RELEASED);
                runOut(p, false);
                assertTrue(ultimateCooling(p), "let go: nothing cleared");

                // Stunned partway, and then it runs out with the channel pressed again.
                InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                Burnout.apply(p, 400);
                channel(p, 10);
                assertTrue(Repair.isRepairing(p), "setup: repairing");
                com.cnethuka.kegare.combat.CombatHelper.stun(p, 40);
                CastManager.tick(p);
                assertTrue(CastManager.currentCast(p).isEmpty(), "setup: the stun ended the channel");
                runOut(p, false);
                assertTrue(ultimateCooling(p), "stunned: nothing cleared");
                com.cnethuka.kegare.combat.CombatHelper.clearStun(p);

                // Out of Malice partway.
                InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                Burnout.apply(p, 400);
                channel(p, 5);
                assertTrue(Repair.isRepairing(p), "setup: repairing");
                MaliceService.setCurrent(p, 0);
                for (int i = 0; i < 40 && CastManager.currentCast(p).isPresent(); i++) CastManager.tick(p);
                assertTrue(CastManager.currentCast(p).isEmpty(), "setup: out of Malice ended it");
                runOut(p, false);
                assertTrue(ultimateCooling(p), "out of Malice: nothing cleared");

                // The brain filling partway, with the channel still running when the Burnout runs out.
                MaliceService.refill(p);
                InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                BrainDamage.set(p, 99.0);
                Burnout.apply(p, 400);
                channel(p, 60);
                assertTrue(BrainDamage.isFull(p) && Burnout.isBurntOut(p), "setup: the brain filled first");
                runOut(p, true);
                assertTrue(ultimateCooling(p), "the brain stopped it: nothing cleared");
                CastManager.stopAll(p, EndReason.CANCELLED);
            } finally {
                Repair.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void backgroundRmtShortensBurnoutButNeverTheCooldown(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "repair_bg_ult", TimeManipulation.TIME_MANIPULATION.get());
                Unlocks.grant(p, BackgroundRmt.ID);
                assertEquals(BackgroundTaskResult.OK, BackgroundTaskService.set(p, 0, BackgroundRmt.ID));
                BackgroundTaskService.tick(p);
                assertTrue(BackgroundRmt.running(p), "setup: Background RMT running");
                BrainDamage.set(p, 0);
                InnateTechniqueService.startCooldown(p, TimeManipulation.STANDSTILL);
                Burnout.apply(p, 400);
                assertEquals(300, Burnout.remaining(p), "setup: shortened");
                assertTrue(ultimateCooling(p), "shortening clears nothing");
                for (int i = 0; i < 400 && Burnout.isBurntOut(p); i++) {
                    Burnout.tick(p);
                    BackgroundTaskService.tick(p);
                }
                assertTrue(ultimateCooling(p), "nor does running out with it on");
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void fullItRefusesEvenForTheSixEyes(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerPlayer p = player(server, "repair_full");
            try {
                TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
                assertEquals(TraitService.AddResult.ADDED, TraitService.add(p, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN));
                BrainDamage.set(p, BrainDamage.MAX);
                assertTrue(BrainDamage.refusesDominion(p), "the Dominion hook says no");
                Burnout.apply(p, domain());
                p.setHealth(10f);
                channel(p, 20);
                assertEquals(domain(), Burnout.remaining(p), "no repair: the Burnout runs its full length");
                assertTrue(p.getHealth() > 10f, "RMT goes to the body instead");
                assertFalse(Repair.isRepairing(p), "no nosebleed");
                CastManager.stopAll(p, EndReason.CANCELLED);

                // Nearly full: it repairs until the meter's full, then stops.
                Burnout.apply(p, domain());
                BrainDamage.set(p, 99.0);
                int before = Burnout.remaining(p);
                channel(p, 100);
                assertTrue(BrainDamage.isFull(p), "full now");
                assertTrue(Burnout.remaining(p) < before && Burnout.remaining(p) > 0, "part of it repaired, the rest stands");
                CastManager.stopAll(p, EndReason.CANCELLED);
            } finally {
                TraitService.remove(p, KegareTraits.SIX_EYES.get(), Trait.RemoveReason.ADMIN);
                Repair.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void itHealsOutOfAFightAndASleepClearsIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = player(server, "repair_heal");
                BrainDamage.set(p, 50);
                CombatHelper.markCombat(p);
                for (int i = 0; i < 20; i++) BrainDamage.tick(p);
                assertEquals(50, BrainDamage.get(p), EPS, "not in a fight");
                TestServer.advanceGameTime(server, 20 * 60);
                for (int i = 0; i < 20; i++) BrainDamage.tick(p);
                assertEquals(50 - RmtConfig.BRAIN_HEAL_PER_SECOND.get(), BrainDamage.get(p), EPS, "brainHealPerSecond out of one");

                p.startSleeping(BlockPos.containing(p.position()));
                BrainDamage.nightSlept(p.serverLevel());
                assertEquals(0, BrainDamage.get(p), EPS, "a night's sleep clears it");
                p.stopSleeping();
            } finally {
                TestServer.removePlayers(server);
            }
        });
    }
}
