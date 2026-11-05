package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Switchback's pending swaps back (runtime only). Each one belongs to its caster; the swap back fires
 * {@link SwapTargetEvent} again. It is called off if either player dies, logs out or changes dimension. Both players
 * wear the countdown ring until then.
 */
public final class Switchback {
    private record Pending(UUID target, long dueTick, ResourceKey<Level> dimension) {}

    /** By caster. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private Switchback() {}

    static void schedule(ServerPlayer caster, ServerPlayer target) {
        cancel(caster.server, caster.getUUID());
        long due = caster.serverLevel().getGameTime() + SwapConfig.ticks(SwapConfig.SWITCHBACK_DELAY);
        PENDING.put(caster.getUUID(), new Pending(target.getUUID(), due, caster.level().dimension()));
        String key = timerKey(caster.getUUID());
        VisualService.startLoop(caster, key, SwapIds.SWITCHBACK_TIMER, 0);
        VisualService.startLoop(target, key, SwapIds.SWITCHBACK_TIMER, 0);
    }

    /** The caster is waiting to be swapped back (Round Trip). */
    public static boolean isPending(ServerPlayer caster) {
        return PENDING.containsKey(caster.getUUID());
    }

    /** Every server tick: swap back whoever is due. */
    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.overworld().getGameTime();
        Map<UUID, Pending> due = new HashMap<>();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            if (now >= e.getValue().dueTick()) {
                due.put(e.getKey(), e.getValue());
                it.remove();
            }
        }
        due.forEach((casterId, pending) -> {
            ServerPlayer caster = find(server, casterId);
            ServerPlayer target = find(server, pending.target());
            stopTimers(casterId, caster, target);
            if (!usable(caster, pending) || !usable(target, pending)) return;
            if (!SwapTargeting.allowed(caster, target, Swap.SWITCHBACK, Component.translatable("message.kegare.swap.switchback_refused"))) return;
            Swapper.swapPlayers(caster, target, true);
            SwapTracker.swapped(caster, target);
        });
    }

    private static boolean usable(@Nullable ServerPlayer player, Pending pending) {
        return player != null && player.isAlive() && !player.hasDisconnected() && player.level().dimension() == pending.dimension();
    }

    /** Calls off every swap back this player is part of (death, logout, dimension change). */
    static void cancelInvolving(ServerPlayer player) {
        UUID id = player.getUUID();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            if (e.getKey().equals(id) || e.getValue().target().equals(id)) {
                it.remove();
                stopTimers(e.getKey(), find(player.server, e.getKey()), find(player.server, e.getValue().target()));
            }
        }
    }

    private static void cancel(MinecraftServer server, UUID casterId) {
        Pending old = PENDING.remove(casterId);
        if (old != null) stopTimers(casterId, find(server, casterId), find(server, old.target()));
    }

    /** An online player by UUID (players in a level but not the player list, like test fake players, count too). */
    @Nullable
    static ServerPlayer find(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getPlayerByUUID(id) instanceof ServerPlayer found) return found;
        }
        return null;
    }

    private static void stopTimers(UUID casterId, @Nullable ServerPlayer caster, @Nullable ServerPlayer target) {
        String key = timerKey(casterId);
        if (caster != null) VisualService.stopLoop(caster, key);
        if (target != null) VisualService.stopLoop(target, key);
    }

    private static String timerKey(UUID casterId) {
        return SwapIds.SWITCHBACK_TIMER_KEY + casterId;
    }

    static void clear() {
        PENDING.clear();
    }
}
