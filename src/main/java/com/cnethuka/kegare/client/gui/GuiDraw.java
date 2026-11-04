package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Shared drawing helpers for Kegare screens. Sprites live in {@code textures/gui/sprites/} (nine-slice where a
 * {@code .mcmeta} says so) and are generated as placeholders by {@code tools/gen_placeholders.py}.
 */
public final class GuiDraw {
    public static final ResourceLocation PANEL = Kegare.id("panel");
    public static final ResourceLocation CARD = Kegare.id("card");
    public static final ResourceLocation TAB = Kegare.id("tab");
    public static final ResourceLocation TAB_SELECTED = Kegare.id("tab_selected");
    public static final ResourceLocation SLOT = Kegare.id("radial_slot");
    public static final ResourceLocation SLOT_SELECTED = Kegare.id("radial_slot_selected");
    public static final ResourceLocation SPARKLE = Kegare.id("sparkle");
    public static final ResourceLocation SEVERED_EMBLEM = Kegare.id("severed_emblem");

    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int DARK_GRAY = 0xFF777777;
    public static final int MALICE = 0xFFD9B3FF;
    public static final int GOLD = 0xFFFFD24A;
    public static final int AQUA = 0xFF7FE6FF;
    public static final int RED = 0xFFFF6B6B;

    private GuiDraw() {}

    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        g.blitSprite(sprite, x, y, w, h);
    }

    /** Sprite multiplied by an RGB color (cards and slots are greyscale so they can take any rarity color). */
    public static void tinted(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, int rgb, float alpha) {
        RenderSystem.enableBlend();
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
        g.blitSprite(sprite, x, y, w, h);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Soft glow: stacked translucent rectangles around a box. */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int rgb, float strength) {
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) (Mth.clamp(strength, 0f, 1f) * 120 * (1f - i / 7f));
            g.fill(x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2, (alpha << 24) | (rgb & 0xFFFFFF));
        }
    }

    /**
     * An innate technique card: rarity-tinted frame, icon, name (up to two lines) and rarity.
     *
     * @param dim 0 = full brightness, 1 = fully darkened (non-result cards after the spin lands)
     */
    public static void card(GuiGraphics g, Font font, InnateTechnique innateTechnique, int x, int y, int w, int h, float dim) {
        int color = innateTechnique.rarity().color();
        tinted(g, CARD, x, y, w, h, color, 1f);
        g.renderItem(new ItemStack(innateTechnique.tabIcon()), x + w / 2 - 8, y + Math.max(4, h / 7));

        float scale = w >= 76 ? 1f : 0.75f;
        int textWidth = (int) ((w - 6) / scale);
        List<FormattedCharSequence> lines = font.split(innateTechnique.displayName(), textWidth);
        int lineY = y + Math.max(4, h / 7) + 22;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            centered(g, font, lines.get(i), x + w / 2, lineY, scale, WHITE);
            lineY += (int) (10 * scale);
        }
        Component rarity = Component.translatable(innateTechnique.rarity().translationKey());
        centered(g, font, rarity.getVisualOrderText(), x + w / 2, y + h - (int) (12 * scale), scale, 0xFF000000 | color);

        if (dim > 0f) g.fill(x, y, x + w, y + h, 300, ((int) (Mth.clamp(dim, 0f, 1f) * 190) << 24));
    }

    /** Centered text at a scale (1 = normal). */
    public static void centered(GuiGraphics g, Font font, FormattedCharSequence text, int centerX, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200); // above item icons (z~150), below card dimming (z 300)
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, (int) (centerX / scale - font.width(text) / 2f), (int) (y / scale), color, true);
        g.pose().popPose();
    }

    public static void centered(GuiGraphics g, Font font, Component text, int centerX, int y, float scale, int color) {
        centered(g, font, text.getVisualOrderText(), centerX, y, scale, color);
    }

    /** Left-aligned text at a scale. Returns the scaled line height. */
    public static int text(GuiGraphics g, Font font, Component text, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, (int) (x / scale), (int) (y / scale), color, true);
        g.pose().popPose();
        return (int) Math.ceil(font.lineHeight * scale);
    }

    /** Word-wrapped, left-aligned. Returns the height used. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        int dy = 0;
        for (FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            g.drawString(font, line, x, y + dy, color, true);
            dy += font.lineHeight + 1;
        }
        return dy;
    }

    /** Word-wrapped, centered. Returns the height used. */
    public static int wrappedCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int width, int color) {
        int dy = 0;
        for (FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            g.drawString(font, line, centerX - font.width(line) / 2, y + dy, color, true);
            dy += font.lineHeight + 1;
        }
        return dy;
    }

    /** Lang key for an innate technique's or ability's one-line description. */
    public static Component description(String descriptionId) {
        return Component.translatable(descriptionId + ".desc");
    }

    public static String seconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    /** Formats a Malice amount: whole numbers stay whole, fractions keep one decimal (e.g. upkeep 2.5/s). */
    public static String amount(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
