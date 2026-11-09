package com.cnethuka.kegare.innate_technique.time_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Standstill and real sword swings (DESIGN §3.26), through vanilla's own {@code Player.attack}: every hit on something frozen counts
 * toward its held hit (full charge, partial, a crit, and a sweep's on the ones beside it), on every kind of mob; a mob hit just before
 * the cast takes the first swing after the resume in full; and nothing frozen hurts anyone (a blow, thorns, a magma cube's touch), is
 * pushed, or teleports.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class StandstillHitsTest {
    private static final double Y = 120;

    /** A full swing's charge (a test player never ticks, so its attack recharge is set by hand). */
    private static void charged(ServerPlayer p) {
        try {
            var f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 1000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServerPlayer caster(MinecraftServer server, ServerLevel level) {
        ServerPlayer p = TestServer.damageable(server, "ss_hits_caster");
        level.addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(3000));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, TimeManipulation.TIME_MANIPULATION.get());
        MaliceService.refill(p);
        p.setHealth(p.getMaxHealth());
        p.moveTo(0.5, Y, 0.5, -90f, 0f); // facing +X
        p.setOnGround(true);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        p.setItemInHand(InteractionHand.MAIN_HAND, sword);
        sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> p.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
        return p;
    }

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, double x, double z, List<Mob> all) {
        Mob m = type.create(level);
        m.moveTo(x, Y, z, 90f, 0f);
        m.setNoGravity(true);
        m.setNoAi(true);
        level.addFreshEntity(m);
        all.add(m);
        return m;
    }

    private static void resume(MinecraftServer server, ServerLevel level) {
        TestServer.advanceGameTime(server, TimeConfig.ticks(TimeConfig.STANDSTILL_SECONDS));
        TimeStops.tick(level);
        assertFalse(TimeStops.active(level), "setup: time runs again");
    }

    @Test
    void everySwingOnTheFrozenCountsOnEveryMob(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            List<Mob> all = new ArrayList<>();
            try {
                ServerPlayer caster = caster(server, level);
                List<EntityType<? extends Mob>> types = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.IRON_GOLEM,
                        EntityType.WOLF, EntityType.ENDERMAN, EntityType.CREEPER);
                List<Mob> mobs = new ArrayList<>();
                for (int i = 0; i < types.size(); i++) mobs.add(mob(level, types.get(i), 2.0, 0.5 + i * 4, all));
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                assertTrue(TimeStops.active(level), "setup: time stands still");
                float[] before = new float[mobs.size()];
                for (int i = 0; i < mobs.size(); i++) {
                    Mob m = mobs.get(i);
                    before[i] = m.getHealth();
                    caster.moveTo(0.5, Y, m.getZ(), -90f, 0f);
                    charged(caster);
                    caster.attack(m); // full charge
                    caster.attack(m); // straight after: partial
                    charged(caster);
                    caster.fallDistance = 1f;
                    caster.setOnGround(false);
                    caster.attack(m); // a crit
                    caster.fallDistance = 0f;
                    caster.setOnGround(true);
                    assertEquals(before[i], m.getHealth(), 1.0e-4, m.getType().toShortString() + ": nothing yet");
                    assertEquals(Vec3.ZERO, m.getDeltaMovement(), m.getType().toShortString() + ": and not pushed");
                }
                resume(server, level);
                for (int i = 0; i < mobs.size(); i++) {
                    Mob m = mobs.get(i);
                    float landed = before[i] - (m.isAlive() ? m.getHealth() : 0f);
                    // 6 (full) + 1.2 (a fifth, uncharged) + 9 (a crit), less what its armour takes; or all it had.
                    assertTrue(!m.isAlive() || landed > 14f, m.getType().toShortString() + ": all three landed as one, " + landed);
                }
                Mob golem = mobs.get(types.indexOf(EntityType.IRON_GOLEM));
                assertEquals(6f + 1.2f + 9f, before[types.indexOf(EntityType.IRON_GOLEM)] - golem.getHealth(), 0.05f, "the sum, exactly, on the golem");
            } finally {
                all.forEach(Mob::discard);
                TimeStops.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aSweepsHitsOnTheFrozenBesideItAreHeldToo(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            List<Mob> all = new ArrayList<>();
            try {
                ServerPlayer caster = caster(server, level);
                Mob main = mob(level, EntityType.IRON_GOLEM, 2.4, 0.5, all);
                Mob beside = mob(level, EntityType.IRON_GOLEM, 2.4, 2.0, all);
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                float mainBefore = main.getHealth(), besideBefore = beside.getHealth();
                charged(caster);
                String why = "ground " + caster.onGround() + " sprint " + caster.isSprinting() + " charge " + caster.getAttackStrengthScale(0.5f)
                        + " walk " + (caster.walkDist - caster.walkDistO) + " fall " + caster.fallDistance + " stop " + TimeStops.active(level)
                        + " frozen " + TimeStops.frozen(main) + "/" + TimeStops.frozen(beside);
                caster.attack(main); // on the ground, fully charged, a sword: a sweep
                assertEquals(besideBefore, beside.getHealth(), 1.0e-4, "held, not dealt");
                assertTrue(TimeStops.holding(main), "main held: " + why);
                assertTrue(caster.getMainHandItem().getDamageValue() > 0, "the swing counted as landed (the sword wore): " + why);
                assertTrue(TimeStops.holding(beside), "the sweep reached it: " + why);
                resume(server, level);
                assertEquals(6f, mainBefore - main.getHealth(), 0.05f, "the swing");
                // A sweep deals 1 (no Sweeping Edge) to what's beside it.
                assertEquals(1f, besideBefore - beside.getHealth(), 0.05f, "and the sweep's hit on the one beside it, held and landed");
            } finally {
                all.forEach(Mob::discard);
                TimeStops.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void aMobHitJustBeforeTheCastTakesTheFirstSwingAfterInFull(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            List<Mob> all = new ArrayList<>();
            try {
                ServerPlayer caster = caster(server, level);
                Mob golem = mob(level, EntityType.IRON_GOLEM, 2.4, 0.5, all);
                charged(caster);
                caster.attack(golem);
                assertEquals(20, golem.invulnerableTime, "setup: its hurt cooldown running");
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                resume(server, level); // its cooldown stood still with it
                float before = golem.getHealth();
                charged(caster);
                caster.attack(golem);
                assertEquals(6f, before - golem.getHealth(), 0.05f, "the first swing after the resume counts in full");
            } finally {
                all.forEach(Mob::discard);
                TimeStops.clear();
                TestServer.removePlayers(server);
            }
        });
    }

    @Test
    void nothingFrozenHurtsAnyone(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            List<Mob> all = new ArrayList<>();
            try {
                ServerPlayer caster = caster(server, level);
                Mob zombie = mob(level, EntityType.ZOMBIE, 1.4, 0.5, all);
                net.minecraft.world.entity.monster.MagmaCube cube = (net.minecraft.world.entity.monster.MagmaCube) mob(level, EntityType.MAGMA_CUBE, 0.5, 1.6, all);
                cube.setSize(2, true);
                cube.setNoAi(false); // a slime only hurts what it touches with its AI on
                cube.moveTo(0.5, Y, 1.6, 0f, 0f);
                net.minecraft.world.entity.monster.EnderMan enderman = (net.minecraft.world.entity.monster.EnderMan) mob(level, EntityType.ENDERMAN, 4.5, 4.5, all);
                CastManager.handleIntent(caster, CastIntent.ULTIMATE_PRESS);
                assertTrue(TimeStops.frozen(zombie), "setup: frozen");
                float health = caster.getHealth();
                assertFalse(caster.hurt(caster.damageSources().mobAttack(zombie), 3f), "its blow, mid-swing as time stopped: nothing");
                assertFalse(caster.hurt(caster.damageSources().thorns(zombie), 2f), "the thorns on its armour: nothing");
                cube.playerTouch(caster);
                assertEquals(health, caster.getHealth(), 1.0e-4, "the caster untouched, even walking into a frozen magma cube");
                assertTrue(net.neoforged.neoforge.event.EventHooks.onEnderTeleport(enderman, 20.5, Y, 20.5).isCanceled(), "a frozen enderman never teleports");
                zombie.knockback(1.0, -1.0, 0.0);
                assertEquals(Vec3.ZERO, zombie.getDeltaMovement(), "and nothing frozen is pushed");
                resume(server, level);
                caster.invulnerableTime = 0;
                float now = caster.getHealth();
                cube.playerTouch(caster);
                assertTrue(caster.getHealth() < now, "the same touch hurts once time runs again (so the check above meant something)");
                caster.invulnerableTime = 0;
                assertTrue(caster.hurt(caster.damageSources().mobAttack(zombie), 3f), "and so does its blow");
            } finally {
                all.forEach(Mob::discard);
                TimeStops.clear();
                TestServer.removePlayers(server);
            }
        });
    }
}
