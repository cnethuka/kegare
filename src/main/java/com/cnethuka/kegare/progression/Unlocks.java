package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The framework for shared and system unlocks, separate from {@link TechniqueUnlocks}: a registry later systems add to
 * with {@link #register}. Nothing real is registered yet: Maximum Output, Cradle, Threshold, Petalfall, Incomplete Dominion
 * and Dominion plug in when they're built, each with a {@code unlock.<namespace>.<path>} lang line for its toast.
 * <p>
 * Every player is re-evaluated whenever their Grade, Refinement or Insight changes, and on login. An unlock tied to its
 * technique is lost on a reroll and when Refinement falls below it; any other stays once earned.
 */
public final class Unlocks {
    /** Told when a player gains an unlock. */
    @FunctionalInterface
    public interface Listener {
        void onUnlocked(ServerPlayer player, ResourceLocation id);
    }

    private static final Map<ResourceLocation, UnlockRequirements> ENTRIES = new LinkedHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private Unlocks() {}

    public static void register(ResourceLocation id, UnlockRequirements requirements) {
        if (ENTRIES.putIfAbsent(id, requirements) != null) throw new IllegalArgumentException("Unlock registered twice: " + id);
    }

    /** For tests, whose entries mustn't outlive them. */
    public static void unregister(ResourceLocation id) {
        ENTRIES.remove(id);
    }

    public static Collection<ResourceLocation> registered() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    private static UnlockData data(Player player) {
        return player.getData(KegareAttachments.UNLOCKS);
    }

    public static boolean has(Player player, ResourceLocation id) {
        return data(player).unlocked.contains(id);
    }

    /** The Refinement this player needs for this unlock right now, with what their matching Insight is allowed to take off gone. */
    public static int refinementNeeded(Player player, UnlockRequirements requirements) {
        return requirements.effectiveRefinement(category -> Insight.get(player, category), ProgressionConfig.INSIGHT_REFINEMENT_RATE.get(), ProgressionConfig.INSIGHT_REFINEMENT_CAP.get());
    }

    private static boolean met(ServerPlayer player, UnlockRequirements requirements) {
        return requirements.met(player, GradeService.grade(player), RefinementService.level(player),
                category -> Insight.get(player, category), ProgressionConfig.INSIGHT_REFINEMENT_RATE.get(), ProgressionConfig.INSIGHT_REFINEMENT_CAP.get());
    }

    /** Grade, Refinement or Insight changed, or they just logged in. */
    public static void evaluate(ServerPlayer player) {
        UnlockData data = data(player);
        for (Map.Entry<ResourceLocation, UnlockRequirements> entry : ENTRIES.entrySet()) {
            boolean had = data.unlocked.contains(entry.getKey());
            if (had) {
                if (entry.getValue().tiedToTechnique() && !met(player, entry.getValue())) data.unlocked.remove(entry.getKey());
            } else if (!entry.getValue().grantedOnly() && met(player, entry.getValue())) {
                data.unlocked.add(entry.getKey());
                for (Listener listener : LISTENERS) listener.onUnlocked(player, entry.getKey());
            }
        }
    }

    /** The technique changed: what was tied to the old one goes. Runs after Refinement has been reset. */
    static void onTechniqueChanged(ServerPlayer player) {
        UnlockData data = data(player);
        for (Map.Entry<ResourceLocation, UnlockRequirements> entry : ENTRIES.entrySet()) {
            if (entry.getValue().tiedToTechnique()) data.unlocked.remove(entry.getKey());
        }
        evaluate(player);
    }

    /** Whether they meet an entry's requirements now (for a {@code grantedOnly} entry: whether they can be taught it). */
    public static boolean eligible(ServerPlayer player, ResourceLocation id) {
        UnlockRequirements requirements = ENTRIES.get(id);
        return requirements != null && met(player, requirements);
    }

    /**
     * Gives them an entry, for the system that teaches a {@code grantedOnly} one (or an admin), whether or not they meet it. Returns whether
     * it's new; listeners hear of it.
     */
    public static boolean grant(ServerPlayer player, ResourceLocation id) {
        if (!ENTRIES.containsKey(id) || !data(player).unlocked.add(id)) return false;
        for (Listener listener : LISTENERS) listener.onUnlocked(player, id);
        return true;
    }

    /** Admin: takes one away. Returns whether they had it. */
    public static boolean revoke(ServerPlayer player, ResourceLocation id) {
        return data(player).unlocked.remove(id);
    }

    /** Admin: all of it. */
    static void reset(ServerPlayer player) {
        data(player).unlocked.clear();
    }
}
