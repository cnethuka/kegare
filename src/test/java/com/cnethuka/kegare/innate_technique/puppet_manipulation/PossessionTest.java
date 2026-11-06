package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Puppet Manipulation, part 2: Possess and every way back (R, shell hit, puppet death, distance, death, dimension, reroll). */
@ExtendWith(EphemeralTestServerProvider.class)
class PossessionTest {
    private static final double Y = PuppetTest.Y;
    private static final double EPS = 1.0e-3;

    @Test
    void possessAndReturn(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = setUp(server);
            ServerPlayer p = owner(server, level, "possess_return");
            try {
                p.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
                p.getInventory().add(new ItemStack(Items.COBBLESTONE, 32));
                p.setHealth(15f);
                PuppetEntity puppet = puppetInFront(server, p, PuppetConfig.ticks(PuppetConfig.CREATION_HALF_CHARGE));
                puppet.setHealth(3f);
                Vec3 bodyAt = p.position();
                double before = PuppetTest.malice(p);

                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p), "possessing");
                assertEquals(before - PuppetConfig.POSSESS_COST.get(), PuppetTest.malice(p), EPS, "paid");
                assertTrue(puppet.isPossessed(), "the puppet is inside them");
                assertEquals(puppet.position().x, p.getX(), 0.3, "the player is where the puppet stood");
                assertEquals(PuppetConfig.SCALE.get(), p.getAttributeValue(Attributes.SCALE), EPS, "the puppet's size");
                assertEquals(PuppetTier.HALF.health(), p.getMaxHealth(), EPS, "the puppet's max health");
                assertEquals(3f, p.getHealth(), EPS, "and its health");
                assertTrue(p.getInventory().isEmpty(), "no items");
                PuppetShellEntity shell = shellOf(level, p);
                assertEquals(bodyAt.x, shell.getX(), EPS, "the body stays where it was");
                assertEquals(p.getUUID(), shell.ownerId());
                for (int i = 0; i < 40; i++) puppet.tick();
                assertFalse(puppet.isSpinning(), "a possessed puppet never spins");

                // No casting while possessing (and no healing).
                CastManager.handleIntent(p, CastIntent.CAST_PRESS);
                assertNull(CastManager.state(p).casting(), "Puppet Creation can't even start charging");
                p.heal(2f);
                assertEquals(3f, p.getHealth(), EPS, "a puppet doesn't heal");

                // R returns: everything back.
                p.moveTo(p.getX() + 3, Y, p.getZ(), p.getYRot(), 0f);
                Vec3 leftAt = p.position();
                p.setHealth(2f);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertFalse(Possession.isPossessing(p), "returned");
                assertEquals(bodyAt.x, p.getX(), EPS, "back at the body");
                assertEquals(1.0, p.getAttributeValue(Attributes.SCALE), EPS, "real size");
                assertEquals(20f, p.getMaxHealth(), EPS, "real max health");
                assertEquals(15f, p.getHealth(), EPS, "real health");
                assertEquals(1, count(p, Items.DIAMOND_SWORD), "the sword is back");
                assertEquals(32, count(p, Items.COBBLESTONE), "and the cobblestone");
                assertTrue(shell.isRemoved(), "the shell is gone");
                assertFalse(puppet.isPossessed(), "the puppet is out");
                assertEquals(leftAt.x, puppet.getX(), EPS, "where it was left");
                assertEquals(2f, puppet.getHealth(), EPS, "with what health it was left");
                assertTrue(puppet.isAlive());
            } finally {
                cleanUp(server, level, p);
            }
        });
    }

    /**
     * The possession slide: a hidden puppet trails just behind its possessor on the client, and vanilla lets any mob on the
     * client shove the players it overlaps, so it pushed them along for as long as it kept up. A possessed puppet pushes nothing.
     * The server's copy has noPhysics set, which isn't synced, so it's cleared here to stand for the client's.
     */
    @Test
    void aPossessedPuppetPushesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = setUp(server);
            ServerPlayer p = owner(server, level, "possess_push");
            net.minecraft.world.entity.animal.Cow cow = new net.minecraft.world.entity.animal.Cow(net.minecraft.world.entity.EntityType.COW, level);
            try {
                PuppetEntity puppet = puppetInFront(server, p, PuppetConfig.ticks(PuppetConfig.CREATION_HALF_CHARGE));
                cow.moveTo(puppet.getX() + 0.2, puppet.getY(), puppet.getZ(), 0f, 0f);
                level.addFreshEntity(cow);

                Possession.possess(p, puppet);
                assertTrue(puppet.isPossessed(), "setup: inside them");
                puppet.noPhysics = false;
                cow.setDeltaMovement(Vec3.ZERO);
                puppet.pushEntities();
                assertEquals(Vec3.ZERO, cow.getDeltaMovement(), "hidden inside its possessor, it shoves nobody");

                Possession.release(p, Possession.Return.RETURNED);
                assertFalse(puppet.isPossessed(), "setup: out again");
                cow.moveTo(puppet.getX() + 0.2, puppet.getY(), puppet.getZ(), 0f, 0f);
                cow.setDeltaMovement(Vec3.ZERO);
                puppet.pushEntities();
                assertTrue(cow.getDeltaMovement().horizontalDistance() > 0, "back out, it's a body like any other");
            } finally {
                cow.discard();
                cleanUp(server, level, p);
            }
        });
    }

    @Test
    void snapBacks(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = setUp(server);
            server.setPvpAllowed(true);
            ServerPlayer p = damageable(server, "possess_snap");
            level.addNewPlayer(p);
            prepare(p);
            ServerPlayer attacker = damageable(server, "possess_attacker");
            level.addNewPlayer(attacker);
            try {
                // The body is hit: snap back, and the hit lands on the real body.
                p.setHealth(15f);
                PuppetEntity puppet = puppetInFront(server, p, 0);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                PuppetShellEntity shell = shellOf(level, p);
                assertTrue(shell.hurt(level.damageSources().playerAttack(attacker), 3f), "the hit landed");
                assertFalse(Possession.isPossessing(p), "snapped back");
                assertEquals(12f, p.getHealth(), EPS, "and took the hit");
                assertTrue(puppet.isAlive(), "the puppet is fine");

                // The puppet dies: snap back with backlash (nausea, a stun, Malice lost); the player lives.
                InnateTechniqueService.clearCooldowns(p);
                MaliceService.refill(p);
                p.invulnerableTime = 0;
                aimAt(p, puppet);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                double malice = PuppetTest.malice(p);
                p.invulnerableTime = 0;
                p.hurt(level.damageSources().playerAttack(attacker), 50f);
                assertTrue(p.isAlive(), "the player doesn't die");
                assertFalse(Possession.isPossessing(p), "snapped back");
                assertEquals(12f, p.getHealth(), EPS, "with their real health");
                assertTrue(puppet.isRemoved(), "the puppet is destroyed");
                assertTrue(p.hasEffect(MobEffects.CONFUSION), "backlash: nausea");
                assertTrue(CombatHelper.isStunned(p), "a stun");
                assertEquals(malice - PuppetConfig.BACKLASH_MALICE.get(), PuppetTest.malice(p), EPS, "and Malice lost");
                CombatHelper.clearStun(p);
                p.removeAllEffects();

                // Too far from the body.
                InnateTechniqueService.clearCooldowns(p);
                PuppetEntity second = puppetInFront(server, p, 0);
                InnateTechniqueService.clearCooldowns(p);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                p.moveTo(p.getX() + PuppetConfig.POSSESS_MAX_DISTANCE.get() + 2, Y, p.getZ());
                Possession.tick(p);
                assertFalse(Possession.isPossessing(p), "too far: snapped back");
                assertTrue(second.isAlive(), "no harm done to the puppet");

                // A dimension change: back in the body first.
                InnateTechniqueService.clearCooldowns(p);
                second.moveTo(p.getX() + 4, Y, p.getZ(), 90f, 0f); // it was left where they wandered off to
                aimAt(p, second);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                NeoForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(p, Level.NETHER));
                assertFalse(Possession.isPossessing(p), "returned before travelling");

                // Rerolling away while possessing: back in the body, then every puppet dissolves.
                InnateTechniqueService.clearCooldowns(p);
                aimAt(p, second);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
                assertFalse(Possession.isPossessing(p), "returned");
                assertEquals(20f, p.getMaxHealth(), EPS);
                assertTrue(second.isRemoved(), "and the puppets dissolved");
            } finally {
                level.removePlayerImmediately(attacker, Entity.RemovalReason.DISCARDED);
                cleanUp(server, level, p);
            }
        });
    }

    @Test
    void realDeathAndCrashRestore(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = setUp(server);
            ServerPlayer p = damageable(server, "possess_death");
            level.addNewPlayer(p);
            prepare(p);
            GameRules.BooleanValue keep = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
            boolean kept = keep.get();
            keep.set(true, server);
            try {
                // A crash mid-possession: the shell and the puppet's hiding are gone, only the saved record is left.
                p.getInventory().add(new ItemStack(Items.APPLE, 5));
                Vec3 bodyAt = p.position();
                PuppetEntity puppet = puppetInFront(server, p, 0);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                shellOf(level, p).discard();
                Possession.clear();
                Possession.restoreOnLogin(p);
                assertFalse(Possession.isPossessing(p), "restored on login");
                assertEquals(bodyAt.x, p.getX(), EPS, "at the body");
                assertEquals(5, count(p, Items.APPLE), "with the items");
                assertEquals(1.0, p.getAttributeValue(Attributes.SCALE), EPS);

                // A real death (/kill) while possessing: back in the body first, then the death goes ahead there.
                InnateTechniqueService.clearCooldowns(p);
                aimAt(p, puppet);
                CastManager.handleIntent(p, CastIntent.SPECIAL_PRESS);
                assertTrue(Possession.isPossessing(p));
                p.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                assertFalse(Possession.isPossessing(p), "returned first");
                assertEquals(bodyAt.x, p.getX(), EPS, "died at the body");
                assertTrue(p.isDeadOrDying(), "and died");
                assertEquals(5, count(p, Items.APPLE), "with their own inventory (keepInventory here)");
            } finally {
                keep.set(kept, server);
                cleanUp(server, level, p);
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    static ServerLevel setUp(MinecraftServer server) {
        TestServer.ensureOverworld(server);
        ServerLevel level = server.overworld();
        PuppetTest.loadArena(level);
        return level;
    }

    static ServerPlayer owner(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer p = PuppetTest.owner(server, level, name);
        return p;
    }

    static void prepare(ServerPlayer p) {
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, PuppetManipulation.PUPPET_MANIPULATION.get());
        InnateTechniqueService.select(p, 0);
        p.moveTo(0.5, Y, 0.5, -90f, 0f);
        p.setYHeadRot(-90f);
        MaliceService.refill(p);
    }

    /** Makes a puppet and puts it 4 blocks in front of the owner, who looks down at it. */
    static PuppetEntity puppetInFront(MinecraftServer server, ServerPlayer p, int chargeTicks) {
        PuppetEntity puppet = PuppetTest.create(server, p, chargeTicks);
        InnateTechniqueService.clearCooldowns(p);
        MaliceService.refill(p);
        puppet.moveTo(p.getX() + 4, p.getY(), p.getZ(), 90f, 0f);
        aimAt(p, puppet);
        return puppet;
    }

    static void aimAt(ServerPlayer p, Entity target) {
        Vec3 d = target.position().add(0, target.getBbHeight() / 2, 0).subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    static PuppetShellEntity shellOf(ServerLevel level, ServerPlayer p) {
        List<PuppetShellEntity> shells = level.getEntitiesOfClass(PuppetShellEntity.class, p.getBoundingBox().inflate(80),
                s -> p.getUUID().equals(s.ownerId()) && s.isAlive());
        assertEquals(1, shells.size(), "one shell");
        assertSame(Possession.data(p).shell(), shells.get(0).getUUID());
        return shells.get(0);
    }

    static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    static void cleanUp(MinecraftServer server, ServerLevel level, ServerPlayer p) {
        if (Possession.isPossessing(p)) Possession.release(p, Possession.Return.RETURNED);
        PuppetTest.cleanUp(server, level, p);
    }

    /** A test player that can be hurt and hurt others (NeoForge's fake players can't), with real advancements. */
    static ServerPlayer damageable(MinecraftServer server, String name) {
        return TestServer.damageable(server, name);
    }
}
