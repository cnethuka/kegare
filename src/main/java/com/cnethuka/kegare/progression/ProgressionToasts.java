package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import net.minecraft.server.level.ServerPlayer;

/** Small private toasts. Only their owner ever gets one: Grade and unlocks are nobody else's business. */
public final class ProgressionToasts {
    private ProgressionToasts() {}

    static void abilityUnlocked(ServerPlayer player, Ability ability) {}
}
