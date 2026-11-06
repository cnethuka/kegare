package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.CombatPanelLayer;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetOrder;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Puppet Manipulation on the combat panel (Technique Mode): "Puppets: X/15" above the meter's left end (gold at the cap),
 * and the current Directive order as a small glyph on Directive's slot (top left; the key is bottom right and the R
 * badge top right).
 */
final class PuppetHudClient {
    private static final int LAVENDER = 0xFF000000 | PuppetManipulation.MALICE_COLOR;

    private PuppetHudClient() {}

    static void init() {
        CombatPanelLayer.addSlotDecorator(PuppetHudClient::decorate);
        CombatPanelLayer.addPanelDecorator(PuppetHudClient::decoratePanel);
    }

    private static ResourceLocation glyph(PuppetOrder order) {
        return Kegare.id("textures/gui/puppet_order/" + order.serializedName() + ".png");
    }

    private static void decoratePanel(GuiGraphics g, Font font, int x0, int y0) {
        PuppetHud.Payload hud = PossessionClient.hud();
        if (!hud.holds()) return;
        boolean full = hud.puppets() >= hud.maxPuppets();
        g.drawString(font, Component.translatable("hud.kegare.puppets", hud.puppets(), hud.maxPuppets()), x0 + 8, y0 - 3, full ? 0xFFFFD24A : LAVENDER, true);
    }

    private static void decorate(GuiGraphics g, Font font, Ability ability, int x, int y, int size) {
        PuppetHud.Payload hud = PossessionClient.hud();
        if (!hud.holds()) return;
        if (ability == PuppetManipulation.DIRECTIVE) {
            RenderSystem.enableBlend();
            ResourceLocation glyph = glyph(PuppetOrder.byId(hud.order()));
            g.setColor(0f, 0f, 0f, 0.85f); // a drop shadow, so it reads over the icon
            g.blit(glyph, x + 1, y, 0f, 0f, 7, 7, 7, 7);
            g.setColor(1f, 1f, 1f, 1f);
            g.blit(glyph, x, y - 1, 0f, 0f, 7, 7, 7, 7);
        }
    }
}
