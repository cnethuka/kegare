package com.cnethuka.kegare.database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What's waiting to be written (DESIGN §3.33), by player: the newest value per attachment wins, so an outage costs memory for one copy of each
 * player's data at most, however long it lasts. A null value means the row goes.
 */
final class SaveQueue {
    /** One player's changes. */
    static final class Batch {
        final Map<String, String> changes = new LinkedHashMap<>();
        boolean release;
        /** The lease this was made under: a write under any other does nothing. */
        UUID token;

        boolean isEmpty() {
            return changes.isEmpty() && !release;
        }
    }

    private final Map<UUID, Batch> waiting = new HashMap<>();

    synchronized void offer(UUID player, UUID token, Map<String, String> changes, boolean release) {
        Batch batch = waiting.computeIfAbsent(player, p -> new Batch());
        batch.token = token;
        batch.changes.putAll(changes);
        batch.release |= release;
        if (batch.isEmpty()) waiting.remove(player);
        notifyAll();
    }

    /** Takes one player's batch to write, or null if nothing's waiting. */
    synchronized Map.Entry<UUID, Batch> take() {
        var iterator = waiting.entrySet().iterator();
        if (!iterator.hasNext()) return null;
        Map.Entry<UUID, Batch> entry = iterator.next();
        iterator.remove();
        return Map.entry(entry.getKey(), entry.getValue());
    }

    /** A write failed: back it goes, behind anything newer that's arrived since. */
    synchronized void putBack(UUID player, Batch failed) {
        Batch newer = waiting.get(player);
        if (newer != null) {
            failed.changes.putAll(newer.changes);
            failed.release |= newer.release;
            failed.token = newer.token;
        }
        waiting.put(player, failed);
        notifyAll();
    }

    synchronized void wake() {
        notifyAll();
    }

    synchronized void awaitWork(long millis) throws InterruptedException {
        if (waiting.isEmpty()) wait(millis);
    }

    synchronized boolean has(UUID player) {
        return waiting.containsKey(player);
    }

    /** Players with something waiting. */
    synchronized int size() {
        return waiting.size();
    }

    /** Everything waiting, copied (for the spill file). */
    synchronized Map<UUID, Batch> copy() {
        Map<UUID, Batch> out = new HashMap<>();
        waiting.forEach((player, batch) -> {
            Batch copy = new Batch();
            copy.changes.putAll(batch.changes);
            copy.release = batch.release;
            copy.token = batch.token;
            out.put(player, copy);
        });
        return out;
    }

    synchronized List<UUID> players() {
        return new ArrayList<>(waiting.keySet());
    }
}
