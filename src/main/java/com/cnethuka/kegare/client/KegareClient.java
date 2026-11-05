package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.client.visual.VisualClientEvents;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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
        modBus.addListener(KegareClient::registerReloadListeners);
        VisualClientEvents.init(modBus);
        com.cnethuka.kegare.innate_technique.rime.client.RimeClient.init(modBus);
        com.cnethuka.kegare.rmt.client.RmtClient.init();
    }

    /** Emblem/icon art is looked up once per texture; forget the answers when resource packs change. */
    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> GuiDraw.clearArtCache());
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, MaliceHudLayer.ID, new MaliceHudLayer());
        // After hearts/armour/food/air so it can stack above whatever they used (Gui.leftHeight/rightHeight).
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, AbilityBarLayer.ID, new AbilityBarLayer());
    }
}
