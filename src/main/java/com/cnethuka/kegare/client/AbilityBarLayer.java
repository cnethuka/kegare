package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The ability bar, bottom-centre above the hotbar (and above hearts/armour/air, whatever their height): every ability
 * slot plus a larger ultimate slot. Each slot shows its icon, number key, cooldown sweep, a flash when it comes off
 * cooldown, and an "R" badge (lit if the ability has a special). Technique Mode lights the bar and dims the hotbar.
 * Display only: reads {@link ClientData}.
 */
public final class AbilityBarLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("ability_bar");

    private static final int SLOT = GuiDraw.ICON_SLOT_SIZE;
    private static final int GAP = 2;
    private static final int ULT = GuiDraw.ULTIMATE_SLOT_SIZE;
    private static final int READY_FLASH_TICKS = 8;
    /** The selected ability's name shows above the bar this long after the selection changes. */
    private static final int NAME_TICKS = 40;

    private static final int COLOR_COOLDOWN_TEXT = 0xFFFF6B6B;
    private static final int COLOR_TOGGLE_ON = 0xFF5CFF7A;
    private static final int COLOR_CASTING = 0xFFFFD24A;
    private static final int COLOR_CHANNEL = 0xFF7FD4FF;

    /** Ability ID -> game time it came off cooldown (for the ready flash). */
    private static final Map<ResourceLocation, Long> READY_AT = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> WAS_COOLING = new HashMap<>();
    private static int lastSelected = -1;
    private static long selectedAt;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!KegareClientConfig.HUD_ENABLED.get() || mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator()) return;
        SyncPayload data = ClientData.get();
        InnateTechnique technique = TechniqueMode.current(); // null when Severed: no ability bar
        if (data == null || technique == null) return;
        List<Ability> abilities = technique.abilities();
        if (abilities.isEmpty()) return;

        Font font = mc.font;
        long now = mc.level.getGameTime();
        boolean mode = TechniqueMode.isActive();
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();

        // Technique Mode: the hotbar is not in use, so dim it.
        if (mode) g.fill(screenW / 2 - 91, screenH - 22, screenW / 2 + 91, screenH, 0xA0000000);

        boolean hasUlt = technique.ultimate().isPresent() || technique.ultimateLocked();
        int width = abilities.size() * SLOT + (abilities.size() - 1) * GAP + (hasUlt ? 6 + ULT : 0);
        int stack = Math.max(mc.gui.leftHeight, mc.gui.rightHeight); // hearts, armour, food, air: whatever is shown
        int bottom = screenH - stack - 3;
        int top = bottom - ULT;
        int x0 = (screenW - width) / 2;

        if (mode) {
            int color = GuiDraw.MALICE & 0xFFFFFF;
            GuiDraw.glow(g, x0 - 4, top - 4, width + 8, ULT + 8, color, 0.55f);
            GuiDraw.sprite(g, GuiDraw.PANEL, x0 - 6, top - 6, width + 12, ULT + 12);
            GuiDraw.centered(g, font, Component.translatable("hud.kegare.technique_mode"), screenW / 2, top - 16, 0.75f,
                    0xFF000000 | brighten(color));
        }

        ResourceLocation castingId = data.casting().map(SyncPayload.CastingEntry::ability).orElse(null);
        for (int i = 0; i < abilities.size(); i++) {
            int sx = x0 + i * (SLOT + GAP);
            int sy = bottom - SLOT;
            drawSlot(g, font, data, abilities.get(i), sx, sy, SLOT, i == data.selectedSlot(), Component.literal(Integer.toString(i + 1)),
                    castingId, now, mode);
        }
        if (hasUlt) {
            int ux = x0 + abilities.size() * (SLOT + GAP) - GAP + 6;
            Optional<Ability> ult = technique.ultimate();
            if (ult.isPresent()) {
                drawSlot(g, font, data, ult.get(), ux, top, ULT, false, KegareKeys.ULTIMATE.getTranslatedKeyMessage(), castingId, now, mode);
            } else {
                GuiDraw.lockedSlot(g, font, ux, top, ULT, KegareKeys.ULTIMATE.getTranslatedKeyMessage());
            }
        }

        drawCastProgress(g, technique, data, now, x0, top - 4, width);
        drawSelectedName(g, font, abilities, data, now, screenW / 2, top - (mode ? 26 : 12));
    }

    private static void drawSlot(GuiGraphics g, Font font, SyncPayload data, Ability ability, int x, int y, int size, boolean selected,
                                 Component label, @Nullable ResourceLocation castingId, long now, boolean mode) {
        boolean hasIcon = GuiDraw.abilitySlot(g, ability, x, y, size, selected);
        int inner = size - 4;

        int remaining = cooldownRemaining(data, ability, now);
        int duration = cooldownDuration(data, ability);
        boolean cooling = remaining > 0 && duration > 0;
        if (cooling) {
            int sweep = Mth.ceil(inner * Math.min(1f, remaining / (float) duration));
            g.fill(x + 2, y + 2, x + 2 + inner, y + 2 + inner, GuiDraw.COOLDOWN_DIM);
            g.fill(x + 2, y + 2 + inner - sweep, x + 2 + inner, y + 2 + inner, GuiDraw.COOLDOWN_SWEEP);
        }
        // Ready flash: a white pulse the moment it comes off cooldown.
        Boolean was = WAS_COOLING.put(ability.id(), cooling);
        if (Boolean.TRUE.equals(was) && !cooling) READY_AT.put(ability.id(), now);
        Long readyAt = READY_AT.get(ability.id());
        if (readyAt != null && now - readyAt < READY_FLASH_TICKS) {
            int alpha = (int) (170 * (1f - (now - readyAt) / (float) READY_FLASH_TICKS));
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, (alpha << 24) | 0xFFFFFF);
        }
        if (data.activeToggles().contains(ability.id())) g.renderOutline(x, y, size, size, COLOR_TOGGLE_ON);
        if (ability.id().equals(castingId) || ability.special().map(s -> s.id().equals(castingId)).orElse(false)) {
            g.renderOutline(x, y, size, size, COLOR_CASTING);
        }
        if (selected) { // a caret above the selected slot
            int cx = x + size / 2;
            for (int i = 0; i < 3; i++) g.fill(cx - 3 + i, y - 4 + i, cx + 3 - i, y - 3 + i, 0xFFFFD24A);
        }
        g.flush();

        GuiDraw.specialBadge(g, x + size - 8, y - 1, ability.special().isPresent());
        if (cooling) {
            g.drawCenteredString(font, Integer.toString(Mth.ceil(remaining / 20f)), x + size / 2, y + (size - 8) / 2, COLOR_COOLDOWN_TEXT);
        } else if (hasIcon) {
            GuiDraw.cornerLabel(g, font, label, x, y, size, selected ? GuiDraw.WHITE : mode ? 0xFFCCCCCC : GuiDraw.GRAY);
        } else {
            g.drawCenteredString(font, label, x + size / 2, y + (size - 8) / 2, selected ? 0xFFFFFF : 0xA0A0A0);
        }
    }

    /** A thin charge (fills) or channel (drains) bar just above the slots. */
    private static void drawCastProgress(GuiGraphics g, InnateTechnique technique, SyncPayload data, long now, int x, int y, int width) {
        data.casting().ifPresent(casting -> {
            Ability ability = technique.findAbility(casting.ability()).orElse(null);
            if (ability == null) return;
            long elapsed = Math.max(0, now - casting.startTick());
            boolean charged = ability.castType() == CastType.CHARGED;
            float progress = casting.maxTicks() <= 0 ? 1f
                    : charged ? Math.min(1f, elapsed / (float) casting.maxTicks()) : Math.max(0f, 1f - elapsed / (float) casting.maxTicks());
            GuiDraw.progress(g, x, y, width, 3, progress, (charged ? COLOR_CASTING : COLOR_CHANNEL) & 0xFFFFFF);
        });
    }

    private static void drawSelectedName(GuiGraphics g, Font font, List<Ability> abilities, SyncPayload data, long now, int cx, int y) {
        int selected = data.selectedSlot();
        if (selected != lastSelected) {
            lastSelected = selected;
            selectedAt = now;
        }
        long age = now - selectedAt;
        if (age >= NAME_TICKS || selected < 0 || selected >= abilities.size()) return;
        int alpha = age < NAME_TICKS - 10 ? 255 : (int) (255 * (NAME_TICKS - age) / 10f);
        if (alpha <= 8) return;
        Component name = abilities.get(selected).displayName();
        g.drawCenteredString(font, name, cx, y, (alpha << 24) | 0xFFFFFF);
    }

    private static int brighten(int rgb) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 90);
        int gr = Math.min(255, ((rgb >> 8) & 0xFF) + 90);
        int b = Math.min(255, (rgb & 0xFF) + 90);
        return (r << 16) | (gr << 8) | b;
    }

    /** Specials share their base ability's cooldown, so the base slot shows it. */
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
}
