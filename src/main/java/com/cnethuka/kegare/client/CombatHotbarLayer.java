package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * While the combat panel is up the vanilla hotbar stands on the left edge as a column: vanilla's own hotbar,
 * selection and off-hand sprites turned a quarter turn (slot 1 at the top, the off-hand slot above it), items drawn
 * upright with vanilla's pick-up "pop". Nothing custom: it is the normal hotbar, moved.
 */
public final class CombatHotbarLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("combat_hotbar");

    private static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation OFFHAND = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    /** The column's footprint: vanilla's 182x22 hotbar, turned. */
    public static final int WIDTH = 22;
    public static final int LENGTH = 182;
    private static final int LEFT = 1;
    private static final long SLIDE_MS = 180;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!CombatPanelLayer.shown() || player == null) return;
        float t = Math.min(1f, (Util.getMillis() - TechniqueMode.shownAtMs()) / (float) SLIDE_MS);
        int slide = Math.round((1f - t) * (1f - t) * (WIDTH + LEFT + 2));
        int x = LEFT - slide;
        int y = g.guiHeight() - 1 - LENGTH;
        ItemStack offhand = player.getOffhandItem();

        // The frame: vanilla's sprites in a frame turned 90° clockwise, so the hotbar's left end is at the top.
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(x + WIDTH, y, -90f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90f));
        g.blitSprite(HOTBAR, 0, 0, LENGTH, WIDTH);
        g.blitSprite(SELECTION, -1 + player.getInventory().selected * 20, -1, 24, 23);
        if (!offhand.isEmpty()) g.blitSprite(OFFHAND, -29, -1, 29, 24);
        g.pose().popPose();
        RenderSystem.disableBlend();

        // The items, upright, where vanilla puts them inside each cell.
        for (int i = 0; i < 9; i++) {
            slot(g, mc, deltaTracker, player, player.getInventory().items.get(i), x + 3, y + 3 + i * 20, i + 1);
        }
        if (!offhand.isEmpty()) slot(g, mc, deltaTracker, player, offhand, x + 3, y - 26, 10);
    }

    /** Gui#renderSlot: the item (squashed for a moment after it was picked up), then its count and durability. */
    private static void slot(GuiGraphics g, Minecraft mc, DeltaTracker deltaTracker, LocalPlayer player, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
        if (pop > 0f) {
            float s = 1f + pop / 5f;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0f);
            g.pose().scale(1f / s, (s + 1f) / 2f, 1f);
            g.pose().translate(-(x + 8), -(y + 12), 0f);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0f) g.pose().popPose();
        g.renderItemDecorations(mc.font, stack, x, y);
    }
}
