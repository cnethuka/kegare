package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client-only entry point. Never loaded on a dedicated server. */
@Mod(value = Kegare.MODID, dist = Dist.CLIENT)
public final class KegareClient {
    public KegareClient(IEventBus modBus, ModContainer container) {
        // Mods list > Kegare > Config edits both client and (in singleplayer) server configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(KegareKeys::register);
        modBus.addListener(KegareClient::registerGuiLayers);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, CeHudLayer.ID, new CeHudLayer());
    }
}
