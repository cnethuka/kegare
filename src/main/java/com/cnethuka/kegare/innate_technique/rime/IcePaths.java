package com.cnethuka.kegare.innate_technique.rime;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ice Path's trails, server side (runtime attachment {@code kegare:ice_paths} on the level; nothing is saved and no
 * blocks are placed). Each caster's trail is a run of points on the ground that expire after the trail time. Anyone
 * but the caster standing on one gets Slowness.
 */
public final class IcePaths {
    /** Trail points this close together are merged. */
    private static final double MIN_SPACING = 0.4;
    /** Lay ice only when the ground is at most this far below the feet. */
    private static final double MAX_HEIGHT = 1.5;
    private static final int CHECK_EVERY = 4;
    private static final int SLOW_TICKS = 20;

    record Point(Vec3 pos, long expires) {}

    /** The attachment's value: every live trail in the level, by caster. */
    public static final class Store {
        final Map<UUID, ArrayDeque<Point>> trails = new HashMap<>();
    }

    private IcePaths() {}

    /** Lays ice under the caster's feet (if the ground is close). */
    static void lay(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 feet = player.position().add(0, 0.2, 0);
        BlockHitResult hit = level.clip(new ClipContext(feet, feet.subtract(0, MAX_HEIGHT, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) return;
        ArrayDeque<Point> trail = level.getData(RimeRegistry.ICE_PATHS).trails.computeIfAbsent(player.getUUID(), u -> new ArrayDeque<>());
        Vec3 at = hit.getLocation();
        Point last = trail.peekLast();
        if (last != null && last.pos().distanceToSqr(at) < MIN_SPACING * MIN_SPACING) return;
        trail.addLast(new Point(at, level.getGameTime() + RimeConfig.ticks(RimeConfig.PATH_TRAIL_SECONDS)));
    }

    /** Every server level tick: melt old ice, and slow anyone else on what's left. */
    static void tick(ServerLevel level) {
        if (!level.hasData(RimeRegistry.ICE_PATHS)) return;
        Map<UUID, ArrayDeque<Point>> trails = level.getData(RimeRegistry.ICE_PATHS).trails;
        if (trails.isEmpty()) return;
        long now = level.getGameTime();
        boolean check = now % CHECK_EVERY == 0;
        double radius = RimeConfig.PATH_TRAIL_RADIUS.get();
        int amplifier = RimeConfig.PATH_SLOWNESS_AMPLIFIER.get();
        Iterator<Map.Entry<UUID, ArrayDeque<Point>>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ArrayDeque<Point>> entry = it.next();
            ArrayDeque<Point> trail = entry.getValue();
            while (!trail.isEmpty() && trail.peekFirst().expires() <= now) trail.removeFirst();
            if (trail.isEmpty()) {
                it.remove();
                continue;
            }
            if (!check) continue;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entry.getKey());
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds(trail).inflate(radius, 1.0, radius),
                    e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(entry.getKey()))) {
                if (onTrail(target, trail, radius)) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, amplifier), owner);
                }
            }
        }
    }

    /** True if {@code target}'s feet are on any point of the trail. */
    static boolean onTrail(LivingEntity target, Iterable<Point> trail, double radius) {
        double r2 = radius * radius;
        for (Point point : trail) {
            double dx = target.getX() - point.pos().x;
            double dz = target.getZ() - point.pos().z;
            double dy = target.getY() - point.pos().y;
            if (dx * dx + dz * dz <= r2 && dy > -0.5 && dy < 1.0) return true;
        }
        return false;
    }

    /** Points of the caster's live trail (tests). */
    static int points(ServerLevel level, UUID caster) {
        if (!level.hasData(RimeRegistry.ICE_PATHS)) return 0;
        ArrayDeque<Point> trail = level.getData(RimeRegistry.ICE_PATHS).trails.get(caster);
        return trail == null ? 0 : trail.size();
    }

    private static AABB bounds(Iterable<Point> trail) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Point p : trail) {
            minX = Math.min(minX, p.pos().x); minY = Math.min(minY, p.pos().y); minZ = Math.min(minZ, p.pos().z);
            maxX = Math.max(maxX, p.pos().x); maxY = Math.max(maxY, p.pos().y); maxZ = Math.max(maxZ, p.pos().z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
