package com.cnethuka.kegare.dominion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.FightTracker;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.sixeyes.Edge;
import com.cnethuka.kegare.timing.SubTick;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Dominion clashes (DESIGN §3.35): starting one, a third refused, the Grip, the Maintain's tug of war, the Fight, ties and forfeits. */
@ExtendWith(EphemeralTestServerProvider.class)
class ClashTest {
    private static final double EPS = 1.0e-6;
    /** The clock prompts are timed on, held still unless a test moves it (so no prompt comes unless it does). */
    private static final double[] NOW = {0};

    private static DominionEntity incomplete(ServerPlayer p) {
        DominionMeter.set(p, 100);
        DominionEntity d = Dominions.open(p, IncompleteDominions.spec(p), CastIntent.CAST_RELEASE);
        assertNotNull(d, "setup: it opens");
        return d;
    }

    private static DominionEntity full(ServerPlayer p) {
        return DominionTest.open(p);
    }

    private static boolean earned(ServerPlayer p, ResourceLocation id) {
        return p.getAdvancements().getOrStartProgress(p.server.getAdvancements().get(id)).isDone();
    }

    private static void ticks(Clash c, int n) {
        for (int i = 0; i < n && c.phase() != Clash.Phase.OVER; i++) Clashes.tick(c);
    }

    /** Through the Grip, both holding, into the Maintain. */
    private static void toMaintain(Clash c) {
        ticks(c, DominionConfig.ticks(DominionConfig.CLASH_GRIP));
        assertEquals(Clash.Phase.MAINTAIN, c.phase(), "setup: holding on");
    }

    /** Through the Maintain (the clock held still: no prompts) into the Fight. */
    private static void toFight(Clash c) {
        toMaintain(c);
        ticks(c, DominionConfig.ticks(DominionConfig.CLASH_MAINTAIN));
        assertEquals(Clash.Phase.FIGHT, c.phase(), "setup: to the fight");
    }

    /** Moves the clock until this side has a prompt waiting (the other side's go unanswered meanwhile: misses). */
    private static void waitForPrompt(Clash c, Clash.Side s) {
        for (int i = 0; i < 100 && s.promptKey() < 0 && c.phase() != Clash.Phase.OVER; i++) {
            NOW[0] += 200; // a fifth of a second a tick: prompts come every 5 to 10 ticks, well inside the Maintain
            Clashes.tick(c);
        }
    }

    /** Waits for this side's next prompt, then answers it on its beat (with the right key, or {@code key} if not -1). */
    private static void answer(Clash c, Clash.Side s, int key) {
        waitForPrompt(c, s);
        if (c.phase() == Clash.Phase.OVER) return;
        assertTrue(s.promptKey() >= 0, "setup: a prompt came");
        Clashes.onPress(Dominions.caster(s.dominion()), key < 0 ? s.promptKey() : key, s.promptBeatMs());
    }

    private static void run(MinecraftServer server, Consumer<MinecraftServer> test) throws Exception {
        TestServer.onServerThread(server, () -> {
            NOW[0] = 0;
            Clashes.clock = () -> NOW[0];
            try {
                test.accept(server);
            } finally {
                Clashes.clock = SubTick::nowMs;
                Clashes.clear();
                FightTracker.clear();
                DominionTest.cleanUp(server);
            }
        });
    }

    @Test
    void openingOverAnotherStartsAClashAndAThirdIsRefused(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "clash_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "clash_b", 14, 8);
            ServerPlayer c = DominionTest.at(s, "clash_c", -8, 8);
            DominionEntity da = incomplete(a);
            DominionTest.closed(da);
            assertTrue(Dominions.trapping(b).isPresent(), "setup: b is trapped in a's");
            int insight = Insight.get(b, InsightCategory.DOMINION);
            DominionEntity db = incomplete(b);
            Clash clash = Clashes.of(da);
            assertNotNull(clash, "they clash");
            assertSame(clash, Clashes.of(db));
            assertSame(da, clash.first().dominion().primary(), "the one that was there first (met in its inner space, at its sky)");
            assertSame(da.space.inner, clash.first().dominion(), "the clash is in its inner space");
            assertTrue(InnerSpaces.isInnerSpace(db.level()), "opened by someone it took in");
            assertEquals(DominionEntity.State.CLASH, da.state());
            assertEquals(DominionEntity.State.CLASH, da.space.inner.state(), "its sky half-formed too");
            assertEquals(DominionEntity.State.CLASH, db.state());
            assertEquals(db.getId(), da.space.inner.clashWith());
            assertTrue(Dominions.trapping(b).isEmpty(), "contested: nobody's held by a sealed barrier");
            assertTrue(InnerSpaces.isInnerSpace(b.level()), "though nobody's let out of the inner space while it lasts");
            assertTrue(Insight.get(b, InsightCategory.DOMINION) > insight, "clashed_with_dominion paid");

            DominionMeter.set(c, 100);
            double malice = MaliceService.get(c).current();
            Optional<Component> refused = Dominions.refusal(c, IncompleteDominions.spec(c));
            assertTrue(refused.isPresent() && refused.get().getContents().toString().contains("message.kegare.dominion.clash.full"),
                    "a third side is refused: " + refused);
            ServerPlayer inside = DominionTest.at(s, "clash_inside", 0, 0);
            InnerSpaces.move(inside, da.space.level, da.space.middle().add(3, 0, 0), 0f, 0f);
            DominionMeter.set(inside, 100);
            Optional<Component> insideToo = Dominions.refusal(inside, IncompleteDominions.spec(inside));
            assertTrue(insideToo.isPresent() && insideToo.get().getContents().toString().contains("message.kegare.dominion.clash.full"),
                    "inside its inner space too: " + insideToo);
            assertNull(Dominions.open(c, IncompleteDominions.spec(c), CastIntent.CAST_RELEASE));
            assertEquals(100, DominionMeter.get(c), EPS, "and pays nothing");
            assertEquals(malice, MaliceService.get(c).current(), EPS);
        });
    }

    @Test
    void notHoldingOnInTheGripLosesIt(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "grip_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "grip_b", 14, 8);
            DominionEntity da = incomplete(a);
            DominionTest.closed(da);
            DominionTest.step(da, 40); // two seconds of its time used
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            Clashes.onHold(a, false); // let go long ago, never pressed again
            Clashes.onHold(b, true);
            ticks(c, DominionConfig.ticks(DominionConfig.CLASH_GRIP));
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(da.isRemoved(), "a's barrier broke");
            assertEquals(DominionConfig.ticks(DominionConfig.INCOMPLETE_BURNOUT), Burnout.remaining(a), "and a burns out as usual");
            DominionEntity won = Dominions.ownedBy(b).orElseThrow();
            assertTrue(won.isClosed(), "b's stands sealed: it took the inner space over");
            assertTrue(won.level() == da.level() && won.centre().distanceTo(da.centre()) < 1.0e-6, "its shell in the world where a's was");
            assertTrue(InnerSpaces.isInnerSpace(a.level()) && Dominions.trapping(a).isPresent(), "everyone still inside, now in b's");
            assertEquals(0, Burnout.remaining(b));
            assertTrue(earned(b, KegareAdvancements.DOMINION_PUSHBACK), "won before the fight: Pushback");
            assertFalse(earned(a, KegareAdvancements.DOMINION_PUSHBACK));
        });
    }

    @Test
    void theMaintainIsATugOfWarWonByTiming(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "tug_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "tug_b", 14, 8);
            DominionEntity da = incomplete(a);
            DominionTest.closed(da);
            DominionTest.step(da, 60);
            int used = da.tickCount - da.closedAt();
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            toMaintain(c);
            assertTrue(Clashes.holding(a) && Clashes.holding(b), "both holding on");
            double malice = MaliceService.get(a).current();
            ticks(c, 20);
            assertEquals(malice - DominionConfig.CLASH_DRAIN.get(), MaliceService.get(a).current(), 0.5, "a second of drain");

            // A miss: the wrong key. It pushes back and cracks a's own barrier.
            Clash.Side first = c.first();
            answer(c, first, -1);
            assertTrue(c.bar(first) > 0, "setup: a hit first");
            waitForPrompt(c, first);
            double before = c.bar(first);
            float barrier = da.barrier();
            Clashes.onPress(a, (first.promptKey() + 1) % 3, first.promptBeatMs());
            assertEquals(before - DominionConfig.CLASH_PUSH_PER_MISS.get(), c.bar(first), EPS, "a miss pushes it back");
            assertEquals(barrier - DominionConfig.CLASH_CRACK_PER_MISS.get(), da.barrier(), 1.0e-4, "and cracks their barrier");
            // Too late is a miss too.
            waitForPrompt(c, first);
            double beforeLate = c.bar(first);
            Clashes.onPress(a, first.promptKey(), first.promptBeatMs() + DominionConfig.CLASH_PROMPT_WINDOW_MS.get());
            assertEquals(beforeLate - DominionConfig.CLASH_PUSH_PER_MISS.get(), c.bar(first), EPS, "too late");

            // Hits until it's all the way.
            for (int i = 0; i < 20 && c.phase() != Clash.Phase.OVER; i++) answer(c, first, -1);
            assertEquals(Clash.Phase.OVER, c.phase(), "pushed all the way");
            assertTrue(db.isRemoved(), "b's barrier broke");
            assertEquals(DominionConfig.ticks(DominionConfig.INCOMPLETE_BURNOUT), Burnout.remaining(b), "b burns out");
            assertTrue(da.isClosed(), "a's closes again");
            assertTrue(InnerSpaces.isInnerSpace(b.level()), "with b still in its inner space");
            assertEquals(used, da.tickCount - da.closedAt(), "with the time it had used kept");
            assertTrue(earned(a, KegareAdvancements.DOMINION_PUSHBACK));
        });
    }

    @Test
    void anIncompleteDominionPushesLessAgainstAFullOne(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "handicap_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "handicap_b", 14, 8);
            full(a);
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(db);
            assertEquals(1 - DominionConfig.CLASH_INCOMPLETE_HANDICAP.get(), Clashes.handicap(c, c.second()), EPS, "the Incomplete one");
            assertEquals(1.0, Clashes.handicap(c, c.first()), EPS, "the full one pushes in full");
            toMaintain(c);
            answer(c, c.second(), -1);
            assertEquals(DominionConfig.CLASH_PUSH_PER_HIT.get() * (1 - DominionConfig.CLASH_INCOMPLETE_HANDICAP.get()), c.bar(c.second()), EPS);
        });
    }

    @Test
    void runningDryOrLettingGoLosesIt(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "dry_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "dry_b", 14, 8);
            DominionEntity da = incomplete(a);
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            toMaintain(c);
            MaliceService.setCurrent(b, 0.1);
            ticks(c, 2);
            assertEquals(Clash.Phase.OVER, c.phase(), "out of Malice");
            assertTrue(db.isRemoved());
            assertTrue(da.isClosed(), "a's had never closed: it closes now");
            assertTrue(earned(a, KegareAdvancements.DOMINION_PUSHBACK));

            ServerPlayer x = DominionTest.at(s, "dry_x", 80, 80);
            ServerPlayer y = DominionTest.at(s, "dry_y", 86, 80);
            incomplete(x);
            DominionEntity dy = incomplete(y);
            Clash c2 = Clashes.of(dy);
            toMaintain(c2);
            Clashes.onHold(y, false);
            ticks(c2, 1);
            assertEquals(Clash.Phase.OVER, c2.phase(), "letting go");
            assertTrue(dy.isRemoved());
        });
    }

    @Test
    void holdingOnTheyCantAttackOrCast(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "still_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "still_b", 14, 8);
            incomplete(a);
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(db);
            assertTrue(Clashes.holding(a), "the Grip counts");
            b.invulnerableTime = 0;
            float hp = b.getHealth();
            b.hurt(a.damageSources().playerAttack(a), 4f);
            assertEquals(hp, b.getHealth(), EPS, "no attacking");
            InnateTechniqueService.select(a, 0);
            CastManager.handleIntent(a, CastIntent.CAST_PRESS);
            CastManager.handleIntent(a, CastIntent.CAST_RELEASE);
            assertEquals(0, InnateTechniqueService.cooldownRemaining(a, com.cnethuka.kegare.innate_technique.rime.Rime.RIME.get().abilities().get(0)),
                    "no casting");
            assertTrue(db.holdAt != null, "held in place");
            toFight(c);
            assertFalse(Clashes.holding(a), "free in the fight");
            assertNull(db.holdAt);
        });
    }

    @Test
    void theFightIsWonByDamageBetweenTheTwoAlone(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "fight_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "fight_b", 14, 8);
            ServerPlayer x = DominionTest.at(s, "fight_x", 11, 12);
            DominionEntity da = incomplete(a);
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            assertTrue(Dominions.sureHitTargets(da).isEmpty() && Dominions.sureHitTargets(db).isEmpty(), "no sure-hit in a clash");
            toFight(c);
            FightTracker.damage(a, b, 6);
            FightTracker.damage(b, a, 3);
            FightTracker.damage(x, b, 50);
            FightTracker.damage(b, x, 50);
            assertEquals(6, c.first().score(), EPS, "a's damage to b");
            assertEquals(3, c.second().score(), EPS, "b's to a; nobody else's counts");
            ticks(c, DominionConfig.ticks(DominionConfig.CLASH_FIGHT));
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(db.isRemoved(), "b broke");
            assertTrue(da.isClosed());
            assertTrue(earned(a, KegareAdvancements.DOMINION_FIGHT_ME), "won in the fight: Fight Me");
            assertFalse(earned(a, KegareAdvancements.DOMINION_PUSHBACK));
        });
    }

    @Test
    void aCloseFightBreaksBothAndExhaustsThem(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "tie_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "tie_b", 14, 8);
            DominionEntity da = incomplete(a);
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            toFight(c);
            FightTracker.damage(a, b, 10);
            FightTracker.damage(b, a, 9.5);
            ticks(c, DominionConfig.ticks(DominionConfig.CLASH_FIGHT));
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(da.isRemoved() && db.isRemoved(), "both broke");
            assertEquals(DominionConfig.ticks(DominionConfig.CLASH_EXHAUSTED_BURNOUT), Burnout.remaining(a), "exhausted");
            assertEquals(DominionConfig.ticks(DominionConfig.CLASH_EXHAUSTED_BURNOUT), Burnout.remaining(b));
            assertTrue(earned(a, KegareAdvancements.DOMINION_MUTUAL_DESTRUCTION) && earned(b, KegareAdvancements.DOMINION_MUTUAL_DESTRUCTION));
        });
    }

    @Test
    void aCasterLeavingForfeitsWithNothingWon(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DominionTest.at(s, "forfeit_a", 8, 8);
            ServerPlayer b = DominionTest.at(s, "forfeit_b", 14, 8);
            DominionEntity da = incomplete(a);
            DominionEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            assertTrue(Edge.topTier(a, b) && Edge.topTier(b, a), "each is top-tier to the other's Six Eyes");
            toMaintain(c);
            Dominions.end(db, Dominions.EndReason.COMMAND);
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(da.isClosed(), "a's closes");
            assertFalse(earned(a, KegareAdvancements.DOMINION_PUSHBACK), "but nothing was won");
            assertFalse(Edge.topTier(a, b), "no longer clashing");
        });
    }

    @Test
    void theAdvancementsAreHiddenAndSilentOnTheBaseTab(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (ResourceLocation id : List.of(KegareAdvancements.DOMINION_PUSHBACK, KegareAdvancements.DOMINION_FIGHT_ME,
                    KegareAdvancements.DOMINION_MUTUAL_DESTRUCTION)) {
                var holder = server.getAdvancements().get(id);
                assertNotNull(holder, id + " was generated");
                var display = holder.value().display().orElseThrow();
                assertFalse(display.shouldAnnounceChat(), id + " announces nothing");
                assertTrue(display.isHidden(), id + " is hidden");
                assertEquals(KegareAdvancements.AWAKENING, holder.value().parent().orElseThrow(), id + " is on the Base tab");
            }
        });
    }
}
