package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.network.VisualPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Wires the client visual system: payload handler, PAL layer, Photon preloading, per-tick work and cleanup. */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class VisualClientEvents {
    private VisualClientEvents() {}

    /** Called from the client mod constructor. */
    public static void init(IEventBus modBus) {
        VisualPayload.setClientHandler(ClientVisuals::handle);
        VisualHooks.setTrailStarter(ClientVisuals::startTrail);
        modBus.addListener(VisualClientEvents::clientSetup);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PlayerAnimations.registerLayer(); // PAL: must be enqueued work on NeoForge
            PhotonEffects.preload(VisualHooks.allEffects().stream().filter(e -> !ClientVisuals.isDrawnByMove(e)).toList());
        });
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientVisuals.tick();
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientVisuals.clear();
    }
}
