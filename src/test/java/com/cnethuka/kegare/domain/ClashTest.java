package com.cnethuka.kegare.domain;

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

/** Domain clashes (DESIGN §3.35): starting one, a third refused, the Grip, the Maintain's tug of war, the Fight, ties and forfeits. */
@ExtendWith(EphemeralTestServerProvider.class)
class ClashTest {
    private static final double EPS = 1.0e-6;
    /** The clock the line sweeps on, held still unless a test moves it. */
    private static final double[] NOW = {0};

    private static DomainEntity incomplete(ServerPlayer p) {
        DomainMeter.set(p, 100);
        DomainEntity d = Domains.open(p, IncompleteDomains.spec(p), CastIntent.CAST_RELEASE);
        assertNotNull(d, "setup: it opens");
        return d;
    }

    private static DomainEntity full(ServerPlayer p) {
        return DomainTest.open(p);
    }

    private static boolean earned(ServerPlayer p, ResourceLocation id) {
        return p.getAdvancements().getOrStartProgress(p.server.getAdvancements().get(id)).isDone();
    }

    private static void ticks(Clash c, int n) {
        for (int i = 0; i < n && c.phase() != Clash.Phase.OVER; i++) Clashes.tick(c);
    }

    /** Through the Grip, both holding, into the Maintain. */
    private static void toMaintain(Clash c) {
        ticks(c, DomainConfig.ticks(DomainConfig.CLASH_GRIP));
        assertEquals(Clash.Phase.MAINTAIN, c.phase(), "setup: holding on");
    }

    /** Through the Maintain (nobody pressing) into the Fight. */
    private static void toFight(Clash c) {
        toMaintain(c);
        ticks(c, DomainConfig.ticks(DomainConfig.CLASH_MAINTAIN));
        assertEquals(Clash.Phase.FIGHT, c.phase(), "setup: to the fight");
    }

    /** A moment (server ms) at which the line is {@code offset} of the meter from the middle of this side's mark. */
    private static double when(Clash c, Clash.Side s, double offset) {
        double at = s.mark() + offset;
        if (at < 0 || at > 1) at = s.mark() - offset;
        return c.sweepStartMs() + at * Clashes.sweepMs(); // on its first crossing the line is at t / sweepMs
    }

    /** This side presses with the line on the middle of their mark. */
    private static void answer(Clash c, Clash.Side s) {
        if (c.phase() == Clash.Phase.OVER) return;
        Clashes.onPress(Domains.caster(s.domain()), when(c, s, 0));
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
                DomainTest.cleanUp(server);
            }
        });
    }

    @Test
    void openingOverAnotherStartsAClashAndAThirdIsRefused(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "clash_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "clash_b", 14, 8);
            ServerPlayer c = DomainTest.at(s, "clash_c", -8, 8);
            DomainEntity da = incomplete(a);
            DomainTest.closed(da);
            assertTrue(Domains.trapping(b).isPresent(), "setup: b is trapped in a's");
            int insight = Insight.get(b, InsightCategory.DOMAIN);
            DomainEntity db = incomplete(b);
            Clash clash = Clashes.of(da);
            assertNotNull(clash, "they clash");
            assertSame(clash, Clashes.of(db));
            assertSame(da, clash.first().domain().primary(), "the one that was there first (met in its inner space, at its sky)");
            assertSame(da.space.inner, clash.first().domain(), "the clash is in its inner space");
            assertTrue(InnerSpaces.isInnerSpace(db.level()), "opened by someone it took in");
            assertEquals(DomainEntity.State.CLASH, da.state());
            assertEquals(DomainEntity.State.CLASH, da.space.inner.state(), "its sky half-formed too");
            assertEquals(DomainEntity.State.CLASH, db.state());
            assertEquals(db.getId(), da.space.inner.clashWith());
            assertTrue(Domains.trapping(b).isEmpty(), "contested: nobody's held by a sealed barrier");
            assertTrue(InnerSpaces.isInnerSpace(b.level()), "though nobody's let out of the inner space while it lasts");
            assertTrue(Insight.get(b, InsightCategory.DOMAIN) > insight, "clashed_with_domain paid");

            DomainMeter.set(c, 100);
            double malice = MaliceService.get(c).current();
            Optional<Component> refused = Domains.refusal(c, IncompleteDomains.spec(c));
            assertTrue(refused.isPresent() && refused.get().getContents().toString().contains("message.kegare.domain.clash.full"),
                    "a third side is refused: " + refused);
            ServerPlayer inside = DomainTest.at(s, "clash_inside", 0, 0);
            InnerSpaces.move(inside, da.space.level, da.space.middle().add(3, 0, 0), 0f, 0f);
            DomainMeter.set(inside, 100);
            Optional<Component> insideToo = Domains.refusal(inside, IncompleteDomains.spec(inside));
            assertTrue(insideToo.isPresent() && insideToo.get().getContents().toString().contains("message.kegare.domain.clash.full"),
                    "inside its inner space too: " + insideToo);
            assertNull(Domains.open(c, IncompleteDomains.spec(c), CastIntent.CAST_RELEASE));
            assertEquals(100, DomainMeter.get(c), EPS, "and pays nothing");
            assertEquals(malice, MaliceService.get(c).current(), EPS);
        });
    }

    @Test
    void notHoldingOnInTheGripLosesIt(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "grip_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "grip_b", 14, 8);
            DomainEntity da = incomplete(a);
            DomainTest.closed(da);
            DomainTest.step(da, 40); // two seconds of its time used
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            Clashes.onHold(a, false); // let go long ago, never pressed again
            Clashes.onHold(b, true);
            ticks(c, DomainConfig.ticks(DomainConfig.CLASH_GRIP));
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(da.isRemoved(), "a's barrier broke");
            assertEquals(DomainConfig.ticks(DomainConfig.INCOMPLETE_BURNOUT), Burnout.remaining(a), "and a burns out as usual");
            DomainEntity won = Domains.ownedBy(b).orElseThrow();
            assertTrue(won.isClosed(), "b's stands sealed: it took the inner space over");
            assertTrue(won.level() == da.level() && won.centre().distanceTo(da.centre()) < 1.0e-6, "its shell in the world where a's was");
            assertTrue(InnerSpaces.isInnerSpace(a.level()) && Domains.trapping(a).isPresent(), "everyone still inside, now in b's");
            assertEquals(0, Burnout.remaining(b));
            assertTrue(earned(b, KegareAdvancements.DOMAIN_PUSHBACK), "won before the fight: Pushback");
            assertFalse(earned(a, KegareAdvancements.DOMAIN_PUSHBACK));
        });
    }

    @Test
    void theMaintainIsATugOfWarWonByTiming(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "tug_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "tug_b", 14, 8);
            DomainEntity da = incomplete(a);
            DomainTest.closed(da);
            DomainTest.step(da, 60);
            int used = da.tickCount - da.closedAt();
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            toMaintain(c);
            assertTrue(Clashes.holding(a) && Clashes.holding(b), "both holding on");
            double malice = MaliceService.get(a).current();
            ticks(c, 20);
            assertEquals(malice - DomainConfig.CLASH_DRAIN.get(), MaliceService.get(a).current(), 0.5, "a second of drain");

            // The line sweeps across and back; each side has a mark on its own meter.
            Clash.Side first = c.first();
            double sweep = Clashes.sweepMs(), start = c.sweepStartMs(), half = Clashes.markHalf();
            assertEquals(0.0, Clashes.lineAt(c, start), EPS, "from the left end");
            assertEquals(0.25, Clashes.lineAt(c, start + sweep * 0.25), EPS);
            assertEquals(1.0, Clashes.lineAt(c, start + sweep), EPS, "across");
            assertEquals(0.75, Clashes.lineAt(c, start + sweep * 1.25), EPS, "and back");
            assertEquals(0.0, Clashes.lineAt(c, start + sweep * 2), EPS);
            assertTrue(first.mark() >= half && first.mark() <= 1 - half, "the whole mark on the meter: " + first.mark());

            // On the middle of the mark: the whole push. Then the mark moves.
            double mark = first.mark();
            answer(c, first);
            double perfect = DomainConfig.CLASH_PUSH_PER_HIT.get() * DomainConfig.CLASH_PERFECT_PUSH.get();
            assertEquals(perfect, c.bar(first), EPS, "on the middle: a perfect press, pushing more");
            assertTrue(perfect > Clashes.push(Clashes.perfectFrom() - 0.01) * 1.5, "well over the best hit that isn't one");
            assertEquals(perfect, Clashes.push(Clashes.perfectFrom()), EPS, "from the edge of the perfect zone");
            assertTrue(Math.abs(first.mark() - mark) >= 0.2 - EPS, "the mark moved: " + mark + " to " + first.mark());
            // Near its edge: still a hit, for less.
            double before = c.bar(first);
            Clashes.onPress(a, when(c, first, half * 0.9));
            double edge = DomainConfig.CLASH_EDGE_SHARE.get();
            assertEquals(before + DomainConfig.CLASH_PUSH_PER_HIT.get() * (edge + (1 - edge) * 0.1), c.bar(first), 1.0e-6, "near the edge: less");
            // Outside it: a miss. It pushes back and cracks a's own barrier.
            before = c.bar(first);
            float barrier = da.barrier();
            Clashes.onPress(a, when(c, first, half * 1.5));
            assertEquals(before - DomainConfig.CLASH_PUSH_PER_MISS.get(), c.bar(first), EPS, "a miss pushes it back");
            assertEquals(barrier - DomainConfig.CLASH_CRACK_PER_MISS.get(), da.barrier(), 1.0e-4, "and cracks their barrier");
            // Not pressing costs nothing.
            before = c.bar(first);
            ticks(c, 20);
            assertEquals(before, c.bar(first), EPS, "no press: nothing");
            // The other side pushes it back the other way.
            answer(c, c.second());
            assertEquals(before - perfect, c.bar(first), EPS, "one bar, pushed both ways");

            // Hits until it's all the way.
            for (int i = 0; i < 20 && c.phase() != Clash.Phase.OVER; i++) answer(c, first);
            assertEquals(Clash.Phase.OVER, c.phase(), "pushed all the way");
            assertTrue(db.isRemoved(), "b's barrier broke");
            assertEquals(DomainConfig.ticks(DomainConfig.INCOMPLETE_BURNOUT), Burnout.remaining(b), "b burns out");
            assertTrue(da.isClosed(), "a's closes again");
            assertTrue(InnerSpaces.isInnerSpace(b.level()), "with b still in its inner space");
            assertEquals(used, da.tickCount - da.closedAt(), "with the time it had used kept");
            assertTrue(earned(a, KegareAdvancements.DOMAIN_PUSHBACK));
        });
    }

    @Test
    void anIncompleteDomainPushesLessAgainstAFullOne(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "handicap_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "handicap_b", 14, 8);
            full(a);
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(db);
            assertEquals(1 - DomainConfig.CLASH_INCOMPLETE_HANDICAP.get(), Clashes.handicap(c, c.second()), EPS, "the Incomplete one");
            assertEquals(1.0, Clashes.handicap(c, c.first()), EPS, "the full one pushes in full");
            toMaintain(c);
            answer(c, c.second());
            assertEquals(DomainConfig.CLASH_PUSH_PER_HIT.get() * DomainConfig.CLASH_PERFECT_PUSH.get() * (1 - DomainConfig.CLASH_INCOMPLETE_HANDICAP.get()), c.bar(c.second()), EPS);
        });
    }

    @Test
    void runningDryOrLettingGoLosesIt(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "dry_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "dry_b", 14, 8);
            DomainEntity da = incomplete(a);
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            toMaintain(c);
            MaliceService.setCurrent(b, 0.1);
            ticks(c, 2);
            assertEquals(Clash.Phase.OVER, c.phase(), "out of Malice");
            assertTrue(db.isRemoved());
            assertTrue(da.isClosed(), "a's had never closed: it closes now");
            assertTrue(earned(a, KegareAdvancements.DOMAIN_PUSHBACK));

            ServerPlayer x = DomainTest.at(s, "dry_x", 80, 80);
            ServerPlayer y = DomainTest.at(s, "dry_y", 86, 80);
            incomplete(x);
            DomainEntity dy = incomplete(y);
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
            ServerPlayer a = DomainTest.at(s, "still_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "still_b", 14, 8);
            incomplete(a);
            DomainEntity db = incomplete(b);
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
            ServerPlayer a = DomainTest.at(s, "fight_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "fight_b", 14, 8);
            ServerPlayer x = DomainTest.at(s, "fight_x", 11, 12);
            DomainEntity da = incomplete(a);
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            assertTrue(Domains.sureHitTargets(da).isEmpty() && Domains.sureHitTargets(db).isEmpty(), "no sure-hit in a clash");
            toFight(c);
            FightTracker.damage(a, b, 6);
            FightTracker.damage(b, a, 3);
            FightTracker.damage(x, b, 50);
            FightTracker.damage(b, x, 50);
            assertEquals(6, c.first().score(), EPS, "a's damage to b");
            assertEquals(3, c.second().score(), EPS, "b's to a; nobody else's counts");
            ticks(c, DomainConfig.ticks(DomainConfig.CLASH_FIGHT));
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(db.isRemoved(), "b broke");
            assertTrue(da.isClosed());
            assertTrue(earned(a, KegareAdvancements.DOMAIN_FIGHT_ME), "won in the fight: Fight Me");
            assertFalse(earned(a, KegareAdvancements.DOMAIN_PUSHBACK));
        });
    }

    @Test
    void aCloseFightBreaksBothAndExhaustsThem(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "tie_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "tie_b", 14, 8);
            DomainEntity da = incomplete(a);
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            toFight(c);
            FightTracker.damage(a, b, 10);
            FightTracker.damage(b, a, 9.5);
            ticks(c, DomainConfig.ticks(DomainConfig.CLASH_FIGHT));
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(da.isRemoved() && db.isRemoved(), "both broke");
            assertEquals(DomainConfig.ticks(DomainConfig.CLASH_EXHAUSTED_BURNOUT), Burnout.remaining(a), "exhausted");
            assertEquals(DomainConfig.ticks(DomainConfig.CLASH_EXHAUSTED_BURNOUT), Burnout.remaining(b));
            assertTrue(earned(a, KegareAdvancements.DOMAIN_MUTUAL_DESTRUCTION) && earned(b, KegareAdvancements.DOMAIN_MUTUAL_DESTRUCTION));
        });
    }

    @Test
    void aCasterLeavingForfeitsWithNothingWon(MinecraftServer server) throws Exception {
        run(server, s -> {
            ServerPlayer a = DomainTest.at(s, "forfeit_a", 8, 8);
            ServerPlayer b = DomainTest.at(s, "forfeit_b", 14, 8);
            DomainEntity da = incomplete(a);
            DomainEntity db = incomplete(b);
            Clash c = Clashes.of(da);
            assertTrue(Edge.topTier(a, b) && Edge.topTier(b, a), "each is top-tier to the other's Six Eyes");
            toMaintain(c);
            Domains.end(db, Domains.EndReason.COMMAND);
            assertEquals(Clash.Phase.OVER, c.phase());
            assertTrue(da.isClosed(), "a's closes");
            assertFalse(earned(a, KegareAdvancements.DOMAIN_PUSHBACK), "but nothing was won");
            assertFalse(Edge.topTier(a, b), "no longer clashing");
        });
    }

    @Test
    void theAdvancementsAreHiddenAndSilentOnTheBaseTab(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            for (ResourceLocation id : List.of(KegareAdvancements.DOMAIN_PUSHBACK, KegareAdvancements.DOMAIN_FIGHT_ME,
                    KegareAdvancements.DOMAIN_MUTUAL_DESTRUCTION)) {
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
