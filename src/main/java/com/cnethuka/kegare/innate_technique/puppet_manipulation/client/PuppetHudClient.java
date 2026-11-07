package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.client.ClientLoadout;
import com.cnethuka.kegare.client.CombatPanelLayer;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.TechniqueMode;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.client.gui.SorcererScreen;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetOrder;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsVariation;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.network.SyncPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Puppet Manipulation on the combat panel (Technique Mode): "Puppets: X/15" above the meter's left end (gold at the cap),
 * and the current Directive order as a small glyph on Directive's slot (top left; the key is bottom right and the R
 * badge top right). Strings' stage: while a string waits, its slot shows what the keys would do right now, and a line
 * under the crosshair gives those costs (in and out of Technique Mode); with Strings selected and no string out, "Fire: 20"
 * above the meter's right end. The Sorcerer screen lists the stage and every cost.
 */
final class PuppetHudClient {
    private static final int LAVENDER = 0xFF000000 | PuppetManipulation.MALICE_COLOR;

    private PuppetHudClient() {}

    static void init() {
        CombatPanelLayer.addSlotDecorator(PuppetHudClient::decorate);
        CombatPanelLayer.addPanelDecorator(PuppetHudClient::decoratePanel);
        StatusLayer.addExtra(PuppetHudClient::stringsStatus);
        SorcererScreen.addDetailLines(PuppetHudClient::sorcererLines);
    }

    private static ResourceLocation glyph(PuppetOrder order) {
        return Kegare.id("textures/gui/puppet_order/" + order.serializedName() + ".png");
    }

    private static void decoratePanel(GuiGraphics g, Font font, int x0, int y0) {
        PuppetHud.Payload hud = PossessionClient.hud();
        if (!hud.holds()) return;
        boolean full = hud.puppets() >= hud.maxPuppets();
        g.drawString(font, Component.translatable("hud.kegare.puppets", hud.puppets(), hud.maxPuppets()), x0 + 8, y0 - 3, full ? 0xFFFFD24A : LAVENDER, true);
        // Not on the Sorcerer screen's tray: Reset sits there in a small window, and that screen lists the cost anyway.
        if (hud.strings().stage() == 0 && stringsSelected() && Minecraft.getInstance().screen == null) {
            Component fire = Component.translatable("hud.kegare.strings.fire", GuiDraw.amount(hud.strings().fireCost()));
            g.drawString(font, fire, x0 + CombatPanelLayer.W - 8 - font.width(fire), y0 - 3, LAVENDER, true);
        }
    }

    private static boolean stringsSelected() {
        SyncPayload data = ClientData.get();
        if (data == null || data.selectedSlot() < 0 || data.selectedSlot() >= data.loadout().size()) return false;
        return data.loadout().get(data.selectedSlot()).map(id -> id.equals(PuppetManipulation.STRINGS.id())).orElse(false);
    }

    private static ResourceLocation icon(StringsVariation variation) {
        return Kegare.id("textures/gui/ability/strings_" + switch (variation) {
            case REEL_IN -> "reel";
            case TIE -> "tie";
            case ZIP -> "zip";
            case LEASH -> "leash";
        } + ".png");
    }

    private static Component name(StringsVariation variation) {
        return Component.translatable(switch (variation) {
            case REEL_IN -> "hud.kegare.strings.reel_in";
            case TIE -> "hud.kegare.strings.tie";
            case ZIP -> "hud.kegare.strings.zip";
            case LEASH -> "hud.kegare.strings.leash";
        });
    }

    /** A string waiting for its second stage: what each key would do now and cost, and how long it has left. */
    private static int stringsStatus(GuiGraphics g, Minecraft mc, int cx, int y) {
        PuppetHud.StringsHud s = PossessionClient.hud().strings();
        int slot = ClientLoadout.slotOf(PuppetManipulation.STRINGS);
        if (s.stage() != 1 || slot < 0 || !TechniqueMode.isActive()) return y; // out of the mode, none of these keys do this
        Component number = ClientLoadout.numberKey(slot);
        Component special = stringsSelected() ? KegareKeys.SPECIAL.getTranslatedKeyMessage()
                : Component.translatable("hud.kegare.key_combo", KegareKeys.SPECIAL.getTranslatedKeyMessage(), number);
        Component line = Component.translatable("hud.kegare.strings.attached", number, name(s.cast()), GuiDraw.amount(s.cost(s.cast())),
                special, name(s.special()), GuiDraw.amount(s.cost(s.special())), s.secondsLeft());
        GuiDraw.centered(g, mc.font, line, cx, y, 1f, LAVENDER);
        return y + 11;
    }

    /** Strings on the Sorcerer screen: where it stands now, then what each stage costs. */
    private static List<Component> sorcererLines(Ability ability) {
        if (ability != PuppetManipulation.STRINGS) return List.of();
        PuppetHud.StringsHud s = PossessionClient.hud().strings();
        Component now = switch (s.stage()) {
            case 1 -> Component.translatable("gui.kegare.strings.stage.attached");
            case 2 -> Component.translatable("gui.kegare.strings.stage.running");
            default -> Component.translatable("gui.kegare.strings.stage.fire", GuiDraw.amount(s.fireCost()));
        };
        return List.of(now, Component.translatable("gui.kegare.strings.then", GuiDraw.amount(s.reelCost()), GuiDraw.amount(s.tieCost())),
                Component.translatable("gui.kegare.strings.then_special", GuiDraw.amount(s.zipCost()), GuiDraw.amount(s.leashCost())));
    }

    private static void decorate(GuiGraphics g, Font font, Ability ability, int x, int y, int size) {
        PuppetHud.Payload hud = PossessionClient.hud();
        if (!hud.holds()) return;
        if (ability == PuppetManipulation.STRINGS && hud.strings().stage() == 1) {
            // A string out: the slot shows what its number key would do now, with R's in the corner.
            RenderSystem.enableBlend();
            g.blit(icon(hud.strings().cast()), x + 2, y + 2, 0f, 0f, 16, 16, 16, 16);
            g.renderOutline(x + 1, y + 1, size - 2, size - 2, LAVENDER);
            g.pose().pushPose();
            g.pose().translate(x - 1, y - 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.blit(icon(hud.strings().special()), 0, 0, 0f, 0f, 16, 16, 16, 16);
            g.pose().popPose();
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
