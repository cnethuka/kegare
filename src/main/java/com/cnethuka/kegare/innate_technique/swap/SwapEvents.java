package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Swap's server hooks: Switchback's timer, and calling things off on death, logout and dimension change. */
@EventBusSubscriber(modid = Kegare.MODID)
public final class SwapEvents {
    private SwapEvents() {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Switchback.tick(event.getServer());
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) endAll(player);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        Switchback.clear();
        SwapDebug.clear();
    }

    /** Everything Swap has running that involves this player ends. */
    private static void endAll(ServerPlayer player) {
        Switchback.cancelInvolving(player);
    }
}
