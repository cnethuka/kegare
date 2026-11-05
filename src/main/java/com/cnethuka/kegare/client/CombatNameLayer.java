package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The selected ability's name, shown for 2 s after picking it, above hearts/food/armour (drawn after them, so it
 * clears whatever they used). Only while the combat panel is up.
 */
public final class CombatNameLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("combat_name");
    private static final int NAME_TICKS = 40;

    private static int lastSelected = -1;
    private static long selectedAt;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        SyncPayload data = ClientData.get();
        if (!CombatPanelLayer.shown() || data == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        int selected = data.selectedSlot();
        if (selected != lastSelected) {
            lastSelected = selected;
            selectedAt = now;
        }
        List<Ability> abilities = ClientLoadout.abilities();
        long age = now - selectedAt;
        if (age >= NAME_TICKS || selected < 0 || selected >= abilities.size()) return;
        int alpha = age < NAME_TICKS - 10 ? 255 : (int) (255 * (NAME_TICKS - age) / 10f);
        if (alpha <= 8) return;
        Ability ability = abilities.get(selected);
        Component name = ability != null ? ability.displayName() : Component.translatable("hud.kegare.empty_slot");
        int y = g.guiHeight() - Math.max(mc.gui.leftHeight, mc.gui.rightHeight) - 10;
        g.drawCenteredString(mc.font, name, g.guiWidth() / 2, y, (alpha << 24) | 0xFFFFFF);
    }
}
