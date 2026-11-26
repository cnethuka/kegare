package com.cnethuka.kegare.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.ability.AbilityCategory;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeDamage;
import com.cnethuka.kegare.innate_technique.swap.Swap;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CooldownRates;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.RefinementService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.sixeyes.Reads;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The Incomplete Domain (DESIGN §3.35): how it's learned, what opening it takes, its barrier, and its caster's boosts. */
@ExtendWith(EphemeralTestServerProvider.class)
class IncompleteDomainTest {
    private static final double EPS = 1.0e-6;

    private static boolean has(ServerPlayer p) {
        return SharedAbilityService.isUnlocked(p, DomainIds.INCOMPLETE);
    }

    /** It's theirs and on the bar: the number key it's on. */
    private static int slotted(ServerPlayer p) {
        int slot = LoadoutService.slots(p).indexOf(Optional.of(DomainIds.INCOMPLETE));
        assertTrue(slot >= 0, "setup: it's on the bar");
        return slot;
    }

    private static void press(ServerPlayer p, int slot) {
        InnateTechniqueService.select(p, slot);
        CastManager.handleIntent(p, CastIntent.CAST_PRESS);
        CastManager.handleIntent(p, CastIntent.CAST_RELEASE);
    }

    @Test
    void itsShared(MinecraftServer server) {
        assertTrue(SharedAbilities.isShared(DomainIds.INCOMPLETE));
        assertEquals(AbilityCategory.MALICE, IncompleteDomains.ABILITY.category(), "under the Malice tab");
        assertTrue(Unlocks.registered().contains(DomainIds.INCOMPLETE), "learned through the unlock framework");
    }

    @Test
    void itsLearnedFromAGradeAndAPeakRefinementAndKeptThroughARreroll(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "inc_learn", 8, 8);
                assertFalse(has(p), "nobody starts with it");
                GradeService.set(p, DomainConfig.INCOMPLETE_UNLOCK_GRADE.get());
                RefinementService.setLevel(p, DomainConfig.INCOMPLETE_UNLOCK_REFINEMENT.get() - 1);
                assertFalse(has(p), "a level short");
                RefinementService.setLevel(p, DomainConfig.INCOMPLETE_UNLOCK_REFINEMENT.get());
                assertTrue(has(p), "the Grade and the Peak Refinement: learned");
                assertTrue(Unlocks.has(p, DomainIds.INCOMPLETE));
                slotted(p);

                InnateTechniqueService.setInnateTechnique(p, Swap.SWAP.get());
                assertEquals(0, RefinementService.level(p), "setup: a reroll resets Refinement");
                assertTrue(has(p), "kept through a reroll: it's Peak Refinement it asks for, and it's shared");

                ServerPlayer low = DomainTest.at(server, "inc_low_grade", 12, 8);
                GradeService.set(low, Grade.GRADE_3);
                RefinementService.setLevel(low, 10);
                assertFalse(has(low), "the top Refinement doesn't make up for the Grade");
                GradeService.set(low, Grade.SPECIAL_GRADE);
                assertTrue(has(low), "a Grade above it is fine");
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void domainInsightLowersThePeakRefinementButNeverRemovesIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                int needed = DomainConfig.INCOMPLETE_UNLOCK_REFINEMENT.get(); // 6 by default
                ServerPlayer p = DomainTest.at(server, "inc_insight", 8, 8);
                GradeService.set(p, DomainConfig.INCOMPLETE_UNLOCK_GRADE.get());
                RefinementService.setLevel(p, needed / 2);
                Insight.add(p, InsightCategory.WARD, 1000);
                assertFalse(has(p), "only Domain Insight lowers it");
                Insight.add(p, InsightCategory.DOMAIN, 10 * (needed - needed / 2) - 1);
                assertFalse(has(p), "it rounds down");
                assertEquals(needed / 2 + 1, IncompleteDomains.refinementNeeded(p));
                Insight.add(p, InsightCategory.DOMAIN, 1);
                assertEquals(needed / 2, IncompleteDomains.refinementNeeded(p));
                assertTrue(has(p), "lowered to half: learned");

                ServerPlayer q = DomainTest.at(server, "inc_insight_cap", 12, 8);
                GradeService.set(q, DomainConfig.INCOMPLETE_UNLOCK_GRADE.get());
                RefinementService.setLevel(q, needed / 2 - 1);
                Insight.add(q, InsightCategory.DOMAIN, 100_000);
                assertEquals(needed - needed / 2, IncompleteDomains.refinementNeeded(q), "half at most, whatever the Insight");
                assertFalse(has(q));
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void aSeveredPlayerNeverHasIt(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "inc_severed", 8, 8);
                BirthService.rebirth(p, OptionalInt.of(0));
                assertTrue(MaliceService.isSevered(p), "setup");
                GradeService.set(p, Grade.SPECIAL_GRADE);
                Unlocks.evaluate(p);
                assertFalse(has(p), "not by Grade");
                assertFalse(IncompleteDomains.teach(p), "nor by an admin");
                assertFalse(has(p));
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void openingItTakesHalfTheMeterAndItsMaliceAndARefusalTakesNothing(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "inc_open", 8, 8);
                ServerPlayer other = DomainTest.at(server, "inc_open_other", 12, 8);
                assertTrue(IncompleteDomains.teach(p), "setup");
                int slot = slotted(p);

                DomainMeter.set(p, 49);
                double malice = MaliceService.get(p).current();
                press(p, slot);
                assertTrue(Domains.ownedBy(p).isEmpty(), "49 isn't half");
                assertEquals(malice, MaliceService.get(p).current(), EPS, "a refusal costs no Malice");
                assertEquals(49, DomainMeter.get(p), EPS, "and no meter");
                assertEquals(0, InnateTechniqueService.cooldownRemaining(p, IncompleteDomains.ABILITY), "and starts no cooldown");

                DomainMeter.set(p, 80);
                press(p, slot);
                DomainEntity d = Domains.ownedBy(p).orElse(null);
                assertNotNull(d, "it opens");
                assertEquals(80 - DomainData.MAX * DomainConfig.INCOMPLETE_METER_SHARE.get(), DomainMeter.get(p), EPS, "half the meter");
                assertEquals(malice - DomainConfig.INCOMPLETE_COST.get(), MaliceService.get(p).current(), 1.0, "and its Malice");
                assertFalse(d.complete(), "incomplete");
                assertEquals(DomainConfig.INCOMPLETE_RADIUS.get(), d.radius(), EPS, "smaller than a full one");
                assertTrue(d.radius() < DomainConfig.FULL_RADIUS.get());
                assertEquals(0x000000, d.look().shellColor(), "its shell pitch black");
                assertFalse(d.look().cracked(), "and smooth: it cracks only where it's hit");
                assertEquals(IncompleteDomains.WASTELAND, d.look(), "the one wasteland, whoever opens it");
                assertEquals(Optional.of(DomainIds.INCOMPLETE), Reads.detail(p).body().orElseThrow().domain(), "the Six Eyes read it by name");

                DomainTest.closed(d);
                assertTrue(Domains.trapping(other).isPresent(), "setup: the other is inside");
                assertTrue(Domains.sureHitTargets(d).isEmpty(), "it finds nobody: no sure-hit");
                assertEquals(DomainMastery.durationTicks(p, false, DomainConfig.ticks(DomainConfig.INCOMPLETE_DURATION)), d.duration(),
                        "its own duration, scaled by its caster's mastery");

                DomainTest.step(d, d.duration());
                assertTrue(d.isRemoved(), "its time runs out");
                assertEquals(DomainConfig.ticks(DomainConfig.INCOMPLETE_BURNOUT), Burnout.remaining(p), "a short Burnout");
                InnateTechniqueService.clearCooldowns(p);
                DomainMeter.set(p, 100);
                press(p, slot);
                assertTrue(Domains.ownedBy(p).isEmpty(), "never another while burnt out");
                assertEquals(100, DomainMeter.get(p), EPS);
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void itsBarrierIsWeakerThanAFullOne(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "inc_weak", 8, 8);
                DomainMeter.set(p, 100);
                DomainEntity d = Domains.open(p, IncompleteDomains.spec(p), CastIntent.CAST_RELEASE);
                assertNotNull(d);
                DomainTest.closed(d);
                assertTrue(DomainConfig.INCOMPLETE_BARRIER.get() < DomainConfig.FULL_BARRIER.get(), "the defaults");
                Domains.hit(d, DomainConfig.INCOMPLETE_BARRIER.get() * 0.6, d.centre().add(d.radius(), 0, 0), null);
                assertFalse(d.isRemoved(), "it holds a while");
                Domains.hit(d, DomainConfig.INCOMPLETE_BARRIER.get() * 0.6, d.centre().add(d.radius(), 0, 0), null);
                assertTrue(d.isRemoved(), "and breaks at its own strength, a full one's quarter");
                assertEquals(DomainConfig.ticks(DomainConfig.INCOMPLETE_BURNOUT), Burnout.remaining(p), "broken, its caster burns out all the same");
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void itsCasterHasStrengthResistanceAndSpeedWhileItStands(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = DomainTest.at(server, "inc_effects", 8, 8);
                ServerPlayer other = DomainTest.at(server, "inc_effects_other", 11, 8);
                var STRENGTH = net.minecraft.world.effect.MobEffects.DAMAGE_BOOST;
                var RESISTANCE = net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE;
                var SPEED = net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED;
                p.removeAllEffects();
                // A Speed II potion of their own, before it opens.
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(SPEED, 20 * 600, 1));
                DomainMeter.set(p, 100);
                DomainEntity d = Domains.open(p, IncompleteDomains.spec(p), CastIntent.CAST_RELEASE);
                assertNotNull(d);
                DomainTest.step(d, 3);
                assertFalse(p.hasEffect(STRENGTH), "nothing while it rises");
                DomainTest.closed(d);
                DomainTest.step(d, 1);
                for (var e : java.util.List.of(STRENGTH, RESISTANCE)) {
                    var inst = p.getEffect(e);
                    assertNotNull(inst, e.getRegisteredName() + " while it stands");
                    assertEquals(0, inst.getAmplifier(), "level I");
                    int left = d.duration() - (d.tickCount - d.closedAt());
                    assertTrue(inst.getDuration() >= left && inst.getDuration() <= left + 5, "as long as it has left (test players never count it down): " + inst.getDuration() + " vs " + left);
                }
                assertEquals(1, p.getEffect(SPEED).getAmplifier(), "their own stronger potion still shows");
                assertFalse(other.hasEffect(STRENGTH) || other.hasEffect(RESISTANCE), "its caster's alone");

                Domains.end(d, Domains.EndReason.COMMAND);
                assertFalse(p.hasEffect(STRENGTH) || p.hasEffect(RESISTANCE), "gone as it ends");
                assertTrue(p.hasEffect(SPEED) && p.getEffect(SPEED).getAmplifier() == 1, "a potion of their own stays");
                p.removeAllEffects();
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void itsBoostsAreItsCastersAloneAndOnlyWhileItStands(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerLevel level = DomainTest.level(server);
                ServerPlayer p = DomainTest.at(server, "inc_boost", 8, 8);
                ServerPlayer other = DomainTest.at(server, "inc_boost_other", 11, 8);
                var shard = Rime.RIME.get().abilities().get(0);
                assertEquals(100, CastManager.abilityCost(p, shard, 100), EPS, "setup: full price");

                DomainMeter.set(p, 100);
                DomainEntity d = Domains.open(p, IncompleteDomains.spec(p), CastIntent.CAST_RELEASE);
                assertNotNull(d);
                assertFalse(IncompleteDomains.boosted(p), "nothing while it rises");
                assertEquals(100, CastManager.abilityCost(p, shard, 100), EPS);
                DomainTest.closed(d);
                assertTrue(IncompleteDomains.boosted(p));

                assertEquals(100 * (1 - DomainConfig.INCOMPLETE_TECHNIQUE_COST.get()), CastManager.abilityCost(p, shard, 100), EPS, "the technique cheaper");
                assertEquals(100, CastManager.abilityCost(p, IncompleteDomains.ABILITY, 100), EPS, "a shared ability isn't");
                assertEquals(100, CastManager.abilityCost(other, shard, 100), EPS, "and nobody else's");
                assertEquals(DomainConfig.INCOMPLETE_COOLDOWN_RATE.get(), CooldownRates.multiplier(p, shard.cooldownKey()), EPS, "its cooldowns faster");
                assertEquals(1.0, CooldownRates.multiplier(p, IncompleteDomains.ABILITY.cooldownKey()), EPS, "not its own");
                assertEquals(1.0, CooldownRates.multiplier(other, shard.cooldownKey()), EPS, "nor anyone else's");

                other.invulnerableTime = 0;
                float hp = other.getHealth();
                other.hurt(RimeDamage.source(level, p, p), 4f);
                assertEquals(4f * (1 + DomainConfig.INCOMPLETE_OUTPUT.get()), hp - other.getHealth(), 1.0e-4, "its hits harder");
                p.invulnerableTime = 0;
                p.setHealth(p.getMaxHealth());
                hp = p.getHealth();
                p.hurt(RimeDamage.source(level, other, other), 4f);
                assertEquals(4f, hp - p.getHealth(), 1.0e-4, "not the other's");

                Domains.end(d, Domains.EndReason.COMMAND);
                assertFalse(IncompleteDomains.boosted(p));
                assertEquals(100, CastManager.abilityCost(p, shard, 100), EPS, "gone when it ends");
                assertEquals(1.0, CooldownRates.multiplier(p, shard.cooldownKey()), EPS);
            } finally {
                DomainTest.cleanUp(server);
            }
        });
    }
}
