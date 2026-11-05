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
import net.minecraft.resources.ResourceLocation;

/**
 * Puppet Manipulation on the combat panel (Technique Mode): how many puppets you have, on Puppet Creation's slot, and
 * the current Directive order as a small glyph on Directive's slot (top left of each; the key is bottom right and the
 * R badge top right).
 */
final class PuppetHudClient {
    private static final int LAVENDER = 0xFF000000 | PuppetManipulation.MALICE_COLOR;

    private PuppetHudClient() {}

    static void init() {
        CombatPanelLayer.addSlotDecorator(PuppetHudClient::decorate);
    }

    private static ResourceLocation glyph(PuppetOrder order) {
        return Kegare.id("textures/gui/puppet_order/" + order.serializedName() + ".png");
    }

    private static void decorate(GuiGraphics g, Font font, Ability ability, int x, int y, int size) {
        PuppetHud.Payload hud = PossessionClient.hud();
        if (!hud.holds()) return;
        if (ability == PuppetManipulation.PUPPET_CREATION) {
            String count = Integer.toString(hud.puppets());
            g.drawString(font, count, x + 1, y - 1, hud.puppets() > 0 ? LAVENDER : 0xFF8A80A0, true);
        } else if (ability == PuppetManipulation.DIRECTIVE) {
            RenderSystem.enableBlend();
            ResourceLocation glyph = glyph(PuppetOrder.byId(hud.order()));
            g.setColor(0f, 0f, 0f, 0.85f); // a drop shadow, so it reads over the icon
            g.blit(glyph, x + 1, y, 0f, 0f, 7, 7, 7, 7);
            g.setColor(1f, 1f, 1f, 1f);
            g.blit(glyph, x, y - 1, 0f, 0f, 7, 7, 7, 7);
        }
    }
}
