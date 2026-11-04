package com.cnethuka.kegare.innate_technique.rime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime-only per caster (attachment {@code kegare:rime_tracker}): recent freezes per victim, for Deep Freeze. */
public final class RimeTracker {
    final Map<UUID, Deque<Long>> freezes = new HashMap<>();

    /** Records a freeze and returns how many of this victim's freezes fall inside the window. */
    int recordFreeze(UUID victim, long now, long window) {
        Deque<Long> times = freezes.computeIfAbsent(victim, v -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > window) times.removeFirst();
        return times.size();
    }
}
