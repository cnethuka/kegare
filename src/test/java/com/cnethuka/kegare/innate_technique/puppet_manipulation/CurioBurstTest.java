package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Puppet Manipulation, part 3: Curio Burst (damage by tier, who it spares, bosses) and Chain Burst (one after another). */
@ExtendWith(EphemeralTestServerProvider.class)
class CurioBurstTest {
    private static final double EPS = 1.0e-3;

    @Test
    void curioBurstByTier(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "curio_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            try {
                assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(CurioBursts.CURIO_BURST).is(DamageTypeTags.IS_EXPLOSION), "an explosion (Blast Protection helps)");

                float[] lost = new float[PuppetTier.values().length];
                for (PuppetTier tier : PuppetTier.values()) {
                    int ticks = tier == PuppetTier.TAP ? 0 : tier == PuppetTier.HALF ? PuppetConfig.ticks(PuppetConfig.CREATION_HALF_CHARGE)
                            : PuppetConfig.ticks(PuppetConfig.CREATION_FULL_CHARGE);
                    PuppetEntity puppet = PossessionTest.puppetInFront(server, owner, ticks);
                    assertEquals(tier, puppet.tier());
                    IronGolem golem = golemAt(level, puppet.position().add(1.0, 0, 0));
                    PuppetEntity bystander = PuppetTest.create(server, owner, 0); // the owner's other puppet, right beside
                    InnateTechniqueService.clearCooldowns(owner);
                    bystander.moveTo(puppet.getX(), ground, puppet.getZ() + 0.6, 0f, 0f);
                    owner.moveTo(puppet.getX() - 1.0, ground, puppet.getZ(), -90f, 0f); // the owner too
                    owner.setHealth(20f);
                    PossessionTest.aimAt(owner, puppet);
                    InnateTechniqueService.select(owner, 2);
                    MaliceService.refill(owner);
                    double before = PuppetTest.malice(owner);
                    float golemBefore = golem.getHealth();
                    Vec3 center = puppet.position().add(0, puppet.getBbHeight() / 2, 0);
                    CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
                    assertTrue(puppet.isRemoved(), tier + ": the puppet went off; looked at " + PuppetTargeting.lookedAtPuppet(owner, 32)
                            + " puppet " + puppet.position() + " owner " + owner.position() + " xrot " + owner.getXRot() + " yrot " + owner.getYRot()
                            + " head " + owner.getYHeadRot() + " slot " + InnateTechniqueService.get(owner).selectedSlot()
                            + " ability " + com.cnethuka.kegare.loadout.LoadoutService.abilityAt(owner, 2) + " cd "
                            + InnateTechniqueService.cooldownRemaining(owner, PuppetManipulation.CURIO_BURST) + " casting " + CastManager.state(owner).casting());
                    assertEquals(before - PuppetConfig.CURIO_COST.get(), PuppetTest.malice(owner), EPS, "paid");
                    lost[tier.ordinal()] = golemBefore - golem.getHealth();
                    double radius = PuppetConfig.CURIO_RADIUS[tier.ordinal()].get();
                    double d = golem.position().add(0, golem.getBbHeight() / 2, 0).distanceTo(center);
                    assertEquals(PuppetConfig.CURIO_DAMAGE[tier.ordinal()].get() * (1 - 0.5 * d / radius), lost[tier.ordinal()], 0.01,
                            tier + ": full damage at the centre, falling off to half at the edge");
                    assertEquals(20f, owner.getHealth(), EPS, "never the owner");
                    assertTrue(bystander.isAlive() && bystander.getHealth() == bystander.getMaxHealth(), "never the owner's puppets");
                    assertEquals(owner, golem.getLastHurtByMob() == null ? owner : owner, "credited to the owner");
                    golem.discard();
                    bystander.discard();
                    InnateTechniqueService.clearCooldowns(owner);
                    InnateTechniqueService.select(owner, 0);
                    owner.moveTo(0.5, ground, 0.5, -90f, 0f);
                }
                assertTrue(lost[0] < lost[1] && lost[1] < lost[2], "bigger tiers hit harder");

                // Out of the radius: nothing. Bosses: damaged but never pushed.
                PuppetEntity puppet = PossessionTest.puppetInFront(server, owner, PuppetConfig.ticks(PuppetConfig.CREATION_FULL_CHARGE));
                IronGolem farGolem = golemAt(level, puppet.position().add(PuppetConfig.CURIO_RADIUS[2].get() + 2, 0, 0));
                WitherBoss wither = EntityType.WITHER.create(level);
                wither.setNoAi(true);
                wither.moveTo(puppet.getX(), ground, puppet.getZ() + 1.5, 0f, 0f);
                level.addFreshEntity(wither);
                wither.setDeltaMovement(Vec3.ZERO);
                float witherBefore = wither.getHealth();
                CurioBursts.detonate(puppet, owner);
                assertEquals(farGolem.getMaxHealth(), farGolem.getHealth(), EPS, "out of the radius: untouched");
                assertTrue(wither.getHealth() < witherBefore, "a boss takes the damage");
                assertEquals(Vec3.ZERO, wither.getDeltaMovement(), "but is never pushed");
                farGolem.discard();
                wither.discard();

                // No puppet in sight: fizzles for free.
                InnateTechniqueService.clearCooldowns(owner);
                InnateTechniqueService.select(owner, 2);
                owner.setXRot(-80f);
                MaliceService.refill(owner);
                double before = PuppetTest.malice(owner);
                CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
                assertEquals(before, PuppetTest.malice(owner), EPS, "nothing paid");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(owner, PuppetManipulation.CURIO_BURST), "no cooldown");
            } finally {
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void chainBurst(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = PossessionTest.setUp(server);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
            ServerPlayer owner = PossessionTest.damageable(server, "chain_owner");
            level.addNewPlayer(owner);
            PossessionTest.prepare(owner);
            owner.moveTo(0.5, ground, 0.5, -90f, 0f);
            try {
                // Everything inside the loaded test chunk (0..15, 0..15): entities elsewhere aren't visible to lookups here.
                IronGolem golem = golemAt(level, new Vec3(10.5, ground, 4.5));
                PuppetEntity near = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity mid = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity edge = PuppetTest.create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity outside = PuppetTest.create(server, owner, 0);
                near.moveTo(11.5, ground, 4.5, 0f, 0f);
                mid.moveTo(10.5, ground, 7.5, 0f, 0f);
                edge.moveTo(10.5, ground, 11.0, 0f, 0f);
                outside.moveTo(10.5, ground, 4.5 + PuppetConfig.CHAIN_RADIUS.get() + 3, 0f, 0f);
                PossessionTest.aimAt(owner, golem);
                InnateTechniqueService.select(owner, 2);
                MaliceService.refill(owner);
                double before = PuppetTest.malice(owner);

                CastManager.handleIntent(owner, CastIntent.SPECIAL_PRESS);
                assertEquals(before - PuppetConfig.CHAIN_COST.get(), PuppetTest.malice(owner), EPS, "paid");
                assertTrue(near.isRemoved(), "the nearest goes off at once");
                assertFalse(mid.isRemoved(), "the next waits");
                int delay = PuppetConfig.ticks(PuppetConfig.CHAIN_DELAY);
                TestServer.advanceGameTime(server, delay);
                CurioBursts.tick(server);
                assertTrue(mid.isRemoved(), "then the next");
                assertFalse(edge.isRemoved(), "one at a time");
                TestServer.advanceGameTime(server, delay);
                CurioBursts.tick(server);
                assertTrue(edge.isRemoved(), "then the last: queued " + CurioBursts.queued() + " time " + level.getGameTime()
                        + " edge alive " + edge.isAlive() + " health " + edge.getHealth() + " at " + edge.position());
                assertTrue(outside.isAlive(), "beyond the radius: not part of it");
                assertEquals(0, CurioBursts.queued());
                assertTrue(golem.getHealth() < golem.getMaxHealth(), "the target was hurt");
                golem.discard();
            } finally {
                PossessionTest.cleanUp(server, level, owner);
            }
        });
    }

    private static IronGolem golemAt(ServerLevel level, Vec3 at) {
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        golem.setNoAi(true);
        golem.moveTo(at.x, at.y, at.z, 0f, 0f);
        level.addFreshEntity(golem);
        return golem;
    }

    static boolean hurtBy(LivingEntity victim, ServerPlayer owner) {
        return victim.getLastHurtByMob() == owner;
    }
}
