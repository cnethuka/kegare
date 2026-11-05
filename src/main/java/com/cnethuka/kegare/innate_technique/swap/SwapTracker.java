package com.cnethuka.kegare.innate_technique.swap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Runtime bookkeeping for Swap's advancements (nothing here is saved). */
public final class SwapTracker {
    /** Who last moved a player with a swap, and when (Fall Guy). */
    private record Mark(UUID caster, long tick) {}

    /** Identity Crisis: caster -> the tick they cast Total Swap below 30% health. */
    private static final Map<UUID, Long> IDENTITY_CRISIS = new HashMap<>();
    /** Fall Guy: swapped player or mob -> who swapped them, when. */
    private static final Map<UUID, Mark> SWAPPED = new HashMap<>();
    /** Newton's Third: Momentum Exchange target -> who exchanged with them, when. */
    private static final Map<UUID, Mark> MOMENTUM = new HashMap<>();

    /** The caster moved {@code target} (a player or a mob) with a swap (Positions, Switchback either way, Send Off, Total Swap). */
    static void swapped(ServerPlayer caster, LivingEntity target) {
        SWAPPED.put(target.getUUID(), new Mark(caster.getUUID(), caster.serverLevel().getGameTime()));
    }

    /** The caster traded momentum with {@code target} (a player or a mob). */
    static void momentum(ServerPlayer caster, LivingEntity target) {
        MOMENTUM.put(target.getUUID(), new Mark(caster.getUUID(), caster.serverLevel().getGameTime()));
    }

    /** A player or mob died from falling: whoever swapped it (5 s) or traded momentum with it (10 s) may earn something. */
    static void fellToDeath(LivingEntity victim) {
        MinecraftServer server = victim.getServer();
        if (server == null) return;
        long now = victim.level().getGameTime();
        Mark swap = SWAPPED.remove(victim.getUUID());
        if (swap != null && now - swap.tick() <= SwapAdvancements.FALL_GUY_WINDOW_TICKS) {
            ServerPlayer caster = Switchback.find(server, swap.caster());
            if (caster != null && caster != victim) SwapAdvancements.award(caster, SwapAdvancements.FALL_GUY);
        }
        Mark push = MOMENTUM.remove(victim.getUUID());
        if (push != null && now - push.tick() <= SwapAdvancements.NEWTONS_THIRD_WINDOW_TICKS) {
            ServerPlayer caster = Switchback.find(server, push.caster());
            if (caster != null && caster != victim) SwapAdvancements.award(caster, SwapAdvancements.NEWTONS_THIRD);
        }
    }

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

    /** Death (after the fall checks) or logout: the marks on it are gone, and a pending Identity Crisis fails. */
    static void forget(LivingEntity player) {
        IDENTITY_CRISIS.remove(player.getUUID());
        SWAPPED.remove(player.getUUID());
        MOMENTUM.remove(player.getUUID());
    }

    /** Test hook: whether this player is on the clock for Identity Crisis. */
    static boolean identityCrisisPending(ServerPlayer player) {
        return IDENTITY_CRISIS.containsKey(player.getUUID());
    }

    static void clear() {
        IDENTITY_CRISIS.clear();
        SWAPPED.clear();
        MOMENTUM.clear();
    }
}
