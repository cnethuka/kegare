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
    /** A Domain-length Burnout, as assumed. */
    static int domain() {
        return (int) Math.round(RmtConfig.DOMINION_BURNOUT_ASSUMED.get() * 20);
    }

    static ServerPlayer player(MinecraftServer server, String name) {
        TestServer.ensureOverworld(server);
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(20000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
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
            InnateTechniqueService.clearCooldowns(p); // (a channel stopped earlier left RMT's cooldown)
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

                // Per second of Burnout, a longer one costs more.
                double shortOne = BrainDamage.grow(0, 10, 20)[0], longOne = BrainDamage.grow(0, 10, 60)[0];
                assertTrue(longOne > shortOne, "10 s off a 60 s Burnout costs more than 10 s off a 20 s one");

                // The anchor: about four repairs of a Domain-length Burnout fit (a fifth Domain, not a sixth).
                assertEquals(4, BrainDamage.repairsThatFit(RmtConfig.DOMINION_BURNOUT_ASSUMED.get()), "four Domain repairs");
                assertTrue(BrainDamage.repairsThatFit(20) > 4, "more of Standstill's 20 s");
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
