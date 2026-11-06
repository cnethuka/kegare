package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.network.ProgressionToastPayload;
import com.cnethuka.kegare.progression.Grade;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The small private toast for a promotion or an unlock. A toast like any other, so {@link RevealQueue} holds it during a reveal. */
final class ProgressionToast implements Toast {
    private static final ResourceLocation FRAME = Kegare.id("menu_well");
    private static final long SHOWN_MS = 4000;
    private static final int W = 150;
    private static final int H = 28;

    private final Component title;
    private final Component name;
    @Nullable
    private final ResourceLocation ability;

    private ProgressionToast(String titleKey, Component name, @Nullable ResourceLocation ability) {
        this.title = Component.translatable(titleKey);
        this.name = name;
        this.ability = ability;
    }

    static ProgressionToast of(ProgressionToastPayload payload) {
        return switch (payload.kind()) {
            case PROMOTION -> new ProgressionToast("toast.kegare.promotion",
                    Component.translatable(Grade.byId(payload.id()).map(Grade::translationKey).orElse(payload.id())), null);
            case UNLOCK -> new ProgressionToast("toast.kegare.unlocked",
                    Component.translatable(Util.makeDescriptionId("unlock", ResourceLocation.parse(payload.id()))), null);
            case ABILITY, SPECIAL, ULTIMATE -> {
                ResourceLocation id = ResourceLocation.parse(payload.id());
                String title = switch (payload.kind()) {
                    case SPECIAL -> "toast.kegare.unlocked.special";
                    case ULTIMATE -> "toast.kegare.unlocked.ultimate";
                    default -> "toast.kegare.unlocked";
                };
                yield new ProgressionToast(title, Component.translatable(Util.makeDescriptionId("ability", id)), id);
            }
        };
    }

    @Override
    public int width() {
        return W;
    }

    @Override
    public int height() {
        return H;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long timeMs) {
        Font font = toasts.getMinecraft().font;
        GuiDraw.sprite(g, FRAME, 0, 0, W, H);
        if (ability == null || !GuiDraw.abilityIcon(g, ability, 6, 6)) GuiDraw.sprite(g, SorcererScreen.ICON_PROGRESS, 6, 6, 16, 16);
        g.drawString(font, title, 26, 5, GuiDraw.GOLD, true);
        g.drawString(font, font.substrByWidth(name, W - 26 - 5).getString(), 26, 15, GuiDraw.WHITE, true);
        return timeMs >= SHOWN_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
