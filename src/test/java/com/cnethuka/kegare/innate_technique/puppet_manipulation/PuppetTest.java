package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Puppet Manipulation, part 1: the puppet entity and Puppet Creation (tiers, the cap, spinning, idling, dissolving). */
@ExtendWith(EphemeralTestServerProvider.class)
class PuppetTest {
    static final double Y = 120;

    @Test
    void creationTiersAndCap(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer owner = owner(server, level, "puppet_tiers");
            try {
                // A tap: released at once.
                double before = malice(owner);
                PuppetEntity tap = create(server, owner, 0);
                assertEquals(PuppetTier.TAP, tap.tier());
                assertEquals(before - PuppetTier.TAP.cost(), malice(owner), 1e-3, "a tap costs the tap cost");
                assertEquals(PuppetTier.TAP.health(), tap.getMaxHealth(), 1e-6);
                assertEquals(PuppetTier.TAP.health(), tap.getHealth(), 1e-6);
                assertFalse(tap.hasSoul(), "no soul");
                assertEquals(owner.getUUID(), tap.ownerId());
                assertEquals(PuppetManipulation.MALICE_COLOR, tap.maliceColor());
                assertEquals(PuppetConfig.SCALE.get(), tap.getAttributeValue(Attributes.SCALE), 1e-6, "half scale");
                assertEquals(0.6 * PuppetConfig.SCALE.get(), tap.getBbWidth(), 1e-3, "and half the player's width");
                assertTrue(tap.distanceTo(owner) < 2.0, "made next to the caster");
                assertEquals(owner.getName().getString(), tap.ownerName(), "carries the owner's look");
                assertEquals(PuppetConfig.ticks(PuppetConfig.CREATION_COOLDOWN), InnateTechniqueService.cooldownRemaining(owner, PuppetManipulation.PUPPET_CREATION));

                // Half and full charges.
                InnateTechniqueService.clearCooldowns(owner);
                before = malice(owner);
                PuppetEntity half = create(server, owner, PuppetConfig.ticks(PuppetConfig.CREATION_HALF_CHARGE));
                assertEquals(PuppetTier.HALF, half.tier());
                assertEquals(before - PuppetTier.HALF.cost(), malice(owner), 1e-3);
                assertEquals(PuppetTier.HALF.health(), half.getMaxHealth(), 1e-6);
                InnateTechniqueService.clearCooldowns(owner);
                before = malice(owner);
                PuppetEntity full = create(server, owner, PuppetConfig.ticks(PuppetConfig.CREATION_FULL_CHARGE));
                assertEquals(PuppetTier.FULL, full.tier());
                assertEquals(before - PuppetTier.FULL.cost(), malice(owner), 1e-3);
                assertEquals(PuppetTier.FULL.health(), full.getMaxHealth(), 1e-6);
                assertEquals(3, Puppets.count(owner), "all three counted");

                // Lowering the cap below what they hold: nobody loses a puppet, but no new ones until below it.
                int cap = PuppetConfig.MAX_PUPPETS.get();
                PuppetConfig.MAX_PUPPETS.set(2);
                try {
                    assertEquals(3, puppetsOf(level, owner).size(), "over the lowered cap: all three stay");
                    assertCapFizzles(owner, 3);
                    // One dies: still at the cap of 2, so still no.
                    tap.hurt(level.damageSources().genericKill(), 1000f);
                    tap.tick();
                    assertEquals(2, Puppets.count(owner), "its slot is freed");
                    assertCapFizzles(owner, 2);
                    // One detonates: below the cap, so a new one can be made.
                    CurioBursts.detonate(half, owner);
                    assertEquals(1, Puppets.count(owner), "a detonated puppet frees its slot");
                    InnateTechniqueService.clearCooldowns(owner);
                    MaliceService.refill(owner);
                    create(server, owner, 0);
                    assertEquals(2, Puppets.count(owner), "below the cap again: a new one");
                    assertCapFizzles(owner, 2);
                } finally {
                    PuppetConfig.MAX_PUPPETS.set(cap);
                }
            } finally {
                cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void theCapCountsUnloadedPuppetsAndFreesSlots(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer owner = owner(server, level, "puppet_cap");
            try {
                assertEquals(15, PuppetConfig.MAX_PUPPETS.getDefault(), "the default cap");
                int cap = PuppetConfig.MAX_PUPPETS.get();
                PuppetConfig.MAX_PUPPETS.set(3);
                try {
                    PuppetEntity first = create(server, owner, 0);
                    InnateTechniqueService.clearCooldowns(owner);
                    create(server, owner, 0);
                    InnateTechniqueService.clearCooldowns(owner);
                    PuppetEntity far = create(server, owner, 0);
                    InnateTechniqueService.clearCooldowns(owner);
                    assertCapFizzles(owner, 3);

                    // Its chunk unloads: it's still theirs, so it still counts.
                    net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
                    far.save(saved);
                    far.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    assertEquals(2, puppetsOf(level, owner).size(), "setup: only two are loaded");
                    assertEquals(3, Puppets.count(owner), "the unloaded one still counts");
                    assertCapFizzles(owner, 3);

                    // One of the loaded ones dissolves: a slot is free.
                    Puppets.dissolve(first);
                    assertEquals(2, Puppets.count(owner), "dissolving frees a slot");
                    MaliceService.refill(owner);
                    create(server, owner, 0);
                    assertEquals(3, Puppets.count(owner));
                    assertCapFizzles(owner, 3);

                    // The unloaded one loads again: still current, still theirs, not counted twice.
                    PuppetEntity back = (PuppetEntity) EntityType.create(saved, level).orElseThrow();
                    level.addFreshEntity(back);
                    back.tick();
                    assertTrue(back.isAlive(), "a current puppet survives loading");
                    assertEquals(3, Puppets.count(owner), "and isn't counted twice");
                } finally {
                    PuppetConfig.MAX_PUPPETS.set(cap);
                }
            } finally {
                cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void spinsWhenStillAndIdlesWithoutItsOwner(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer owner = owner(server, level, "puppet_spin");
            try {
                PuppetEntity puppet = create(server, owner, 0);
                assertFalse(puppet.isSpinning(), "not at first");
                for (int i = 0; i <= PuppetConfig.ticks(PuppetConfig.SPIN_DELAY) + 1; i++) puppet.tick();
                assertTrue(puppet.isSpinning(), "standing still next to its owner: kuru kuru");
                puppet.setDeltaMovement(0.3, 0, 0);
                puppet.tick();
                assertFalse(puppet.isSpinning(), "moving: no spin");

                // Owner gone (offline): the puppet stands idle and never attacks.
                Cow cow = new Cow(EntityType.COW, level);
                cow.moveTo(puppet.getX() + 2, Y, puppet.getZ(), 0f, 0f);
                level.addFreshEntity(cow);
                level.removePlayerImmediately(owner, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                assertNull(puppet.owner(), "setup: the owner is gone");
                puppet.setTarget(cow);
                puppet.getNavigation().moveTo(cow, 1.0);
                puppet.tick();
                assertNull(puppet.getTarget(), "no target while the owner is offline");
                assertTrue(puppet.getNavigation().isDone(), "and it doesn't move");
                assertTrue(puppet.isAlive(), "it survives its owner leaving");
                cow.discard();
                level.addNewPlayer(owner);
            } finally {
                cleanUp(server, level, owner);
            }
        });
    }

    @Test
    void rerollingAwayDissolvesEveryPuppet(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerLevel level = server.overworld();
            loadArena(level);
            ServerPlayer owner = owner(server, level, "puppet_reroll");
            try {
                PuppetEntity loaded = create(server, owner, 0);
                InnateTechniqueService.clearCooldowns(owner);
                PuppetEntity unloaded = create(server, owner, 0);
                // Its chunk unloads: saved to disk, still counted.
                CompoundTag saved = new CompoundTag();
                unloaded.save(saved);
                unloaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                assertEquals(2, Puppets.count(owner), "an unloaded puppet still counts toward the cap");

                // Setting the same technique again changes nothing.
                InnateTechniqueService.setInnateTechnique(owner, PuppetManipulation.PUPPET_MANIPULATION.get());
                assertTrue(loaded.isAlive(), "same technique: the puppets stay");
                int generation = PuppetSavedData.get(server).generation(owner.getUUID());

                // Rerolled away: the loaded one dissolves now...
                InnateTechniqueService.setInnateTechnique(owner, Swap.SWAP.get());
                assertTrue(loaded.isRemoved(), "the loaded puppet dissolved");
                assertEquals(0, Puppets.count(owner), "nothing counted any more");
                assertNotEquals(generation, PuppetSavedData.get(server).generation(owner.getUUID()), "the generation moved on");

                // ...and the unloaded one the moment its chunk loads again.
                PuppetEntity reloaded = (PuppetEntity) EntityType.create(saved, level).orElseThrow();
                level.addFreshEntity(reloaded);
                reloaded.tick();
                assertTrue(reloaded.isRemoved(), "a stale puppet dissolves on its first tick");

                // Even if they roll Puppet Manipulation again later, old puppets never come back.
                InnateTechniqueService.setInnateTechnique(owner, PuppetManipulation.PUPPET_MANIPULATION.get());
                PuppetEntity again = (PuppetEntity) EntityType.create(saved, level).orElseThrow();
                level.addFreshEntity(again);
                again.tick();
                assertTrue(again.isRemoved(), "stale for good");
            } finally {
                cleanUp(server, level, owner);
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    /** At the cap the press fizzles for free: nothing charging, nothing paid, no cooldown, no new puppet. */
    static void assertCapFizzles(ServerPlayer owner, int expected) {
        InnateTechniqueService.clearCooldowns(owner);
        MaliceService.refill(owner);
        double before = malice(owner);
        CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
        assertNull(CastManager.state(owner).casting(), "can't even start charging at the cap");
        assertEquals(before, malice(owner), 1e-6, "nothing paid");
        assertEquals(0, InnateTechniqueService.cooldownRemaining(owner, PuppetManipulation.PUPPET_CREATION), "no cooldown");
        assertEquals(expected, Puppets.count(owner), "no new puppet");
    }

    static ServerPlayer owner(MinecraftServer server, ServerLevel level, String name) {
        ServerPlayer owner = TestServer.player(server, name);
        level.addNewPlayer(owner);
        BirthService.rebirth(owner, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(owner, PuppetManipulation.PUPPET_MANIPULATION.get());
        InnateTechniqueService.select(owner, 0);
        owner.moveTo(0.5, Y, 0.5, -90f, 0f);
        owner.setYHeadRot(-90f);
        MaliceService.refill(owner);
        return owner;
    }

    /** Charges Puppet Creation for {@code ticks} and releases it; returns the new puppet. */
    static PuppetEntity create(MinecraftServer server, ServerPlayer owner, int ticks) {
        List<PuppetEntity> before = puppetsOf(owner.serverLevel(), owner);
        CastManager.handleIntent(owner, CastIntent.CAST_PRESS);
        TestServer.advanceGameTime(server, ticks);
        CastManager.handleIntent(owner, CastIntent.CAST_RELEASE);
        List<PuppetEntity> after = puppetsOf(owner.serverLevel(), owner);
        after.removeAll(before);
        assertEquals(1, after.size(), "one new puppet");
        return after.get(0);
    }

    static List<PuppetEntity> puppetsOf(ServerLevel level, ServerPlayer owner) {
        return new java.util.ArrayList<>(Puppets.loaded(level.getServer(), owner.getUUID()));
    }

    static void cleanUp(MinecraftServer server, ServerLevel level, ServerPlayer owner) {
        Puppets.dissolveAll(server, owner.getUUID());
        CastManager.stopAll(owner, com.cnethuka.kegare.innatetechnique.EndReason.CANCELLED);
        level.removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
    }

    /**
     * Loads the test chunk and runs the chunk system's queued work, so entities added there are visible to lookups at
     * once (between test methods the chunk unloads, and its entities only become visible after a queued status change).
     */
    static void loadArena(ServerLevel level) {
        level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        level.getHeight(Heightmap.Types.MOTION_BLOCKING, 16, 0);
        for (int i = 0; i < 1000 && level.getChunkSource().pollTask(); i++) { }
    }

    static double malice(ServerPlayer p) {
        return MaliceService.get(p).current();
    }
}
