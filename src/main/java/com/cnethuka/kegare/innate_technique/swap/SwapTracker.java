package com.cnethuka.kegare.innate_technique.swap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Runtime bookkeeping for Swap's advancements (nothing here is saved). */
public final class SwapTracker {
    /** Identity Crisis: caster -> the tick they cast Total Swap below 30% health. */
    private static final Map<UUID, Long> IDENTITY_CRISIS = new HashMap<>();

    private SwapTracker() {}

    static void identityCrisisStarted(ServerPlayer caster) {
        IDENTITY_CRISIS.put(caster.getUUID(), caster.serverLevel().getGameTime());
    }

    /** Every server tick: whoever made it 30 seconds since earns Identity Crisis. */
    static void tick(MinecraftServer server) {
        if (IDENTITY_CRISIS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, Long>> it = IDENTITY_CRISIS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            if (now - e.getValue() < SwapAdvancements.IDENTITY_CRISIS_SURVIVE_TICKS) continue;
            it.remove();
            ServerPlayer player = Switchback.find(server, e.getKey());
            if (player != null && player.isAlive()) SwapAdvancements.award(player, SwapAdvancements.IDENTITY_CRISIS);
        }
    }

    /** Death (or logout) ends a pending Identity Crisis without the award. */
    static void forget(ServerPlayer player) {
        IDENTITY_CRISIS.remove(player.getUUID());
    }

    /** Test hook: whether this player is on the clock for Identity Crisis. */
    static boolean identityCrisisPending(ServerPlayer player) {
        return IDENTITY_CRISIS.containsKey(player.getUUID());
    }

    static void clear() {
        IDENTITY_CRISIS.clear();
    }
}
