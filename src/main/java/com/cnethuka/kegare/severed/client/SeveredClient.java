package com.cnethuka.kegare.severed.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Severed players' client side (DESIGN §3.27). */
public final class SeveredClient {
    private SeveredClient() {}

    public static void init(IEventBus modBus) {
        SensesClient.init();
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) m -> SensesClient.reload()));
    }
}
