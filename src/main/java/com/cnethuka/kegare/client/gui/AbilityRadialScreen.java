package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.client.ClientLoadout;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.network.SelectAbilityPayload;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Radial ability picker, opened by holding the next-ability key. Release (or click) over a slot to select it;
 * release in the middle or press Esc to keep the current one. Selection is a request the server validates.
 * Shows cooldown sweeps from the synced state.
 */
public final class AbilityRadialScreen extends Screen {
    private static final int SLOT = 26;

    private int hovered = -1;

    public AbilityRadialScreen() {
        super(Component.translatable("gui.kegare.radial.title"));
    }

    /** Only with an innate technique that has abilities (not Severed). */
    public static boolean canOpen() {
        SyncPayload data = ClientData.get();
        if (data == null || !data.born() || data.severed()) return false;
        return innateTechnique(data) != null && ClientLoadout.size() > 0;
    }

    @Nullable
    private static InnateTechnique innateTechnique(SyncPayload data) {
        return data.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
    }

    private int radius() {
        return Mth.clamp((int) (Math.min(width, height) * 0.28f), 40, 90);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        SyncPayload data = ClientData.get();
        InnateTechnique t = data == null ? null : innateTechnique(data);
        if (t == null) {
            onClose();
            return;
        }
        List<Ability> abilities = ClientLoadout.abilities(); // the loadout; null = an empty slot
        int cx = width / 2;
        int cy = height / 2;
        int r = radius();
        hovered = hoveredSlot(mouseX, mouseY, abilities.size());
        long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;

        // A faint dotted ink ring through the slots.
        int dots = Math.max(24, (int) (Mth.TWO_PI * r / 6));
        for (int i = 0; i < dots; i++) {
            double a = i * Mth.TWO_PI / dots;
            int dx = cx + (int) Math.round(Math.cos(a) * r);
            int dy = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(dx - 1, dy - 1, dx + 1, dy + 1, 0x55D9B3FF);
        }

        for (int i = 0; i < abilities.size(); i++) {
            double angle = angleOf(i, abilities.size());
            int x = cx + (int) Math.round(Math.cos(angle) * r) - SLOT / 2;
            int y = cy + (int) Math.round(Math.sin(angle) * r) - SLOT / 2;
            Ability ability = abilities.get(i);
            boolean isHovered = i == hovered;
            GuiDraw.sprite(g, isHovered ? GuiDraw.SLOT_SELECTED : GuiDraw.SLOT, x, y, SLOT, SLOT);
            if (i == data.selectedSlot()) g.renderOutline(x - 2, y - 2, SLOT + 4, SLOT + 4, GuiDraw.WHITE); // current (hover is gold)
            if (ability == null) {
                g.drawCenteredString(font, Integer.toString(i + 1), x + SLOT / 2, y + (SLOT - 8) / 2, isHovered ? GuiDraw.WHITE : 0xFF606060);
                continue;
            }
            if (com.cnethuka.kegare.client.AbilityBarLayer.toggledOn(data, ability)) g.renderOutline(x, y, SLOT, SLOT, 0xFF5CFF7A);

            boolean hasIcon = GuiDraw.abilityIcon(g, ability, x + (SLOT - 16) / 2, y + (SLOT - 16) / 2);

            int remaining = cooldownRemaining(data, ability, now);
            int duration = cooldownDuration(data, ability);
            Component label = Component.literal(Integer.toString(i + 1));
            int labelColor = isHovered ? GuiDraw.WHITE : GuiDraw.GRAY;
            if (remaining > 0 && duration > 0) {
                int inner = SLOT - 8;
                int sweep = Mth.ceil(inner * Math.min(1f, remaining / (float) duration));
                g.fill(x + 4, y + 4, x + 4 + inner, y + 4 + inner, GuiDraw.COOLDOWN_DIM);
                g.fill(x + 4, y + 4 + inner - sweep, x + 4 + inner, y + 4 + inner, GuiDraw.COOLDOWN_SWEEP);
                g.drawCenteredString(font, Integer.toString(Mth.ceil(remaining / 20f)), x + SLOT / 2, y + (SLOT - 8) / 2, GuiDraw.RED);
            } else if (hasIcon) {
                GuiDraw.cornerLabel(g, font, label, x - 1, y, SLOT, labelColor);
            } else {
                g.drawCenteredString(font, label, x + SLOT / 2, y + (SLOT - 8) / 2, labelColor);
            }
        }

        // Center: the hovered ability (or the current one).
        int shown = hovered >= 0 ? hovered : Mth.clamp(data.selectedSlot(), 0, abilities.size() - 1);
        Ability ability = abilities.get(shown);
        GuiDraw.centered(g, font, Component.translatable("gui.kegare.radial.hint"), cx, Math.max(4, cy - r - SLOT - 6), 1f, GuiDraw.GRAY);
        if (ability == null) {
            GuiDraw.centered(g, font, Component.translatable("hud.kegare.empty_slot"), cx, cy - font.lineHeight / 2, 1f, GuiDraw.DARK_GRAY);
            return;
        }
        GuiDraw.centered(g, font, ability.displayName(), cx, cy - font.lineHeight, 1f, hovered >= 0 ? GuiDraw.WHITE : GuiDraw.GRAY);
        GuiDraw.centered(g, font, Component.translatable(ability.castType().translationKey()), cx, cy + 2, 1f, GuiDraw.DARK_GRAY);
        CostTooltip.shortLine(ability.id()).ifPresent(line -> GuiDraw.centered(g, font, line, cx, cy + 2 + font.lineHeight + 1, 1f, GuiDraw.MALICE));
        ability.special().ifPresent(special -> GuiDraw.centered(g, font, Component.translatable("gui.kegare.radial.special",
                KegareKeys.SPECIAL.getTranslatedKeyMessage(), special.displayName()), cx, cy + 2 + 2 * (font.lineHeight + 1), 1f, GuiDraw.GOLD));
    }

    /** Slot 0 at the top, then clockwise. */
    private static double angleOf(int i, int count) {
        return -Math.PI / 2 + i * (2 * Math.PI / count);
    }

    private int hoveredSlot(double mouseX, double mouseY, int count) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (count == 0 || Math.hypot(dx, dy) < radius() * 0.35) return -1;
        double angle = Math.atan2(dy, dx);
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double diff = Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - angleOf(i, count))));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private static int cooldownRemaining(SyncPayload data, Ability ability, long now) {
        for (SyncPayload.CooldownEntry c : data.cooldowns()) {
            if (c.ability().equals(ability.id())) return (int) Math.max(0, c.endTick() - now);
        }
        return 0;
    }

    private static int cooldownDuration(SyncPayload data, Ability ability) {
        for (SyncPayload.CooldownEntry c : data.cooldowns()) {
            if (c.ability().equals(ability.id())) return c.duration();
        }
        return 0;
    }

    private void commit() {
        SyncPayload data = ClientData.get();
        if (hovered >= 0 && data != null && hovered != data.selectedSlot()) {
            PacketDistributor.sendToServer(new SelectAbilityPayload(hovered));
        }
        onClose();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (KegareKeys.RADIAL.matches(keyCode, scanCode)) {
            commit();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        hovered = hoveredSlot(mouseX, mouseY, abilityCount());
        commit();
        return true;
    }

    private int abilityCount() {
        SyncPayload data = ClientData.get();
        return ClientLoadout.size();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
