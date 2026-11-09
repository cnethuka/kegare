package com.cnethuka.kegare.backgroundtask;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Ticks every player's Background Tasks on the server and keeps their client told. */
public final class BackgroundTaskEvents {
    private BackgroundTaskEvents() {}

    public static void init() {
        BackgroundRmt.init();
        BackgroundReinforcement.init();
        SustainedFlow.init();
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.isAlive()) BackgroundTaskService.tick(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                BackgroundTaskSync.forget(p.getUUID());
                BackgroundTaskSync.sync(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            BackgroundTaskSync.forget(e.getEntity().getUUID());
            BackgroundRmt.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BackgroundTaskSync.clear());
    }
}
