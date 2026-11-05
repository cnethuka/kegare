package com.cnethuka.kegare.innate_technique.rime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime-only per caster (attachment {@code kegare:rime_tracker}): recent freezes per victim (Deep Freeze), which
 * targets each recent Splinter cast hit (all three shards landing), and Ice Path's last position (distance).
 */
public final class RimeTracker {
    /** Splinter casts remembered (their shards fly for about a second, so a few is plenty). */
    private static final int SPLINTER_CASTS_KEPT = 8;

    final Map<UUID, Deque<Long>> freezes = new HashMap<>();
    private int splinterCasts;
    private final Map<Integer, Map<UUID, Integer>> splinterHits = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Map<UUID, Integer>> eldest) {
            return size() > SPLINTER_CASTS_KEPT;
        }
    };
    @Nullable
    Vec3 icePathLast;

    /** Records a freeze and returns how many of this victim's freezes fall inside the window. */
    int recordFreeze(UUID victim, long now, long window) {
        Deque<Long> times = freezes.computeIfAbsent(victim, v -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > window) times.removeFirst();
        return times.size();
    }

    int nextSplinterCast() {
        return ++splinterCasts;
    }

    int splinterHits(int cast, UUID victim) {
        Map<UUID, Integer> hits = splinterHits.get(cast);
        return hits == null ? 0 : hits.getOrDefault(victim, 0);
    }

    /** Returns how many shards of this cast have now hit this victim. */
    int recordSplinterHit(int cast, UUID victim) {
        return splinterHits.computeIfAbsent(cast, c -> new HashMap<>()).merge(victim, 1, Integer::sum);
    }
}
