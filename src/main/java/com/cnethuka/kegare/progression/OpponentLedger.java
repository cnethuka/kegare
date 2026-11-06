package com.cnethuka.kegare.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Per-opponent history within a rolling window: how many times this player met that target lately. Grade counts fights
 * in one and Refinement counts hits in another, so both use the same diminishing returns on the same kind of record.
 * Saved, so logging out doesn't reset it. Times are overworld game ticks.
 */
public final class OpponentLedger {
    private record Entry(UUID id, List<Long> times) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                Codec.LONG.listOf().fieldOf("times").forGetter(Entry::times)
        ).apply(i, Entry::new));
    }

    public static final Codec<OpponentLedger> CODEC = Entry.CODEC.listOf().xmap(entries -> {
        OpponentLedger ledger = new OpponentLedger();
        for (Entry e : entries) ledger.events.put(e.id, new ArrayDeque<>(e.times));
        return ledger;
    }, ledger -> {
        List<Entry> out = new ArrayList<>();
        ledger.events.forEach((id, times) -> out.add(new Entry(id, List.copyOf(times))));
        return out;
    });

    /** Oldest target forgotten beyond this, newest times kept per target: past them returns have long since bottomed out. */
    private static final int MAX_TARGETS = 64;
    private static final int MAX_TIMES = 64;

    private final Map<UUID, Deque<Long>> events = new LinkedHashMap<>();

    /** How many events against {@code id} fall inside the window ending at {@code now}. */
    public int count(UUID id, long now, long window) {
        Deque<Long> times = events.get(id);
        if (times == null) return 0;
        prune(times, now, window);
        return times.size();
    }

    public void record(UUID id, long now, long window) {
        Deque<Long> times = events.remove(id); // re-inserted last: the map runs oldest target first
        if (times == null) times = new ArrayDeque<>();
        prune(times, now, window);
        times.addLast(now);
        while (times.size() > MAX_TIMES) times.removeFirst();
        events.put(id, times);
        pruneTargets(now, window);
    }

    private void pruneTargets(long now, long window) {
        events.values().removeIf(times -> {
            prune(times, now, window);
            return times.isEmpty();
        });
        var it = events.keySet().iterator();
        while (events.size() > MAX_TARGETS && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private static void prune(Deque<Long> times, long now, long window) {
        while (!times.isEmpty() && now - times.peekFirst() >= window) times.removeFirst();
    }

    public void clear() {
        events.clear();
    }
}
