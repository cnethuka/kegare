package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.innate_technique.time_manipulation.TimeRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Time Manipulation's client entry point (DESIGN §3.26). */
public final class TimeClient {
    private TimeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(TimeClient::registerRenderers);
        TimeStopClient.init();
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TimeRegistry.DECELERATE.get(), NoopRenderer::new);
    }
}
