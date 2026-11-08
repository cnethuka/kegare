package com.cnethuka.kegare.innate_technique.time_manipulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where things were, for Rewind (DESIGN §3.26): the last few seconds of every player's position, tick by tick, and of every mob within
 * {@code historyMobRange} of anyone holding Time Manipulation (a mob outside that range isn't followed, so it has no history to
 * rewind). A gap in a mob's record (out of range a moment) or a change of dimension breaks it: Rewind needs every tick from then to
 * now in one level. Runtime only.
 */
public final class PositionHistory {
    record Sample(ResourceKey<Level> level, Vec3 pos, long tick) {}

    private static final Map<LivingEntity, ArrayDeque<Sample>> HISTORY = new WeakHashMap<>();

    private PositionHistory() {}

    /** How many ticks are kept: what Rewind reaches back, and a little more. */
    static int kept() {
        return TimeConfig.ticks(TimeConfig.REWIND_SECONDS) + 2;
    }

    /** Every server tick, after the entities have moved. */
    static void tick(MinecraftServer server) {
        int keep = kept();
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            List<ServerPlayer> holders = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) continue;
                record(p, level, now, keep);
                if (TimeManipulation.holds(p)) holders.add(p);
            }
            double range = TimeConfig.HISTORY_MOB_RANGE.get();
            if (holders.isEmpty() || range <= 0) continue;
            java.util.Set<LivingEntity> near = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (ServerPlayer holder : holders) {
                near.addAll(level.getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(range),
                        e -> !(e instanceof Player) && !(e instanceof ArmorStand) && e.isAlive() && e.distanceToSqr(holder) <= range * range));
            }
            for (LivingEntity mob : near) record(mob, level, now, keep);
        }
        if (server.getTickCount() % 100 == 0) {
            for (Iterator<Map.Entry<LivingEntity, ArrayDeque<Sample>>> it = HISTORY.entrySet().iterator(); it.hasNext(); ) {
                LivingEntity e = it.next().getKey();
                if (e == null || e.isRemoved()) it.remove();
            }
        }
    }

    private static void record(LivingEntity entity, ServerLevel level, long now, int keep) {
        ArrayDeque<Sample> samples = HISTORY.computeIfAbsent(entity, k -> new ArrayDeque<>());
        Sample last = samples.peekLast();
        if (last != null && last.tick() == now) return;
        samples.addLast(new Sample(level.dimension(), entity.position(), now));
        while (samples.size() > keep) samples.pollFirst();
    }

    /**
     * The path from {@code ticks} ago to now (oldest first), if there's an unbroken record of it in the entity's current level; empty
     * if not (it hasn't been followed that long, it changed dimension, or it was out of range a moment).
     */
    static Optional<List<Vec3>> path(LivingEntity entity, int ticks) {
        ArrayDeque<Sample> samples = HISTORY.get(entity);
        if (samples == null || samples.isEmpty()) return Optional.empty();
        long now = entity.level().getGameTime();
        ResourceKey<Level> here = entity.level().dimension();
        List<Vec3> out = new ArrayList<>();
        long expect = -1;
        boolean found = false;
        for (Sample s : samples) {
            if (s.tick() < now - ticks) continue;
            if (!found) {
                if (s.tick() > now - ticks + 1) return Optional.empty(); // the record doesn't reach back that far
                found = true;
            } else if (s.tick() != expect) {
                return Optional.empty(); // a gap
            }
            if (!s.level().equals(here)) return Optional.empty();
            out.add(s.pos());
            expect = s.tick() + 1;
        }
        return found && expect >= now - 1 ? Optional.of(out) : Optional.empty();
    }

    /** Where the entity was {@code ticks} ago, by {@link #path}. */
    static Optional<Vec3> then(LivingEntity entity, int ticks) {
        return path(entity, ticks).map(p -> p.get(0));
    }

    static void forget(LivingEntity entity) {
        HISTORY.remove(entity);
    }

    static void clear() {
        HISTORY.clear();
    }
}
