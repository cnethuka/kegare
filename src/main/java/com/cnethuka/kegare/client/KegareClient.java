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
        com.cnethuka.kegare.innate_technique.swap.client.SwapClient.init(modBus);
    }

    /** Emblem/icon art is looked up once per texture; forget the answers when resource packs change. */
    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> GuiDraw.clearArtCache());
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Technique Mode: the combat panel takes the hotbar's place (HudClearance hides the vanilla hotbar and XP);
        // it raises Gui.leftHeight/rightHeight so hearts and food sit on top of it.
        event.registerAbove(VanillaGuiLayers.HOTBAR, CombatPanelLayer.ID, new CombatPanelLayer());
        event.registerAbove(CombatPanelLayer.ID, CombatHotbarLayer.ID, new CombatHotbarLayer());
        // After hearts/armour/food/air, so it clears whatever they used.
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_LEVEL, CombatNameLayer.ID, new CombatNameLayer());
        // Without the panel: freeze stacks and casting progress under the crosshair.
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, StatusLayer.ID, new StatusLayer());
    }
}
