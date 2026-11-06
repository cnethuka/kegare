package com.cnethuka.kegare.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Assessment on real fights: what it reads, what it can never read, and promotion with zero wins. */
@ExtendWith(EphemeralTestServerProvider.class)
class AssessmentTest {
    private static final double EPS = 1.0e-6;

    @AfterEach
    void cleanUp(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            FightTracker.clear();
            TestServer.removePlayers(server);
        });
    }

    private static ServerPlayer sorcerer(MinecraftServer server, String name, int capacity) {
        ServerPlayer p = TestServer.damageable(server, name);
        server.overworld().addNewPlayer(p);
        BirthService.rebirth(p, OptionalInt.of(capacity));
        TraitService.removeAll(p, Trait.RemoveReason.ADMIN);
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static UUID opponent(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes());
    }

    /** A varied fight built directly: the same inputs every time. */
    private static Fight varied(ServerPlayer a, ServerPlayer b) {
        Fight fight = new Fight(a.getUUID(), b.getUUID(), 0);
        fight.landed(a.getUUID(), Rime.FROST_SHARD.id().toString(), false, 20);
        fight.landed(a.getUUID(), Rime.GLACIAL_LANCE.id().toString(), false, 40);
        fight.landed(a.getUUID(), Rime.SPLINTER.id().toString(), true, 60);
        fight.spend(a.getUUID(), 200);
        fight.damage(a.getUUID(), 16, 300);
        fight.damage(b.getUUID(), 10, 320);
        return fight;
    }

    @Test
    void killsAndWinsDontChangeTheScore(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer a = sorcerer(server, "wins_a", 1000);
            ServerPlayer b = sorcerer(server, "wins_b", 1000);
            Fight fight = varied(a, b);
            double base = AssessmentService.score(new FightReport(a, b, fight, FightOutcome.SURVIVED));
            for (FightOutcome outcome : FightOutcome.values()) {
                assertEquals(base, AssessmentService.score(new FightReport(a, b, fight, outcome)), EPS, outcome + " can't move the score");
            }
        });
    }

    @Test
    void capacityAndSixEyesDontChangeTheScore(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer a = sorcerer(server, "capacity_a", 100);
            ServerPlayer b = sorcerer(server, "capacity_b", 1000);
            Fight fight = varied(a, b);
            double low = AssessmentService.score(new FightReport(a, b, fight, FightOutcome.SURVIVED));
            BirthService.rebirth(a, OptionalInt.of(5000));
            InnateTechniqueService.setInnateTechnique(a, Rime.RIME.get());
            assertEquals(low, AssessmentService.score(new FightReport(a, b, fight, FightOutcome.SURVIVED)), EPS, "capacity isn't an input");
            com.cnethuka.kegare.trait.TraitSuccession.release(server, KegareTraits.SIX_EYES.get());
            assertEquals(TraitService.AddResult.ADDED, TraitService.add(a, KegareTraits.SIX_EYES.get(), Trait.AddReason.ADMIN), "setup");
            assertEquals(low, AssessmentService.score(new FightReport(a, b, fight, FightOutcome.SURVIVED)), EPS, "and neither are the Six Eyes");
            TraitService.removeAll(a, Trait.RemoveReason.ADMIN);
        });
    }

    @Test
    void aSeveredPlayerIsScoredOnMelee(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer severed = TestServer.damageable(server, "melee_a");
            server.overworld().addNewPlayer(severed);
            BirthService.rebirth(severed, OptionalInt.of(0));
            ServerPlayer b = sorcerer(server, "melee_b", 1000);
            Fight plain = new Fight(severed.getUUID(), b.getUUID(), 0);
            Fight mixed = new Fight(severed.getUUID(), b.getUUID(), 0);
            String[] moves = {"sword", "sword_critical", "axe", "axe_sprint", "sword", "fist"};
            for (int i = 0; i < moves.length; i++) {
                plain.melee(severed.getUUID(), "fist", i * 20L);
                mixed.melee(severed.getUUID(), moves[i], i * 20L);
                plain.damage(severed.getUUID(), 2, i * 20L);
                mixed.damage(severed.getUUID(), 2, i * 20L);
            }
            double spammed = AssessmentService.score(new FightReport(severed, b, plain, FightOutcome.SURVIVED));
            double varied = AssessmentService.score(new FightReport(severed, b, mixed, FightOutcome.SURVIVED));
            assertTrue(varied > spammed + 30, "variety is most of it: " + varied + " vs " + spammed);
        });
    }

    @Test
    void onlyQualifyingFightsAreScored(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer a = sorcerer(server, "qualify_a", 1000);
            ServerPlayer b = sorcerer(server, "qualify_b", 1000);
            Fight tooShort = new Fight(a.getUUID(), b.getUUID(), 0);
            tooShort.damage(a.getUUID(), 10, 100);
            tooShort.damage(b.getUUID(), 10, 100);
            assertFalse(AssessmentService.qualifies(tooShort, a.getUUID(), b.getUUID()), "five seconds");
            Fight oneSided = new Fight(a.getUUID(), b.getUUID(), 0);
            oneSided.damage(a.getUUID(), 20, 400);
            oneSided.damage(b.getUUID(), 1, 400);
            assertFalse(AssessmentService.qualifies(oneSided, a.getUUID(), b.getUUID()), "only one of them really fought");
            Fight real = new Fight(a.getUUID(), b.getUUID(), 0);
            real.damage(a.getUUID(), 4, 200);
            real.damage(b.getUUID(), 4, 200);
            assertTrue(AssessmentService.qualifies(real, a.getUUID(), b.getUUID()));
        });
    }

    @Test
    void reachesEveryGradeWithZeroKillsAndZeroWins(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "no_wins", 100); // Low Malice on purpose
            RefinementService.setLevel(p, 10); // Peak has its own test
            UUID one = opponent("one");
            UUID two = opponent("two");
            UUID three = opponent("three");

            GradeService.set(p, Grade.SEMI_GRADE_2);
            GradeService.addXp(p, 1000);
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "XP alone isn't enough");
            for (int i = 0; i < 4; i++) AssessmentService.addFight(p, 60, one);
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "four fights isn't five");
            AssessmentService.addFight(p, 60, one);
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "five fights but all one opponent");
            AssessmentService.addFight(p, 60, two);
            assertEquals(Grade.GRADE_2, Progression.getGrade(p), "now it's met, with no win anywhere");

            GradeService.set(p, Grade.SEMI_GRADE_1);
            for (int i = 0; i < 8; i++) AssessmentService.addFight(p, 75, i % 2 == 0 ? one : two);
            AssessmentService.addFight(p, 75, three);
            AssessmentService.addFight(p, 75, one);
            assertEquals(Grade.SEMI_GRADE_1, Progression.getGrade(p), "the Assessment is met but the XP isn't");
            GradeService.addXp(p, 4000);
            assertEquals(Grade.GRADE_1, Progression.getGrade(p));

            for (int i = 0; i < 18; i++) AssessmentService.addFight(p, 95, i % 2 == 0 ? one : two);
            AssessmentService.addFight(p, 95, three);
            AssessmentService.addFight(p, 95, one);
            assertEquals(Grade.SPECIAL_GRADE, Progression.getGrade(p), "Assessment only, from a Low Malice player");
        });
    }

    @Test
    void everyGateAsksForMoreTechniqueUseThanTheLast(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            double grade2 = AssessmentService.requirement(Grade.GRADE_2).technique();
            double grade1 = AssessmentService.requirement(Grade.GRADE_1).technique();
            double special = AssessmentService.requirement(Grade.SPECIAL_GRADE).technique();
            assertTrue(grade2 < grade1 && grade1 < special, grade2 + " < " + grade1 + " < " + special);
            // With every other part at full marks and a stronger opponent, a 90 average still takes Technique use 80: under that, this gate would never be what stops anyone.
            assertTrue(special > 80, "it has to ask for more than a 90 average already does");
            assertTrue(grade2 > 12.4, "more than one move repeated can ever score");
        });
    }

    @Test
    void spammingOneMoveCantBePromotedWhateverItsAverage(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "spammer", 1000);
            RefinementService.setLevel(p, 10);
            GradeService.set(p, Grade.SEMI_GRADE_2);
            GradeService.addXp(p, 1000);
            // A 60 average beats Grade 2's 50, on Technique use a one-move fight really gets.
            for (int i = 0; i < 5; i++) AssessmentService.addFight(p, 60, 5, opponent(i % 2 == 0 ? "one" : "two"));
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "everything is met but the Technique use");
            for (int i = 0; i < 5; i++) AssessmentService.addFight(p, 60, 45, opponent(i % 2 == 0 ? "one" : "two"));
            assertEquals(Grade.GRADE_2, Progression.getGrade(p), "real variety promotes");
        });
    }

    @Test
    void aRealFightRecordsItsTechniqueUse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer a = sorcerer(server, "records_a", 1000);
            ServerPlayer b = sorcerer(server, "records_b", 1000);
            RefinementService.setLevel(a, 3);
            Fight fight = varied(a, b);
            AssessmentService.Assessed assessed = AssessmentService.assess(new FightReport(a, b, fight, FightOutcome.SURVIVED));
            assertTrue(assessed.technique() > 40, "three different moves: " + assessed.technique());
            assertEquals(assessed.score(), AssessmentService.score(new FightReport(a, b, fight, FightOutcome.SURVIVED)), EPS);
            AssessmentService.addFight(a, assessed.score(), assessed.technique(), b.getUUID());
            AssessmentRules.Entry entry = AssessmentService.recent(a, 1).get(0);
            assertEquals(assessed.technique(), entry.technique(), EPS);

            // A saved fight keeps it, and one saved before it existed reads as none.
            var saved = AssessmentData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, AssessmentService.get(a)).getOrThrow();
            AssessmentData loaded = AssessmentData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
            assertEquals(assessed.technique(), loaded.history().get(0).technique(), EPS);
            net.minecraft.nbt.CompoundTag old = ((net.minecraft.nbt.CompoundTag) saved).copy();
            old.getList("history", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).remove("technique");
            assertEquals(0.0, AssessmentData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow().history().get(0).technique(), EPS);
        });
    }

    @Test
    void aSeveredPlayersMeleeVarietyStandsInForTechniqueUse(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer severed = TestServer.damageable(server, "gate_severed");
            server.overworld().addNewPlayer(severed);
            BirthService.rebirth(severed, OptionalInt.of(0));
            ServerPlayer b = sorcerer(server, "gate_severed_b", 1000);
            Fight plain = new Fight(severed.getUUID(), b.getUUID(), 0);
            Fight mixed = new Fight(severed.getUUID(), b.getUUID(), 0);
            String[] moves = {"sword", "sword_critical", "axe", "axe_sprint", "sword", "fist"};
            for (int i = 0; i < moves.length; i++) {
                plain.melee(severed.getUUID(), "fist", i * 20L);
                mixed.melee(severed.getUUID(), moves[i], i * 20L);
            }
            double spam = AssessmentService.assess(new FightReport(severed, b, plain, FightOutcome.SURVIVED)).technique();
            double varied = AssessmentService.assess(new FightReport(severed, b, mixed, FightOutcome.SURVIVED)).technique();
            assertTrue(spam < AssessmentService.requirement(Grade.GRADE_2).technique(), "the same punch: " + spam);
            assertTrue(varied >= AssessmentService.requirement(Grade.SPECIAL_GRADE).technique(), "they can still reach the top gate: " + varied);
        });
    }

    private static final List<java.util.function.Supplier<InnateTechnique>> REAL_TECHNIQUES = List.of(Rime.RIME::get,
            com.cnethuka.kegare.innate_technique.swap.Swap.SWAP::get, com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation.PUPPET_MANIPULATION::get);

    /** Each move once a second, in this order, {@code rounds} times over. */
    private static double techniqueUse(ServerPlayer a, ServerPlayer b, int rounds, Ability... moves) {
        Fight fight = new Fight(a.getUUID(), b.getUUID(), 0);
        long tick = 0;
        for (int round = 0; round < rounds; round++) {
            for (Ability move : moves) {
                fight.landed(a.getUUID(), move.id().toString(), move.isSpecial(), tick);
                tick += 20;
            }
        }
        return AssessmentService.assess(new FightReport(a, b, fight, FightOutcome.SURVIVED)).technique();
    }

    private static List<Ability> unlocked(ServerPlayer p, InnateTechnique technique) {
        return technique.everyAbility().stream().filter(a -> TechniqueUnlocks.isUnlocked(p, a)).toList();
    }

    /** Runs with Refinement gating on, as on a real server: the test server's default has it off. */
    private static void withGating(Runnable test) {
        boolean before = ProgressionConfig.REQUIRE_REFINEMENT.get();
        ProgressionConfig.REQUIRE_REFINEMENT.set(true);
        try {
            test.run();
        } finally {
            ProgressionConfig.REQUIRE_REFINEMENT.set(before);
        }
    }

    /** Every pair of moves they have, on every real technique, at every level below every Peak that can take these gates. */
    @Test
    void alternatingTwoMovesBelowTheirPeakNeverMeetsGrade1OrSpecialGrade(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            withGating(() -> {
                double grade1 = AssessmentService.requirement(Grade.GRADE_1).technique();
                double special = AssessmentService.requirement(Grade.SPECIAL_GRADE).technique();
                ServerPlayer b = sorcerer(server, "pairs_opponent", 1000);
                int checked = 0;
                double highest = 0;
                int index = 0;
                for (java.util.function.Supplier<InnateTechnique> supplier : REAL_TECHNIQUES) {
                    InnateTechnique technique = supplier.get();
                    ServerPlayer p = sorcerer(server, "pairs_" + index++, 1000);
                    InnateTechniqueService.setInnateTechnique(p, technique);
                    for (int peak = AssessmentService.requirement(Grade.GRADE_1).peakRefinement(); peak <= RefinementService.maxLevel(); peak++) {
                        RefinementService.setLevel(p, peak);
                        for (int level = 0; level < peak; level++) {
                            RefinementService.setLevel(p, level);
                            List<Ability> moves = unlocked(p, technique);
                            for (int i = 0; i < moves.size(); i++) {
                                for (int j = i + 1; j < moves.size(); j++) {
                                    double use = techniqueUse(p, b, 3, moves.get(i), moves.get(j));
                                    String what = technique.id() + " Peak " + peak + " level " + level + ": " + moves.get(i).id() + " and " + moves.get(j).id() + " scored " + use;
                                    assertTrue(use < grade1, what);
                                    assertTrue(use < special, what);
                                    highest = Math.max(highest, use);
                                    checked++;
                                }
                            }
                        }
                    }
                }
                assertTrue(checked > 500, "the loops really ran: " + checked);
                assertEquals(52.5, highest, EPS, "DESIGN §3.21 quotes this");
            });
        });
    }

    @Test
    void aRealRerollToASmallKitNoLongerScoresFullMarksOnTwoMoves(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            withGating(() -> {
                ServerPlayer b = sorcerer(server, "reroll_opponent", 1000);
                ServerPlayer fresh = sorcerer(server, "reroll_fresh", 1000);
                InnateTechnique swap = com.cnethuka.kegare.innate_technique.swap.Swap.SWAP.get();
                InnateTechniqueService.setInnateTechnique(fresh, swap);
                Ability[] two = unlocked(fresh, swap).toArray(new Ability[0]);
                assertEquals(2, two.length, "setup: the level 0 kit");
                assertEquals(100.0, techniqueUse(fresh, b, 3, two), EPS, "fresh, those two are all they have");

                ServerPlayer rerolled = sorcerer(server, "reroll_away", 1000);
                RefinementService.setLevel(rerolled, RefinementService.maxLevel());
                InnateTechniqueService.setInnateTechnique(rerolled, swap);
                assertEquals(0, RefinementService.level(rerolled), "setup: the reroll reset it");
                assertEquals(RefinementService.maxLevel(), RefinementService.peak(rerolled), "setup: Peak stayed");
                double after = techniqueUse(rerolled, b, 3, two);
                assertEquals(100.0 * (40 * 2 / 3.0 + 25 * 2 / 3.0) / 100, after, EPS, "the same two moves");
                assertTrue(after < AssessmentService.requirement(Grade.GRADE_1).technique());
            });
        });
    }

    /** Fresh players are scored as they always were: at their peak, the kit is what they have, level by level. */
    @Test
    void someoneAtTheirPeakScoresExactlyAsBefore(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            withGating(() -> {
                ServerPlayer b = sorcerer(server, "peak_opponent", 1000);
                int index = 0;
                for (java.util.function.Supplier<InnateTechnique> supplier : REAL_TECHNIQUES) {
                    InnateTechnique technique = supplier.get();
                    ServerPlayer p = sorcerer(server, "peak_" + index++, 1000);
                    InnateTechniqueService.setInnateTechnique(p, technique);
                    for (int level = 0; level <= RefinementService.maxLevel(); level++) {
                        RefinementService.setLevel(p, level);
                        var base = TechniqueUnlocks.unlockedBase(p, technique);
                        var specials = TechniqueUnlocks.unlockedSpecials(p, technique);
                        AssessmentScoring.Kit kit = TechniqueUnlocks.scoringKit(p, technique);
                        assertEquals(new AssessmentScoring.Kit(base.size(), specials.size(), false), kit, "level " + level);

                        List<Ability> moves = unlocked(p, technique);
                        Fight fight = new Fight(p.getUUID(), b.getUUID(), 0);
                        for (int i = 0; i < 4; i++) fight.landed(p.getUUID(), moves.get(i % 2).id().toString(), moves.get(i % 2).isSpecial(), i * 20L);
                        double before = 100.0 * AssessmentScoring.techniqueUse(fight.side(p.getUUID()).abilities(), base, specials, AssessmentService.settings());
                        assertEquals(before, AssessmentService.assess(new FightReport(p, b, fight, FightOutcome.SURVIVED)).technique(), EPS, "level " + level);
                    }
                }
            });
        });
    }

    @Test
    void aRerolledPlayerCanStillMeetTheGatesWithRealVariety(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            withGating(() -> {
                ServerPlayer b = sorcerer(server, "variety_opponent", 1000);
                ServerPlayer p = sorcerer(server, "variety", 1000);
                RefinementService.setLevel(p, RefinementService.maxLevel());
                InnateTechnique swap = com.cnethuka.kegare.innate_technique.swap.Swap.SWAP.get();
                InnateTechniqueService.setInnateTechnique(p, swap);

                RefinementService.setLevel(p, 1);
                assertTrue(techniqueUse(p, b, 2, unlocked(p, swap).toArray(new Ability[0])) >= AssessmentService.requirement(Grade.GRADE_1).technique(),
                        "level 1: all three moves meet Grade 1's");
                RefinementService.setLevel(p, 2);
                assertTrue(techniqueUse(p, b, 2, unlocked(p, swap).toArray(new Ability[0])) < AssessmentService.requirement(Grade.SPECIAL_GRADE).technique(),
                        "level 2 is one special short of Special Grade's");
                RefinementService.setLevel(p, 3);
                assertTrue(techniqueUse(p, b, 2, unlocked(p, swap).toArray(new Ability[0])) >= AssessmentService.requirement(Grade.SPECIAL_GRADE).technique(),
                        "level 3 meets it");
            });
        });
    }

    /** With the requirement off everything is unlocked, as it always was: nothing to rebuild, so nothing changes. */
    @Test
    void withGatingOffTheWholeTechniqueIsTheKit(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "gating_off", 1000);
            RefinementService.setLevel(p, 2);
            InnateTechnique technique = InnateTechniqueService.current(p).orElseThrow();
            assertEquals(new AssessmentScoring.Kit(TechniqueUnlocks.unlockedBase(p, technique).size(), TechniqueUnlocks.unlockedSpecials(p, technique).size(), false),
                    TechniqueUnlocks.scoringKit(p, technique));
        });
    }

    @Test
    void peakRefinementGatesPromotionAndSurvivesAReroll(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "peak_gate", 1000);
            GradeService.set(p, Grade.SEMI_GRADE_2);
            GradeService.addXp(p, 1000);
            for (int i = 0; i < 5; i++) AssessmentService.addFight(p, 60, opponent(i % 2 == 0 ? "one" : "two"));
            assertEquals(Grade.SEMI_GRADE_2, Progression.getGrade(p), "low Peak Refinement is the only thing missing");
            RefinementService.setLevel(p, 2);
            assertEquals(Grade.GRADE_2, Progression.getGrade(p), "reaching it promotes at once");

            InnateTechniqueService.setInnateTechnique(p, com.cnethuka.kegare.innate_technique.swap.Swap.SWAP.get());
            assertEquals(0, RefinementService.level(p), "the reroll reset Refinement");
            assertEquals(2, RefinementService.peak(p), "but Peak Refinement stayed");
            assertEquals(Grade.GRADE_2, Progression.getGrade(p));
        });
    }

    @Test
    void aSeveredPlayerSkipsPeakRefinement(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = TestServer.damageable(server, "peak_severed");
            server.overworld().addNewPlayer(p);
            BirthService.rebirth(p, OptionalInt.of(0));
            GradeService.set(p, Grade.GRADE_1);
            for (int i = 0; i < 20; i++) AssessmentService.addFight(p, 95, opponent("s" + i % 3));
            assertEquals(Grade.SPECIAL_GRADE, Progression.getGrade(p));
            assertEquals(0, RefinementService.peak(p));
        });
    }

    @Test
    void aBadRunNeverLowersAGrade(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            TestServer.ensureOverworld(server);
            ServerPlayer p = sorcerer(server, "bad_run", 1000);
            GradeService.set(p, Grade.GRADE_2);
            for (int i = 0; i < 30; i++) AssessmentService.addFight(p, 0, opponent("o" + i));
            assertEquals(Grade.GRADE_2, Progression.getGrade(p));
        });
    }
}
