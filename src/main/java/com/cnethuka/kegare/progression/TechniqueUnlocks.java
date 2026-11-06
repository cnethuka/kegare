package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.network.PlayerSync;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What of their innate technique a player can use: its abilities, specials and ultimate unlock through Refinement, at
 * the levels its definition sets (config can override them). Shared abilities aren't gated here and keep their own rules.
 * Insight never changes these levels. Separate from {@link Unlocks}, which is for shared and system unlocks.
 */
public final class TechniqueUnlocks {
    @Nullable
    private static Boolean lastRequired;

    private TechniqueUnlocks() {}

    public static boolean required() {
        return ProgressionConfig.REQUIRE_REFINEMENT.get();
    }

    /** The level this part of the technique unlocks at. */
    public static int requiredLevel(InnateTechnique technique, Ability ability) {
        OptionalInt override = ProgressionConfig.unlockOverride(ability.id());
        int level = override.isPresent() && !technique.isTestContent() ? override.getAsInt() : technique.unlockLevel(ability);
        return Math.min(level, RefinementService.maxLevel()); // a server with a lower top level mustn't lock something forever
    }

    /** The level this ability of the player's current technique unlocks at; 0 for anything else. */
    public static int requiredLevel(ServerPlayer player, Ability ability) {
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        if (technique.isEmpty() || SharedAbilities.find(ability.id()).isPresent() || technique.get().findAbility(ability.id()).isEmpty()) return 0;
        return requiredLevel(technique.get(), ability);
    }

    public static boolean isUnlocked(ServerPlayer player, Ability ability) {
        return !required() || RefinementService.level(player) >= requiredLevel(player, ability);
    }

    /** The slot abilities and ultimate they have unlocked, by ID. */
    public static Set<String> unlockedBase(ServerPlayer player, InnateTechnique technique) {
        Set<String> out = new HashSet<>();
        for (Ability a : technique.everyAbility()) {
            if (!a.isSpecial() && isUnlocked(player, a)) out.add(a.id().toString());
        }
        return out;
    }

    /** The specials they have unlocked, by ID. */
    public static Set<String> unlockedSpecials(ServerPlayer player, InnateTechnique technique) {
        Set<String> out = new HashSet<>();
        for (Ability a : technique.everyAbility()) {
            if (a.isSpecial() && isUnlocked(player, a)) out.add(a.id().toString());
        }
        return out;
    }

    /**
     * What a fight on this technique is scored against: its kit at their Peak Refinement level, or what they have unlocked if
     * that's more (the requirement can be off). Peak survives a reroll, so a small new kit can't make two moves look like variety.
     * Someone who has never been higher than they are now is scored against exactly what they have.
     */
    public static AssessmentScoring.Kit scoringKit(ServerPlayer player, InnateTechnique technique) {
        int peak = RefinementService.peak(player);
        int base = 0;
        int specials = 0;
        for (Ability a : technique.everyAbility()) {
            if (requiredLevel(technique, a) > peak) continue;
            if (a.isSpecial()) specials++;
            else base++;
        }
        return new AssessmentScoring.Kit(Math.max(base, unlockedBase(player, technique).size()), Math.max(specials, unlockedSpecials(player, technique).size()),
                required() && RefinementService.level(player) < peak);
    }

    /** What is still locked, with the level each one needs, in the technique's own order. Sent to the owner for the screens. */
    public static Map<Ability, Integer> locked(ServerPlayer player) {
        Map<Ability, Integer> out = new LinkedHashMap<>();
        InnateTechniqueService.current(player).ifPresent(t -> {
            for (Ability a : t.everyAbility()) if (!isUnlocked(player, a)) out.put(a, requiredLevel(t, a));
        });
        return out;
    }

    /** Refinement moved: what it just unlocked goes on the bar with a toast; what it just locked comes off. */
    static void onLevelChanged(ServerPlayer player, int from, int to) {
        if (!required()) return;
        if (to < from) {
            LoadoutService.removeLocked(player);
            return;
        }
        InnateTechnique technique = InnateTechniqueService.current(player).orElse(null);
        if (technique == null) return;
        for (Ability a : technique.everyAbility()) {
            int needed = requiredLevel(technique, a);
            if (needed <= from || needed > to) continue;
            if (!a.isSpecial() && technique.abilities().contains(a)) LoadoutService.offer(player, a);
            ProgressionToasts.abilityUnlocked(player, a);
        }
    }

    /**
     * Full Kit: their Refinement has reached every ability and special of a real technique. The ultimate doesn't count, since
     * two techniques can't have one until Dominions exist. Judged by the level itself, so casual servers with the requirement
     * off have to earn it the same way.
     */
    static void checkFullKit(ServerPlayer player) {
        InnateTechnique technique = InnateTechniqueService.current(player).orElse(null);
        if (technique == null || technique.isTestContent()) return;
        int level = RefinementService.level(player);
        for (Ability a : technique.abilities()) {
            if (level < requiredLevel(technique, a)) return;
            if (a.special().isPresent() && level < requiredLevel(technique, a.special().get())) return;
        }
        KegareAdvancements.award(player, KegareAdvancements.FULL_KIT);
    }

    /** The config can change with players online: bring every bar into line when requireRefinementForAbilities flips. */
    static void watchConfig(MinecraftServer server) {
        boolean now = required();
        if (lastRequired != null && lastRequired != now) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                LoadoutService.validate(player);
                if (now) LoadoutService.removeLocked(player);
                PlayerSync.markDirty(player);
            }
        }
        lastRequired = now;
    }
}
