package com.cnethuka.kegare.melee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.melee.MeleeIntentPayload.Action;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Fight;
import com.cnethuka.kegare.progression.FightTracker;
import com.cnethuka.kegare.progression.MeleeMoves;
import com.cnethuka.kegare.progression.ProgressionConfig;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Perfect parries, clean dodges and Strikes against real players, as moves in the fight record. */
@ExtendWith(EphemeralTestServerProvider.class)
class MeleeAssessmentTest {
    private static ServerLevel world(MinecraftServer server) {
        TestServer.ensureOverworld(server);
        return server.overworld();
    }

    private static int ground(ServerLevel level) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        ServerLevel level = world(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(2.5, ground(level), 8.5, -90f, 0f);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static void guard(ServerPlayer p) {
        MeleeIntentPayload.apply(p, new MeleeIntentPayload(Action.PARRY_PRESS, (byte) 0, (byte) 0));
    }

    private static void hit(LivingEntity victim, LivingEntity attacker) {
        victim.invulnerableTime = 0;
        victim.hurt(attacker instanceof ServerPlayer p ? p.damageSources().playerAttack(p) : attacker.damageSources().mobAttack(attacker), 4f);
    }

    /** What their swing would have been: a fake player doesn't tick, so its attack never charges by itself. */
    private static void swung(ServerPlayer attacker, float charge) {
        MeleeState state = MeleeLayer.state(attacker);
        state.swingCharge = charge;
        state.swingTick = attacker.level().getGameTime();
    }

    /** Opens their fight, which picks up whatever was waiting for it, and reads what {@code player} is on record for. */
    private static List<String> moves(ServerPlayer player, ServerPlayer opponent) {
        FightTracker.damage(opponent, player, 1.0);
        Fight fight = FightTracker.between(player, opponent);
        assertNotNull(fight);
        return fight.side(player.getUUID()).melee().stream().map(Fight.Use::id).toList();
    }

    @Test
    void aPerfectParryOfAFullyChargedSwingCounts(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerPlayer defender = player(server, "parry_counts");
            ServerPlayer attacker = player(server, "parry_counts_attacker");
            guard(defender);
            swung(attacker, 1f);
            float before = defender.getHealth();
            hit(defender, attacker);
            assertEquals(before, defender.getHealth(), "parried");
            assertNull(FightTracker.between(defender, attacker), "a parry doesn't start a fight by itself");
            assertEquals(List.of(MeleeMoves.PERFECT_PARRY), moves(defender, attacker));
        });
    }

    @Test
    void aTappedSwingIsStillParriedButDoesntCount(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerPlayer defender = player(server, "parry_tapped");
            ServerPlayer attacker = player(server, "parry_tapped_attacker");
            guard(defender);
            attacker.resetAttackStrengthTicker();
            float before = defender.getHealth();
            attacker.attack(defender); // the real thing, so this also proves the game's swing reaches noteSwing with its charge
            assertEquals(attacker.level().getGameTime(), MeleeLayer.state(attacker).swingTick, "the swing was noted");
            assertTrue(MeleeLayer.state(attacker).swingCharge < 1f, "not charged: " + MeleeLayer.state(attacker).swingCharge);
            assertEquals(before, defender.getHealth(), "the parry itself works as ever");
            assertTrue(ReinforcedStrike.isReady(defender), "and earns the Strike");
            assertEquals(List.of(), moves(defender, attacker));
        });
    }

    @Test
    void ninetyTwoPercentIsntFullyCharged(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerPlayer defender = player(server, "parry_almost");
            ServerPlayer attacker = player(server, "parry_almost_attacker");
            guard(defender);
            swung(attacker, 0.92f); // what the game calls full strength, and a swing eleven ticks after the last with a sword
            hit(defender, attacker);
            assertTrue(ReinforcedStrike.isReady(defender), "parried");
            assertEquals(List.of(), moves(defender, attacker));
        });
    }

    @Test
    void theChargeIsThatOfTheSwingBehindThisHit(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerPlayer defender = player(server, "parry_stale");
            ServerPlayer attacker = player(server, "parry_stale_attacker");
            swung(attacker, 1f);
            TestServer.advanceGameTime(server, 5);
            guard(defender);
            hit(defender, attacker); // no swing this tick: a hit from something else in their name
            assertEquals(List.of(), moves(defender, attacker));
        });
    }

    @Test
    void aMobsSwingDoesntCount(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerLevel level = world(server);
            ServerPlayer defender = player(server, "parry_mob");
            Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
            zombie.moveTo(4.5, ground(level), 8.5, 0f, 0f);
            zombie.setNoAi(true);
            level.addFreshEntity(zombie);
            try {
                guard(defender);
                hit(defender, zombie);
                assertTrue(ReinforcedStrike.isReady(defender), "parried");
                ServerPlayer someone = player(server, "parry_mob_other");
                assertEquals(List.of(), moves(defender, someone));
            } finally {
                zombie.discard();
            }
        });
    }

    private static void dashAside(ServerPlayer p, ServerLevel level) {
        MeleeIntentPayload.apply(p, new MeleeIntentPayload(Action.DASH, (byte) 0, (byte) 0));
        MeleeLayer.state(p).dashDirection = null;
        p.moveTo(p.getX(), ground(level), p.getZ() + 4, p.getYRot(), 0f);
    }

    @Test
    void aCleanDodgeOfAPlayerCounts(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "dodge_counts");
            p.moveTo(8.5, ground(level), 2.5, 0f, 0f);
            ServerPlayer attacker = player(server, "dodge_counts_attacker");
            attacker.moveTo(11.0, ground(level), 2.5, 90f, 0f);
            attacker.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
            level.addNewPlayer(attacker);
            try {
                dashAside(p, level);
                attacker.swing(InteractionHand.MAIN_HAND);
                MeleeLayer.tick(p);
                assertTrue(ReinforcedStrike.isReady(p), "dodged");
                assertEquals(List.of(MeleeMoves.CLEAN_DODGE), moves(p, attacker));
            } finally {
                attacker.swinging = false;
                level.removePlayerImmediately(attacker, Entity.RemovalReason.DISCARDED);
            }
        });
    }

    @Test
    void aStrikeIsItsOwnMoveNotAlsoTheWeaponsHit(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerLevel level = world(server);
            ServerPlayer p = player(server, "strike_counts");
            ServerPlayer target = player(server, "strike_counts_target");
            ReinforcedStrike.earn(p, MeleeLayer.state(p));
            hit(target, p);
            Fight fight = FightTracker.between(p, target);
            assertNotNull(fight, "damage opens the fight");
            assertEquals(List.of(MeleeMoves.REINFORCED_STRIKE), fight.side(p.getUUID()).melee().stream().map(Fight.Use::id).toList());
            TestServer.advanceGameTime(server, 1);
            hit(target, p);
            assertEquals(List.of(MeleeMoves.REINFORCED_STRIKE, "fist"), fight.side(p.getUUID()).melee().stream().map(Fight.Use::id).toList(), "the next hit is plain");
        });
    }

    @Test
    void movesWaitForTheFightOnlySoLong(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            ServerPlayer p = player(server, "opening_moves");
            ServerPlayer other = player(server, "opening_moves_other");
            FightTracker.meleeMove(p, other, MeleeMoves.CLEAN_DODGE);
            TestServer.advanceGameTime(server, (int) ProgressionConfig.ticks(ProgressionConfig.OPENING_CAST_SECONDS) + 1);
            FightTracker.meleeMove(p, other, MeleeMoves.PERFECT_PARRY);
            assertNull(FightTracker.between(p, other), "they don't open a fight");
            long parried = server.overworld().getGameTime();
            TestServer.advanceGameTime(server, 10);
            assertEquals(List.of(MeleeMoves.PERFECT_PARRY), moves(p, other), "the old one has gone, the recent one's picked up");
            assertEquals(parried, FightTracker.between(p, other).side(p.getUUID()).melee().get(0).tick(), "with when it really happened");
        });
    }
}
