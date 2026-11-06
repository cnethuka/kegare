package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;

/** What of a player's innate technique they can use. */
public final class TechniqueUnlocks {
    private TechniqueUnlocks() {}

    public static boolean isUnlocked(ServerPlayer player, Ability ability) {
        return true;
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
}
