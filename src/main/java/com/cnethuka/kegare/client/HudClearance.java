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
 * Moves vanilla text that sits just above the hotbar (action-bar messages such as cast feedback, and the held item's
 * name) up past the ability bar, so neither covers the other.
 */
@EventBusSubscriber(modid = Kegare.MODID, value = Dist.CLIENT)
public final class HudClearance {
    /** Bottom of each layer's text, measured up from the screen bottom (Gui#renderOverlayMessage / renderSelectedItemName). */
    private static final int OVERLAY_TEXT_BOTTOM = 63;
    private static final int ITEM_NAME_TEXT_BOTTOM = 50;

    private static boolean shifted;

    private HudClearance() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void pre(RenderGuiLayerEvent.Pre event) {
        int fromBottom = textBottom(event.getName());
        if (fromBottom < 0 || event.isCanceled()) return;
        int up = AbilityBarLayer.clearance(event.getGuiGraphics().guiHeight() - fromBottom);
        if (up <= 0) return;
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(0, -up, 0);
        shifted = true;
    }

    @SubscribeEvent
    static void post(RenderGuiLayerEvent.Post event) {
        if (shifted && textBottom(event.getName()) >= 0) {
            event.getGuiGraphics().pose().popPose();
            shifted = false;
        }
    }

    private static int textBottom(ResourceLocation layer) {
        if (layer.equals(VanillaGuiLayers.OVERLAY_MESSAGE)) return OVERLAY_TEXT_BOTTOM;
        if (layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) return ITEM_NAME_TEXT_BOTTOM;
        return -1;
    }
}
