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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Switchback's pending swaps back (runtime only). Each one belongs to its caster; the target is a player or a mob.
 * The swap back fires {@link SwapTargetEvent} again. It is called off if either dies, logs out (or the mob is gone) or
 * changes dimension. Both wear the countdown ring until then.
 */
public final class Switchback {
    /** The target is held by reference: a mob can't be looked up by name, and a player who relogs is a new entity. */
    private record Pending(LivingEntity target, long dueTick, ResourceKey<Level> dimension) {}

    /** By caster. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private Switchback() {}

    static void schedule(ServerPlayer caster, LivingEntity target) {
        cancel(caster.server, caster.getUUID());
        long due = caster.serverLevel().getGameTime() + SwapConfig.ticks(SwapConfig.SWITCHBACK_DELAY);
        PENDING.put(caster.getUUID(), new Pending(target, due, caster.level().dimension()));
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
            LivingEntity target = pending.target();
            stopTimers(casterId, caster, target);
            if (caster == null || !usable(caster, pending) || !usable(target, pending)) return;
            if (!SwapTargeting.allowed(caster, target, Swap.SWITCHBACK, Component.translatable("message.kegare.swap.switchback_refused"))) return;
            Swapper.swap(caster, target, true);
            SwapTracker.swapped(caster, target);
        });
    }

    private static boolean usable(LivingEntity entity, Pending pending) {
        if (entity instanceof ServerPlayer player && player.hasDisconnected()) return false;
        return entity.isAlive() && !entity.isRemoved() && entity.level().dimension() == pending.dimension();
    }

    /** Calls off every swap back this player or mob is part of (death, logout, dimension change). */
    static void cancelInvolving(LivingEntity entity) {
        if (PENDING.isEmpty() || entity.level().isClientSide) return;
        UUID id = entity.getUUID();
        MinecraftServer server = entity.getServer();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            if (e.getKey().equals(id) || e.getValue().target().getUUID().equals(id)) {
                it.remove();
                stopTimers(e.getKey(), server == null ? null : find(server, e.getKey()), e.getValue().target());
            }
        }
    }

    private static void cancel(MinecraftServer server, UUID casterId) {
        Pending old = PENDING.remove(casterId);
        if (old != null) stopTimers(casterId, find(server, casterId), old.target());
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

    private static void stopTimers(UUID casterId, @Nullable ServerPlayer caster, @Nullable LivingEntity target) {
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
