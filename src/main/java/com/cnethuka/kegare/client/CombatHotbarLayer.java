package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * While the combat panel is up the hotbar is drawn as a column on the left edge (selected slot lit), with the
 * off-hand item above it. In Technique Mode the selection can't change; in SPLIT the number keys still pick from it.
 */
public final class CombatHotbarLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("combat_hotbar");

    private static final ResourceLocation CELL = Kegare.id("textures/gui/combat/hotbar_cell.png");
    private static final ResourceLocation CELL_SELECTED = Kegare.id("textures/gui/combat/hotbar_cell_selected.png");
    private static final int SIZE = 20;
    private static final long SLIDE_MS = 180;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!CombatPanelLayer.shown() || player == null) return;
        float t = Math.min(1f, (Util.getMillis() - TechniqueMode.shownAtMs()) / (float) SLIDE_MS);
        int slide = Math.round((1f - (1f - (1f - t) * (1f - t))) * (SIZE + 4));
        int x = 2 - slide;
        int bottom = g.guiHeight() - 2;
        int top = bottom - 9 * SIZE;
        RenderSystem.enableBlend();
        for (int i = 0; i < 9; i++) {
            int y = top + i * SIZE;
            boolean selected = i == player.getInventory().selected;
            g.blit(selected ? CELL_SELECTED : CELL, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 2, y + 2, i);
                g.renderItemDecorations(mc.font, stack, x + 2, y + 2);
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty()) {
            int y = top - SIZE - 4;
            g.blit(CELL, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
            g.renderItem(offhand, x + 2, y + 2, 9);
            g.renderItemDecorations(mc.font, offhand, x + 2, y + 2);
        }
    }
}
