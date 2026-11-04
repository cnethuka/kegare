package com.cnethuka.kegare.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.network.SyncPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Top-left HUD: innate technique name, Malice bar (current/capacity), ability slots with cooldowns,
 * selected ability, and a charge/channel bar. Hidden for Severed players.
 * Purely visual: reads {@link ClientData}, never decides anything.
 */
public final class MaliceHudLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = Kegare.id("malice_hud");

    private static final ResourceLocation BAR = Kegare.id("textures/gui/malice_bar.png");
    private static final ResourceLocation SLOT = Kegare.id("textures/gui/ability_slot.png");
    private static final ResourceLocation SLOT_SELECTED = Kegare.id("textures/gui/ability_slot_selected.png");

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_GAP = 2;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_MALICE_TEXT = 0xD9B3FF;
    private static final int COLOR_COOLDOWN_TEXT = 0xFF6B6B;
    /** Cooldown overlay: dim the whole box, then a translucent white sweep for the remaining fraction. */
    private static final int COLOR_COOLDOWN_DIM = 0x99000000;
    private static final int COLOR_COOLDOWN_SWEEP = 0x70FFFFFF;
    private static final int COLOR_TOGGLE_ON = 0xFF5CFF7A;
    private static final int COLOR_CASTING = 0xFFFFD24A;
    private static final int COLOR_CHANNEL = 0xFF7FD4FF;

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!KegareClientConfig.HUD_ENABLED.get() || mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.player.isSpectator() || mc.getDebugOverlay().showDebugScreen()) return;
        SyncPayload data = ClientData.get();
        if (data == null || !data.born() || data.severed()) return; // Severed: no HUD

        Font font = mc.font;
        long now = mc.level.getGameTime();
        int x = KegareClientConfig.HUD_X.get();
        int y = KegareClientConfig.HUD_Y.get();

        InnateTechnique innateTechnique = data.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        Component title = innateTechnique != null
                ? innateTechnique.displayName()
                : Component.translatable("hud.kegare.no_innate_technique").withStyle(ChatFormatting.GRAY);
        g.drawString(font, title, x, y, COLOR_TEXT);
        y += font.lineHeight + 2;

        drawMaliceBar(g, x, y, data.current(), data.capacity());
        g.drawString(font, Component.translatable("hud.kegare.malice", Mth.floor(data.current()), data.capacity()), x + BAR_WIDTH + 4, y, COLOR_MALICE_TEXT);
        y += BAR_HEIGHT + 4;

        if (innateTechnique == null) return;

        // Ability slots, then the ultimate slot.
        List<Ability> abilities = innateTechnique.abilities();
        ResourceLocation castingId = data.casting().map(SyncPayload.CastingEntry::ability).orElse(null);
        int sx = x;
        for (int i = 0; i < abilities.size(); i++) {
            drawSlot(g, font, data, abilities.get(i), sx, y, i == data.selectedSlot(), Component.literal(Integer.toString(i + 1)), castingId, now);
            sx += SLOT_SIZE + SLOT_GAP;
        }
        Optional<Ability> ultimate = innateTechnique.ultimate();
        if (ultimate.isPresent()) {
            sx += 4;
            drawSlot(g, font, data, ultimate.get(), sx, y, false, KegareKeys.ULTIMATE.getTranslatedKeyMessage(), castingId, now);
        }
        y += SLOT_SIZE + 3;

        // Selected ability name + cast type + cooldown.
        Ability selected = innateTechnique.ability(data.selectedSlot());
        if (selected != null) {
            MutableComponent line = Component.translatable("hud.kegare.selected", selected.displayName(),
                    Component.translatable(selected.castType().translationKey()).withStyle(ChatFormatting.DARK_GRAY));
            int lineWidth = g.drawString(font, line, x, y, COLOR_TEXT);
            int remaining = cooldownRemaining(data, selected, now);
            if (remaining > 0) {
                g.drawString(font, Component.translatable("hud.kegare.cooldown", CastManager.seconds(remaining)), lineWidth + 4, y, COLOR_COOLDOWN_TEXT);
            }
            y += font.lineHeight + 2;
        }

        // Charge / channel progress.
        data.casting().ifPresent(casting -> {
            Ability ability = innateTechnique.findAbility(casting.ability()).orElse(null);
            if (ability == null) return;
            long elapsed = Math.max(0, now - casting.startTick());
            float progress;
            int color;
            if (ability.castType() == CastType.CHARGED) {
                progress = casting.maxTicks() <= 0 ? 1f : Math.min(1f, elapsed / (float) casting.maxTicks());
                color = COLOR_CASTING;
            } else {
                progress = casting.maxTicks() <= 0 ? 1f : Math.max(0f, 1f - elapsed / (float) casting.maxTicks());
                color = COLOR_CHANNEL;
            }
            drawProgress(g, x, KegareClientConfig.HUD_Y.get() + font.lineHeight + 2 + BAR_HEIGHT + 1, progress, color);
        });
    }

    private static void drawSlot(GuiGraphics g, Font font, SyncPayload data, Ability ability, int x, int y, boolean selected,
                                 Component label, @Nullable ResourceLocation castingId, long now) {
        g.blit(selected ? SLOT_SELECTED : SLOT, x, y, 0f, 0f, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);

        // Every slot on cooldown (not just the selected one): dim the box, sweep the remaining fraction,
        // and show whole seconds left instead of the slot label.
        int remaining = cooldownRemaining(data, ability, now);
        int duration = cooldownDuration(data, ability);
        boolean onCooldown = remaining > 0 && duration > 0;
        if (onCooldown) {
            int inner = SLOT_SIZE - 2;
            int sweep = Mth.ceil(inner * Math.min(1f, remaining / (float) duration));
            g.fill(x + 1, y + 1, x + 1 + inner, y + 1 + inner, COLOR_COOLDOWN_DIM);
            g.fill(x + 1, y + 1 + inner - sweep, x + 1 + inner, y + 1 + inner, COLOR_COOLDOWN_SWEEP);
        }
        if (data.activeToggles().contains(ability.id())) g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_TOGGLE_ON);
        if (ability.id().equals(castingId)) g.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_CASTING);
        g.flush(); // overlays first, so the text below always draws on top

        if (onCooldown) {
            String seconds = Integer.toString(Mth.ceil(remaining / 20f));
            g.drawCenteredString(font, seconds, x + SLOT_SIZE / 2, y + (SLOT_SIZE - 8) / 2, COLOR_COOLDOWN_TEXT);
        } else {
            g.drawCenteredString(font, label, x + SLOT_SIZE / 2, y + (SLOT_SIZE - 8) / 2, selected ? 0xFFFFFF : 0xA0A0A0);
        }
    }

    /** 3-slice bar: 2px caps, stretched middle. Texture rows 0-7 empty, 8-15 full. */
    private static void drawMaliceBar(GuiGraphics g, int x, int y, float current, int capacity) {
        drawSliced(g, x, y, 0);
        int fill = capacity <= 0 ? 0 : Mth.clamp(Math.round(BAR_WIDTH * current / capacity), 0, BAR_WIDTH);
        if (fill > 0) {
            g.enableScissor(x, y, x + fill, y + BAR_HEIGHT);
            drawSliced(g, x, y, 8);
            g.disableScissor();
        }
    }

    private static void drawSliced(GuiGraphics g, int x, int y, int v) {
        g.blit(BAR, x, y, 0f, (float) v, 2, BAR_HEIGHT, 16, 16);
        g.blit(BAR, x + 2, y, BAR_WIDTH - 4, BAR_HEIGHT, 2f, (float) v, 12, BAR_HEIGHT, 16, 16);
        g.blit(BAR, x + BAR_WIDTH - 2, y, 14f, (float) v, 2, BAR_HEIGHT, 16, 16);
    }

    private static void drawProgress(GuiGraphics g, int x, int y, float progress, int color) {
        g.fill(x, y, x + BAR_WIDTH, y + 2, 0x80000000);
        g.fill(x, y, x + Math.round(BAR_WIDTH * progress), y + 2, color);
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
}
