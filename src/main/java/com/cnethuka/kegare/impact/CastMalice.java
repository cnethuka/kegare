package com.cnethuka.kegare.impact;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.progression.Landings;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Malice behind a cast, for the Impact System's fourth input (DESIGN §3.23): what each player last paid for each ability, at its
 * base cost ({@link Landings#baseCost}), so the surroundings and any discount never make a hit feel lighter. Recorded when the cast
 * is paid ({@code CastManager.pay}): a hit can land long after (a lance in flight, a spike that erupts later) and the cost can
 * change in between (Shatter Shell costs less with the shell up, and the shell is gone by the time it hits). Runtime only.
 */
public final class CastMalice {
    private static final Map<UUID, Map<ResourceLocation, Double>> PAID = new HashMap<>();

    private CastMalice() {}

    /** {@code player} paid {@code amount} for a cast of {@code ability}. */
    public static void paid(ServerPlayer player, Ability ability, double amount) {
        double multiplier = InnateTechniqueService.current(player).map(t -> (double) t.environment(player).multiplier()).orElse(1.0);
        PAID.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(ability.id(), Landings.baseCost(amount, multiplier));
    }

    /** The base Malice behind {@code player}'s last cast of {@code ability}: what it would cost now, if they never paid for one. */
    public static double behind(ServerPlayer player, Ability ability) {
        Map<ResourceLocation, Double> own = PAID.get(player.getUUID());
        Double paid = own == null ? null : own.get(ability.id());
        if (paid != null) return paid;
        double multiplier = InnateTechniqueService.current(player).map(t -> (double) t.environment(player).multiplier()).orElse(1.0);
        return Landings.baseCost(ability.cost(player), multiplier);
    }

    public static void forget(UUID player) {
        PAID.remove(player);
    }

    public static void clear() {
        PAID.clear();
    }
}
