package com.cnethuka.kegare.rmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The green orb's route, with no game running. */
class OrbPathTest {
    private static final OrbPath.Shape SHAPE = new OrbPath.Shape(5, 18f, 45f, 25f, 120f);
    private static final Vec2 START = new Vec2(25f, 0f);
    private static final int SEEDS = 2000;

    private static List<Vec2> route(long seed) {
        return OrbPath.route(RandomSource.create(seed), START, SHAPE);
    }

    /** {@link Vec2} has no equals. */
    private static List<Float> values(List<Vec2> route) {
        List<Float> out = new ArrayList<>();
        for (Vec2 at : route) {
            out.add(at.x);
            out.add(at.y);
        }
        return out;
    }

    /** At 3 blocks, as the service places it, looking along +Z. */
    private static OrbPath world(List<Vec2> route) {
        List<Vec3> points = new ArrayList<>();
        for (Vec2 offset : route) points.add(Vec3.directionFromRotation(offset.y, offset.x).scale(3));
        return new OrbPath(points);
    }

    private static double degreesBetween(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.min(1.0, a.normalize().dot(b.normalize()))));
    }

    @Test
    void aRouteStartsWhereItWasToldAndTakesEveryStep() {
        for (long seed = 0; seed < SEEDS; seed++) {
            List<Vec2> route = route(seed);
            assertEquals(SHAPE.legs() + 1, route.size());
            assertEquals(START, route.get(0));
            for (int i = 1; i < route.size(); i++) {
                float dx = route.get(i).x - route.get(i - 1).x;
                float dy = route.get(i).y - route.get(i - 1).y;
                assertEquals(SHAPE.step(), Math.sqrt(dx * dx + dy * dy), 1.0e-3, "seed " + seed + " leg " + i);
            }
        }
    }

    @Test
    void aRouteStaysInFrontOfThem() {
        for (long seed = 0; seed < SEEDS; seed++) {
            for (Vec2 at : route(seed)) {
                assertTrue(Math.abs(at.x) <= SHAPE.yawRange() + 1.0e-3, "seed " + seed + " yaw " + at.x);
                assertTrue(Math.abs(at.y) <= SHAPE.pitchRange() + 1.0e-3, "seed " + seed + " pitch " + at.y);
            }
        }
    }

    @Test
    void routesAreRandomButTheSameRollGivesTheSameRoute() {
        assertEquals(values(route(7)), values(route(7)));
        Set<List<Float>> seen = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) seen.add(values(route(seed)));
        assertTrue(seen.size() > SEEDS * 0.99, "nearly all different: " + seen.size());
        // And both ways round, up and down: nothing about the turns favours a side.
        long up = 0;
        for (long seed = 0; seed < SEEDS; seed++) if (route(seed).get(SHAPE.legs()).y > 0) up++;
        assertTrue(up > SEEDS * 0.4 && up < SEEDS * 0.6, "ends above the start about half the time: " + up);
    }

    /** It always turns, within what the shape allows, so no leg simply carries on from the last. */
    @Test
    void aRouteWinds() {
        int legs = 0;
        int straight = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            List<Vec2> route = route(seed);
            for (int i = 2; i < route.size(); i++) {
                double a = Math.atan2(route.get(i - 1).y - route.get(i - 2).y, route.get(i - 1).x - route.get(i - 2).x);
                double b = Math.atan2(route.get(i).y - route.get(i - 1).y, route.get(i).x - route.get(i - 1).x);
                double turn = Math.abs(Math.toDegrees(Math.atan2(Math.sin(b - a), Math.cos(b - a))));
                legs++;
                if (turn < SHAPE.maxTurn() / 3f - 0.5f) straight++; // only when cornered and sent back to the middle
            }
        }
        assertTrue(straight < legs * 0.01, straight + " of " + legs + " legs ran on with no real turn");
    }

    /** What makes it fair: no single tick can carry it out of the look cone, and it can't stall at any point either. */
    @Test
    void theOrbMovesSmoothlyAtAboutTheSpeedTheConfigSays() {
        int ticks = 60;
        double expected = SHAPE.legs() * SHAPE.step() / ticks;
        double fastest = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            OrbPath path = world(route(seed));
            for (int t = 0; t < ticks; t++) {
                fastest = Math.max(fastest, degreesBetween(path.at(t / (double) ticks), path.at((t + 1) / (double) ticks)));
            }
        }
        assertTrue(fastest > expected * 0.8, "fastest " + fastest);
        assertTrue(fastest < 10.0 / 3, "under a third of the 10 degree look cone per tick at its fastest: " + fastest);
    }

    @Test
    void theCurvePassesThroughEveryPointAndStopsAtTheEnds() {
        List<Vec3> points = List.of(new Vec3(0, 0, 0), new Vec3(1, 2, 0), new Vec3(3, 1, 1), new Vec3(4, 0, 2));
        OrbPath path = new OrbPath(points);
        for (int i = 0; i < points.size(); i++) {
            assertEquals(0.0, path.at(i / 3.0).distanceTo(points.get(i)), 1.0e-9, "point " + i);
        }
        assertEquals(points.get(0), path.at(-1));
        assertEquals(points.get(3), path.at(2));
        assertNotEquals(points.get(1), path.at(0.5));
    }

    @Test
    void aOnePointPathStaysPut() {
        OrbPath path = new OrbPath(List.of(new Vec3(1, 2, 3)));
        assertEquals(new Vec3(1, 2, 3), path.at(0.5));
    }
}
