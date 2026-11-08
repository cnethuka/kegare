package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.network.PlayerSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * How fast a player's ability cooldowns run (DESIGN §3.26): 1 is normal, 1.5 half again as fast (Accelerate), 0.5 half speed (a
 * Decelerate hit). Each {@link Rate} is a speed, which cooldowns it touches and until when (or until it's removed by its key). Every
 * server tick each cooldown's end comes (the product of the rates that touch it, minus 1) ticks sooner, the fractions carried over, so
 * 1.5 takes half a tick off each tick and 0.5 adds half a tick back. Runtime only: a rate ends with logout.
 */
public final class CooldownRates {
    /**
     * @param key     what set it, so the same thing can replace or remove it (one per key)
     * @param touches which cooldowns (by cooldown key) it speeds or slows
     * @param until   game time it ends at, or {@link Long#MAX_VALUE} until removed
     */
    public record Rate(String key, double multiplier, Predicate<ResourceLocation> touches, long until) {}

    private static final Map<UUID, List<Rate>> RATES = new HashMap<>();
    /** Fractions of a tick carried over, per player and cooldown. */
    private static final Map<UUID, Map<ResourceLocation, Double>> CARRY = new HashMap<>();
    /** Resend the sync this often while cooldowns are being moved (the HUD shows whole seconds). */
    private static final int SYNC_EVERY = 5;

    private CooldownRates() {}

    /** Sets a rate on the player (replacing any with the same key). */
    public static void set(ServerPlayer player, Rate rate) {
        List<Rate> rates = RATES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        rates.removeIf(r -> r.key().equals(rate.key()));
        rates.add(rate);
    }

    public static void remove(ServerPlayer player, String key) {
        List<Rate> rates = RATES.get(player.getUUID());
        if (rates == null) return;
        rates.removeIf(r -> r.key().equals(key));
        if (rates.isEmpty()) end(player);
    }

    /** The product of the rates touching {@code cooldownKey} right now. */
    public static double multiplier(ServerPlayer player, ResourceLocation cooldownKey) {
        List<Rate> rates = RATES.get(player.getUUID());
        if (rates == null) return 1.0;
        long now = player.level().getGameTime();
        double m = 1.0;
        for (Rate r : rates) if (now < r.until() && r.touches().test(cooldownKey)) m *= r.multiplier();
        return m;
    }

    /** Every server tick for every online player. */
    public static void tick(ServerPlayer player) {
        List<Rate> rates = RATES.get(player.getUUID());
        if (rates == null) return;
        long now = player.level().getGameTime();
        rates.removeIf(r -> now >= r.until());
        if (rates.isEmpty()) {
            end(player);
            return;
        }
        InnateTechniqueData data = InnateTechniqueService.get(player);
        Map<ResourceLocation, Double> carry = CARRY.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        boolean moved = false;
        for (Map.Entry<ResourceLocation, InnateTechniqueData.Cooldown> e : new ArrayList<>(data.cooldowns().entrySet())) {
            if (e.getValue().endTick() <= now) continue;
            double m = multiplier(player, e.getKey());
            if (m == 1.0) continue;
            // A tick passes as m ticks of it: the end comes (m - 1) sooner (1.5: half a tick sooner; 0.5: half a tick later).
            double shift = carry.getOrDefault(e.getKey(), 0.0) + (m - 1.0);
            long whole = (long) shift;
            carry.put(e.getKey(), shift - whole);
            if (whole != 0) {
                InnateTechniqueService.shiftCooldown(player, e.getKey(), -whole, false);
                moved = true;
            }
        }
        for (Iterator<ResourceLocation> it = carry.keySet().iterator(); it.hasNext(); ) {
            InnateTechniqueData.Cooldown c = data.cooldowns().get(it.next());
            if (c == null || c.endTick() <= now) it.remove();
        }
        if (moved && now % SYNC_EVERY == 0) PlayerSync.markDirty(player);
    }

    private static void end(ServerPlayer player) {
        RATES.remove(player.getUUID());
        CARRY.remove(player.getUUID());
        PlayerSync.markDirty(player);
    }

    public static void forget(UUID player) {
        RATES.remove(player);
        CARRY.remove(player);
    }

    public static void clear() {
        RATES.clear();
        CARRY.clear();
    }
}
