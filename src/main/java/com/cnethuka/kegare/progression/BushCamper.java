package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * "Bush Camper" (Base tab, task, hidden, announced): steal another person's kill (DESIGN §3.11). You land the killing blow on a player, but
 * another player dealt at least {@code stealShare} (75%) of the damage the victim took in the last {@code stealWindow} (30 s), and you dealt
 * the rest or less (25%). Fed from the damage record (§3.21): every hit on a player, by whom. Anyone can earn it, Severed included; mobs
 * don't count, and it must be your own killing blow.
 */
public final class BushCamper {
    private record Hit(@Nullable UUID attacker, double amount, long tick) {}

    private static final Map<UUID, Deque<Hit>> TAKEN = new HashMap<>();

    private BushCamper() {}

    /** A hit a player took: from another player ({@code attacker}), or from anything else (null). */
    static void took(ServerPlayer victim, @Nullable ServerPlayer attacker, double amount, long now) {
        Deque<Hit> hits = TAKEN.computeIfAbsent(victim.getUUID(), k -> new ArrayDeque<>());
        hits.addLast(new Hit(attacker == null ? null : attacker.getUUID(), amount, now));
        long window = ProgressionConfig.ticks(ProgressionConfig.STEAL_WINDOW);
        while (!hits.isEmpty() && now - hits.peekFirst().tick() > window) hits.pollFirst();
    }

    /** A player died. {@code killer}: who landed the killing blow, if a player did. */
    static void died(ServerPlayer victim, @Nullable ServerPlayer killer, long now) {
        Deque<Hit> hits = TAKEN.remove(victim.getUUID());
        if (killer != null && killer != victim && hits != null && stolen(hits, killer.getUUID(), victim.getUUID(), now)) {
            KegareAdvancements.award(killer, KegareAdvancements.BUSH_CAMPER);
        }
    }

    private static boolean stolen(Deque<Hit> hits, UUID killer, UUID victim, long now) {
        long window = ProgressionConfig.ticks(ProgressionConfig.STEAL_WINDOW);
        double total = 0;
        Map<UUID, Double> byPlayer = new HashMap<>();
        for (Hit hit : hits) {
            if (now - hit.tick() > window) continue;
            total += hit.amount();
            if (hit.attacker() != null) byPlayer.merge(hit.attacker(), hit.amount(), Double::sum);
        }
        if (total <= 0) return false;
        double share = ProgressionConfig.STEAL_SHARE.get();
        if (byPlayer.getOrDefault(killer, 0.0) / total > 1.0 - share + 1.0e-9) return false;
        for (Map.Entry<UUID, Double> e : byPlayer.entrySet()) {
            if (!e.getKey().equals(killer) && !e.getKey().equals(victim) && e.getValue() / total >= share - 1.0e-9) return true;
        }
        return false;
    }

    static void forget(UUID player) {
        TAKEN.remove(player);
    }

    public static void clear() {
        TAKEN.clear();
    }
}
