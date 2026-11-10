package com.cnethuka.kegare.battledamage;

import com.cnethuka.kegare.impact.Impact;
import com.cnethuka.kegare.impact.ImpactTier;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Wall slams (DESIGN §3.24): for a few seconds after a hit that could break the world, its victim is watched (even where nothing may
 * break: the slam still counts for "Lights Out", §3.28). If they're going at {@code minSpeed} or more and stop dead against a wall,
 * floor or ceiling (their speed that way gone, with blocks right there that way), they leave a crater where they hit, scaled by
 * their speed. Craters only: a slam does no damage of its own. Speed is read from their position each tick (a player's client moves
 * them), so it works the same on players and mobs.
 */
final class WallSlams {
    private static final class Watch {
        final ServerLevel level;
        final @Nullable Entity attacker;
        final long until;
        @Nullable Vec3 last;
        @Nullable Vec3 velocity;

        Watch(ServerLevel level, @Nullable Entity attacker, long until) {
            this.level = level;
            this.attacker = attacker;
            this.until = until;
        }
    }

    private static final Map<LivingEntity, Watch> WATCHED = new LinkedHashMap<>();

    private WallSlams() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) tick(level);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> WATCHED.clear());
    }

    /** A hit that could break the world: its victim may be thrown into something. */
    static void watch(Impact impact) {
        if (!BattleDamageConfig.SLAMS.get() || impact.tier().ordinal() < BattleDamageConfig.MIN_TIER.get().ordinal()) return;
        long until = impact.level().getGameTime() + Math.round(BattleDamageConfig.SLAM_WINDOW.get() * 20);
        WATCHED.put(impact.victim(), new Watch(impact.level(), impact.attacker(), until));
    }

    static void tick(ServerLevel level) {
        if (WATCHED.isEmpty()) return;
        long now = level.getGameTime();
        double minSpeed = BattleDamageConfig.SLAM_MIN_SPEED.get();
        for (Iterator<Map.Entry<LivingEntity, Watch>> it = WATCHED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Watch> e = it.next();
            LivingEntity victim = e.getKey();
            Watch w = e.getValue();
            if (w.level != level) continue;
            if (!victim.isAlive() || victim.isRemoved() || victim.level() != level || now > w.until) {
                it.remove();
                continue;
            }
            Vec3 pos = victim.position();
            Vec3 velocity = w.last == null ? null : pos.subtract(w.last);
            Vec3 before = w.velocity;
            w.last = pos;
            w.velocity = velocity;
            if (before == null || velocity == null) continue;
            double speed = before.length();
            if (speed < minSpeed) continue;
            Vec3 dir = before.scale(1 / speed);
            if (velocity.dot(dir) > 0.4 * speed) continue; // still going that way
            if (!blocked(level, victim, dir)) continue; // slowed by something else (water, a cobweb)
            it.remove();
            BattleDamage.slam(level, contact(victim.getBoundingBox(), dir), speed, w.attacker, victim);
        }
    }

    /** Blocks right there, the way they were going. */
    static boolean blocked(ServerLevel level, Entity entity, Vec3 dir) {
        return !level.noCollision(entity, entity.getBoundingBox().move(dir.scale(0.3)));
    }

    /** Where the body met it: the edge of its box the way it was going, just past it. */
    static Vec3 contact(AABB box, Vec3 dir) {
        Vec3 half = new Vec3(box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2);
        double reach = Math.abs(dir.x) * half.x + Math.abs(dir.y) * half.y + Math.abs(dir.z) * half.z;
        return box.getCenter().add(dir.scale(reach + 0.05));
    }

    /** A tier a slam at this speed lands as (for tests and the report). */
    static ImpactTier tierAt(double speed) {
        return com.cnethuka.kegare.impact.ImpactStrength.tier(BattleDamageConfig.SLAM_STRENGTH_PER_SPEED.get() * speed);
    }

    static void clear() {
        WATCHED.clear();
    }
}
