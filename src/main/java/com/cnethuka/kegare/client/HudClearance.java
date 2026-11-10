package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * While the combat panel is up: hides the vanilla hotbar, XP bar and level (the panel takes their place; the hotbar is
 * drawn as a column on the left), moves the vanilla text above the hotbar (action-bar messages, the held item's name)
 * and chat up by as much as the panel raised hearts and food (chat also moves right, clear of the hotbar column).
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class HudClearance {
    private static boolean shifted;

    private HudClearance() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void hide(RenderGuiLayerEvent.Pre event) {
        if (!CombatPanelLayer.shown()) return;
        ResourceLocation layer = event.getName();
        if (layer.equals(VanillaGuiLayers.HOTBAR) || layer.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL) || layer.equals(VanillaGuiLayers.JUMP_METER)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void pre(RenderGuiLayerEvent.Pre event) {
        if (!moves(event.getName()) || event.isCanceled() || !CombatPanelLayer.shown()) return;
        boolean chat = event.getName().equals(VanillaGuiLayers.CHAT);
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(chat ? CombatHotbarLayer.WIDTH + 2 : 0, -CombatPanelLayer.extraHeight(), 0);
        shifted = true;
    }

    @SubscribeEvent
    static void post(RenderGuiLayerEvent.Post event) {
        if (shifted && moves(event.getName())) {
            event.getGuiGraphics().pose().popPose();
            shifted = false;
        }
    }

    private static boolean moves(ResourceLocation layer) {
        return layer.equals(VanillaGuiLayers.OVERLAY_MESSAGE) || layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)
                || layer.equals(VanillaGuiLayers.CHAT);
    }
}
