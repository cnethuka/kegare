package com.cnethuka.kegare.dominion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.TestServer;
import com.cnethuka.kegare.api.dominion.DominionContext;
import com.cnethuka.kegare.api.dominion.DominionLook;
import com.cnethuka.kegare.api.dominion.DominionUltimate;
import com.cnethuka.kegare.api.dominion.KegareDominions;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.rmt.BrainDamage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Dominions' framework (DESIGN §3.35), with a full Dominion that belongs to no technique (the API's own class). */
@ExtendWith(EphemeralTestServerProvider.class)
class DominionTest {
    private static final double EPS = 1.0e-6;
    static final AtomicInteger OPENED = new AtomicInteger(), TICKED = new AtomicInteger(), CLOSED = new AtomicInteger();

    /** A full Dominion with the API's defaults, counting its hooks. */
    static final DominionUltimate TEST = new DominionUltimate(Kegare.id("test_dominion")) {
        @Override public double cost(ServerPlayer player) { return 100; }
        @Override public DominionLook look() { return new DominionLook(0x404040, 0x404040, 24f, 0x101010, Optional.empty(), false, -1, false); }
        @Override public void onOpen(DominionContext ctx) { OPENED.incrementAndGet(); }
        @Override public void tickInside(DominionContext ctx) { TICKED.incrementAndGet(); }
        @Override public void onClose(DominionContext ctx, boolean broken) { CLOSED.incrementAndGet(); }
    };

    static ServerLevel level(MinecraftServer server) {
        TestServer.ensureOverworld(server);
        return server.overworld();
    }

    /**
     * Where the tests stand: high in empty air, the same height for everyone, so nothing another test built (or left) is in the way.
     * Nothing ticks in a test, so nobody falls.
     */
    static final double Y = 200;

    static double ground(ServerLevel level, int x, int z) {
        return Y;
    }

    /** A Malice user standing at (x, ground, z), in the level. */
    static ServerPlayer at(MinecraftServer server, String name, double x, double z) {
        ServerLevel level = level(server);
        ServerPlayer p = TestServer.damageable(server, name);
        BirthService.rebirth(p, OptionalInt.of(1000));
        InnateTechniqueService.setInnateTechnique(p, Rime.RIME.get());
        MaliceService.refill(p);
        p.moveTo(x, ground(level, (int) x, (int) z), z, 0f, 0f);
        p.setHealth(p.getMaxHealth());
        level.addNewPlayer(p);
        return p;
    }

    static DominionEntity open(ServerPlayer caster) {
        DominionMeter.set(caster, 100);
        DominionEntity d = Dominions.open(caster, FullDominions.spec(TEST, caster), CastIntent.ULTIMATE_RELEASE);
        assertNotNull(d, "setup: it opens");
        return d;
    }

    /** As the level would: its clock, then its tick. */
    static void step(DominionEntity d, int ticks) {
        for (int i = 0; i < ticks && !d.isRemoved(); i++) {
            d.tickCount++;
            d.tick();
        }
    }

    static void closed(DominionEntity d) {
        step(d, d.openTicks());
        assertTrue(d.isClosed(), "setup: closed");
    }

    static void cleanUp(MinecraftServer server) {
        Dominions.forgetAllAsIfCrashed();
        TestServer.removePlayers(server);
    }

    @Test
    void openingNeedsAFullMeterAnUnbrokenBrainAndMalice(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer p = at(server, "dom_needs", 8, 8);
                DominionMeter.set(p, 99);
                assertTrue(Dominions.refusal(p, FullDominions.spec(TEST, p)).isPresent(), "99 isn't full");
                DominionMeter.set(p, 100);
                assertTrue(Dominions.refusal(p, FullDominions.spec(TEST, p)).isEmpty(), "a full meter: it can open");
                BrainDamage.set(p, BrainDamage.MAX);
                Optional<net.minecraft.network.chat.Component> brain = Dominions.refusal(p, FullDominions.spec(TEST, p));
                assertTrue(brain.isPresent() && brain.get().getContents().toString().contains("message.kegare.dominion.brain_full"),
                        "a full brain refuses: " + brain);
                BrainDamage.set(p, 0);
                DominionEntity d = open(p);
                assertEquals(0, DominionMeter.get(p), EPS, "it takes the whole meter");
                DominionMeter.set(p, 100);
                assertTrue(Dominions.refusal(p, FullDominions.spec(TEST, p)).isPresent(), "one at a time");
                assertSame(d, Dominions.ownedBy(p).orElseThrow());

                ServerPlayer severed = at(server, "dom_severed", 40, 40);
                BirthService.rebirth(severed, OptionalInt.of(0));
                DominionMeter.add(severed, 100);
                assertEquals(0, DominionMeter.get(severed), EPS, "a Severed player's meter never fills");
                DominionMeter.set(severed, 100);
                assertTrue(Dominions.refusal(severed, FullDominions.spec(TEST, severed)).isPresent(), "and even full, Severed never open one");
            } finally {
                cleanUp(server);
            }
        });
    }

    @Test
    void whatsInsideWhenItSealsIsTakenInAndNobodyCrosses(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = level(server);
            Pig pig = EntityType.PIG.create(level);
            WitherBoss wither = EntityType.WITHER.create(level);
            try {
                ServerPlayer caster = at(server, "dom_trap_caster", 8, 8);
                ServerPlayer in = at(server, "dom_trap_in", 12, 8);
                ServerPlayer out = at(server, "dom_trap_out", 8 + 22, 8);
                pig.moveTo(5, ground(level, 5, 8), 8, 0, 0);
                pig.setNoAi(true);
                level.addFreshEntity(pig);
                wither.moveTo(8, Y, 12, 0, 0);
                wither.setNoAi(true);
                level.addFreshEntity(wither);
                DominionEntity d = open(caster);
                assertTrue(d.trapped.isEmpty(), "spreading: nobody yet");
                closed(d);
                assertTrue(d.trapped.contains(caster.getUUID()) && d.trapped.contains(in.getUUID()) && d.trapped.contains(pig.getUUID()), "inside: taken in");
                assertFalse(d.trapped.contains(out.getUUID()), "outside: not");
                assertFalse(d.trapped.contains(wither.getUUID()), "a boss: never");
                assertTrue(wither.level() == level, "and it stays in the world");
                assertTrue(KegareDominions.isTrapped(in));
                DominionEntity sky = d.space.inner;
                assertTrue(in.level() == sky.level(), "in its inner space");

                // Walking at its sky from inside stops at it; walking at its shell from outside stops at that.
                Vec3 outward = new Vec3(10, 0, 0);
                in.moveTo(sky.centre().x + sky.radius() - 1, in.getY(), sky.centre().z);
                Vec3 cut = DominionBounds.limit(in, outward);
                double end = DominionEntity.middle(in).add(cut).distanceTo(sky.centre());
                assertTrue(end <= sky.radius() - DominionEntity.margin(in) + 1.0e-6, "inside, they stop at its sky: " + end);
                Vec3 cutOut = DominionBounds.limit(out, new Vec3(-20, 0, 0));
                double endOut = DominionEntity.middle(out).add(cutOut).distanceTo(d.centre());
                assertTrue(endOut >= d.radius() + DominionEntity.margin(out) - 1.0e-6, "outside, they stop at its shell: " + endOut);
                assertSame(outward, DominionBounds.limit(wither, outward), "a boss isn't held");

                // Got across another way (a teleport): put back on their side the next tick.
                in.moveTo(sky.centre().x + sky.radius() + 6, in.getY(), sky.centre().z);
                out.moveTo(9, out.getY(), 8);
                step(sky, 1);
                step(d, 1);
                assertTrue(DominionEntity.middle(in).distanceTo(sky.centre()) < sky.radius(), "back inside its inner space");
                assertTrue(DominionEntity.middle(out).distanceTo(d.centre()) > d.radius(), "back outside its shell");
            } finally {
                pig.discard();
                wither.discard();
                cleanUp(server);
            }
        });
    }

    @Test
    void projectilesStopAtItFromEitherSideAndHitsInsideLand(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = level(server);
            Arrow flying = EntityType.ARROW.create(level), hitting = EntityType.ARROW.create(level);
            Arrow leaving = null;
            try {
                ServerPlayer caster = at(server, "dom_proj_caster", 8, 8);
                ServerPlayer in = at(server, "dom_proj_in", 12, 8);
                ServerPlayer out = at(server, "dom_proj_out", 8 + 20, 8);
                DominionEntity d = open(caster);
                closed(d);
                double whole = d.barrierLeft;

                // From outside: an arrow that crossed the shell this tick without hitting anything is gone, and the barrier took it.
                double y = out.getY() + 1;
                flying.setPos(8 + d.radius() - 1, y, 8);
                flying.xo = 8 + d.radius() + 1;
                flying.yo = y;
                flying.zo = 8;
                level.addFreshEntity(flying);
                Dominions.stopProjectiles(d);
                assertTrue(flying.isRemoved(), "stopped at the shell");
                assertTrue(d.barrierLeft < whole, "and it took the hit");

                // An arrow whose hit is across the shell: the hit never happens.
                hitting.setPos(8 + d.radius() + 1, y, 8);
                hitting.xo = hitting.getX();
                hitting.yo = y;
                hitting.zo = 8;
                level.addFreshEntity(hitting);
                ProjectileImpactEvent event = new ProjectileImpactEvent(hitting, new BlockHitResult(new Vec3(8, y, 8),
                        net.minecraft.core.Direction.UP, BlockPos.containing(8, y, 8), false));
                Dominions.onProjectileImpact(event);
                assertTrue(event.isCanceled() && hitting.isRemoved(), "its hit across the barrier is cancelled");

                // From inside: one crossing its sky stops there too, and takes from the same barrier.
                DominionEntity sky = d.space.inner;
                double before = d.barrierLeft;
                leaving = EntityType.ARROW.create(sky.level());
                double iy = in.getY() + 1;
                leaving.setPos(sky.centre().x + sky.radius() + 1, iy, sky.centre().z);
                leaving.xo = sky.centre().x + sky.radius() - 1;
                leaving.yo = iy;
                leaving.zo = sky.centre().z;
                sky.level().addFreshEntity(leaving);
                Dominions.stopProjectiles(sky);
                assertTrue(leaving.isRemoved(), "stopped at its sky");
                assertTrue(d.barrierLeft < before, "one barrier, hit from either side");

                // Inside, hits land as usual.
                float health = in.getHealth();
                in.invulnerableTime = 0;
                in.hurt(in.damageSources().playerAttack(caster), 2f);
                assertEquals(health - 2f, in.getHealth(), 1.0e-4, "inside, hits land");
            } finally {
                flying.discard();
                hitting.discard();
                if (leaving != null) leaving.discard();
                cleanUp(server);
            }
        });
    }

    @Test
    void itBreaksFromHitsInsideAndOutAndTheCasterBurnsOut(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer caster = at(server, "dom_break_caster", 8, 8);
                ServerPlayer in = at(server, "dom_break_in", 12, 8);
                ServerPlayer out = at(server, "dom_break_out", 8 + 20, 8);
                DominionEntity d = open(caster);
                closed(d);
                double whole = d.barrierLeft;
                Dominions.hit(d, 5, d.centre().add(d.radius(), 0, 0), caster);
                assertEquals(whole, d.barrierLeft, EPS, "its own caster's hits never count");
                Dominions.hit(d, 5, d.centre().add(d.radius(), 0, 0), in);
                Dominions.hit(d, 5, d.centre().add(d.radius(), 0, 0), out);
                assertEquals(whole - 10, d.barrierLeft, EPS, "from inside and outside alike");
                assertEquals((whole - 10) / whole, d.barrier(), 1.0e-4, "shown as a share");

                // A swing at the shell from outside, with nothing nearer along the look.
                out.moveTo(8 + d.radius() + 1.5, out.getY(), 8, 90f, 0f); // facing -x: toward the barrier
                out.swinging = true;
                out.swingTime = 0;
                double beforeSwing = d.barrierLeft;
                Dominions.swingsAtTheShell(d);
                assertTrue(d.barrierLeft < beforeSwing, "a swing at it hits it");
                // And one at its sky from inside.
                DominionEntity sky = d.space.inner;
                in.moveTo(sky.centre().x + sky.radius() - 1.5, in.getY(), sky.centre().z, -90f, 0f); // facing +x: toward its sky
                in.swinging = true;
                in.swingTime = 0;
                double beforeInside = d.barrierLeft;
                Dominions.swingsAtTheShell(sky);
                assertTrue(d.barrierLeft < beforeInside, "a swing at its sky hits it too");

                Dominions.hit(d, whole, d.centre(), out);
                assertTrue(d.isRemoved() && d.ended, "broken: it ends at once");
                assertEquals(DominionConfig.ticks(DominionConfig.DOMINION_BURNOUT), Burnout.remaining(caster), "and its caster burns out");
                assertFalse(KegareDominions.isTrapped(in), "everyone inside is free");
                assertTrue(in.level() == level(server), "and back in the world");
                assertSame(new Vec3(5, 0, 0).getClass(), DominionBounds.limit(in, new Vec3(30, 0, 0)).getClass());
                assertEquals(30, DominionBounds.limit(in, new Vec3(30, 0, 0)).x, EPS, "nothing holds them now");
            } finally {
                cleanUp(server);
            }
        });
    }

    @Test
    void itLeavesNothingInTheWorldEndedBrokenOrCrashed(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            ServerLevel level = level(server);
            try {
                assertFalse(DominionRegistry.DOMINION.get().canSerialize(), "its marker is never saved");
                ServerPlayer caster = at(server, "dom_world_caster", 8, 8);
                BlockPos where = BlockPos.containing(8, caster.getY(), 8);
                List<BlockState> before = blocks(level, where, 18);
                for (int round = 0; round < 3; round++) {
                    DominionEntity d = open(caster);
                    closed(d);
                    step(d, 5);
                    switch (round) {
                        case 0 -> step(d, d.duration());
                        case 1 -> Dominions.hit(d, d.barrierLeft + 1, d.centre(), null);
                        default -> Dominions.forgetAllAsIfCrashed();
                    }
                    assertTrue(d.isRemoved(), "gone, round " + round);
                    assertTrue(DominionEntity.in(level).isEmpty(), "no barrier left, round " + round);
                    assertEquals(before, blocks(level, where, 18), "not one block changed, round " + round);
                    if (round == 2) { // left in its inner space by the crash: sent back as they log in
                        InnerSpaces.onLogin(caster);
                        assertTrue(caster.level() == level, "back in the world after the crash");
                    }
                    Burnout.clear(caster);
                }
            } finally {
                cleanUp(server);
            }
        });
    }

    private static List<BlockState> blocks(ServerLevel level, BlockPos centre, int r) {
        List<BlockState> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) out.add(level.getBlockState(pos));
        return out;
    }

    @Test
    void itEndsWhenItsTimeIsUpOrItsCasterDiesLogsOutOrLeaves(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                int burnout = DominionConfig.ticks(DominionConfig.DOMINION_BURNOUT);
                ServerPlayer caster = at(server, "dom_end_caster", 8, 8);
                OPENED.set(0);
                TICKED.set(0);
                CLOSED.set(0);
                DominionEntity d = open(caster);
                closed(d);
                assertEquals(1, OPENED.get(), "onOpen as it closes");
                step(d, d.duration() - 1);
                assertFalse(d.isRemoved(), "not yet");
                step(d, 1);
                assertTrue(d.isRemoved(), "its time is up");
                assertTrue(TICKED.get() >= d.duration() - 1, "tickInside every tick it was open");
                assertEquals(1, CLOSED.get(), "onClose once");
                assertEquals(burnout, Burnout.remaining(caster), "a full Dominion: dominionBurnout");
                assertEquals(com.cnethuka.kegare.rmt.RmtConfig.DOMINION_BURNOUT_ASSUMED.getDefault(), DominionConfig.DOMINION_BURNOUT.getDefault(),
                        "kept equal to the brain's assumption");
                Burnout.clear(caster);

                d = open(caster);
                closed(d);
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(caster));
                assertTrue(d.isRemoved(), "logging out ends it");
                assertEquals(burnout, Burnout.remaining(caster), "and still burns them out");
                Burnout.clear(caster);

                d = open(caster);
                closed(d);
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(caster, net.minecraft.world.level.Level.OVERWORLD,
                        net.minecraft.world.level.Level.NETHER));
                assertTrue(d.isRemoved(), "changing dimension ends it");
                Burnout.clear(caster);

                d = open(caster);
                closed(d);
                caster.hurt(caster.damageSources().fellOutOfWorld(), 1000f);
                assertTrue(d.isRemoved(), "dying ends it");
            } finally {
                cleanUp(server);
            }
        });
    }

    @Test
    void theSureHitFindsNeitherTheSeveredNorTheCaster(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer caster = at(server, "dom_sure_caster", 8, 8);
                ServerPlayer target = at(server, "dom_sure_target", 12, 8);
                ServerPlayer severed = at(server, "dom_sure_severed", 4, 8);
                BirthService.rebirth(severed, OptionalInt.of(0));
                DominionEntity d = open(caster);
                assertTrue(KegareDominions.sureHitTargets(caster).isEmpty(), "nothing while it's rising");
                closed(d);
                List<net.minecraft.world.entity.LivingEntity> found = KegareDominions.sureHitTargets(caster);
                assertTrue(found.contains(target), "someone inside");
                assertFalse(found.contains(caster), "never its caster");
                assertFalse(found.contains(severed), "never a Severed player: no Malice to find");
                assertTrue(d.trapped.contains(severed.getUUID()), "though they're as taken in as anyone");
            } finally {
                cleanUp(server);
            }
        });
    }

    @Test
    void beingCaughtInsidePaysInsightOnce(MinecraftServer server) throws Exception {
        TestServer.onServerThread(server, () -> {
            try {
                ServerPlayer caster = at(server, "dom_insight_caster", 8, 8);
                ServerPlayer in = at(server, "dom_insight_in", 12, 8);
                int casterBefore = Insight.get(caster, InsightCategory.DOMINION), inBefore = Insight.get(in, InsightCategory.DOMINION);
                DominionEntity d = open(caster);
                closed(d);
                assertTrue(Insight.get(in, InsightCategory.DOMINION) > inBefore, "caught inside: caught_in_dominion");
                assertEquals(casterBefore, Insight.get(caster, InsightCategory.DOMINION), "never for its own caster");
                int once = Insight.get(in, InsightCategory.DOMINION);
                Dominions.end(d, Dominions.EndReason.COMMAND);
                Burnout.clear(caster);
                d = open(caster);
                closed(d);
                assertEquals(once, Insight.get(in, InsightCategory.DOMINION), "once per kind of Dominion");
            } finally {
                cleanUp(server);
            }
        });
    }
}
