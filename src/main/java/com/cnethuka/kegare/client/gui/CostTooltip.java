package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.innatetechnique.CostEnvironment;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * An ability's CURRENT Malice cost where you stand (the server includes environment multipliers such as Rime's cold
 * and heat), with the active factors listed. Reads the latest sync; display only.
 */
public final class CostTooltip {
    private CostTooltip() {}

    @Nullable
    public static SyncPayload.AbilityCost cost(ResourceLocation ability) {
        SyncPayload data = ClientData.get();
        if (data == null) return null;
        for (SyncPayload.AbilityCost c : data.costs()) if (c.ability().equals(ability)) return c;
        return null;
    }

    /** One short line for compact places (radial menu): "Cost 8.5 Malice" or "Cost 8 + 10.2/s". */
    public static Optional<Component> shortLine(ResourceLocation ability) {
        SyncPayload.AbilityCost c = cost(ability);
        if (c == null) return Optional.empty();
        return Optional.of(c.upkeepPerSecond() > 0
                ? Component.translatable("gui.kegare.cost.short_upkeep", GuiDraw.amount(round(c.cost())), GuiDraw.amount(round(c.upkeepPerSecond())))
                : Component.translatable("gui.kegare.cost.short", GuiDraw.amount(round(c.cost()))));
    }

    public static List<Component> lines(ResourceLocation ability) {
        List<Component> out = new ArrayList<>();
        SyncPayload data = ClientData.get();
        SyncPayload.AbilityCost c = cost(ability);
        if (data == null || c == null) return out;
        out.add(Component.translatable("gui.kegare.cost.current", GuiDraw.amount(round(c.cost()))).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (c.upkeepPerSecond() > 0) {
            out.add(Component.translatable("gui.kegare.cost.upkeep", GuiDraw.amount(round(c.upkeepPerSecond()))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        CostEnvironment env = data.environment();
        if (env.factors().isEmpty()) {
            out.add(Component.translatable("gui.kegare.cost.no_modifier").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (CostEnvironment.Factor f : env.factors()) {
                ChatFormatting color = f.multiplier() < 1f ? ChatFormatting.AQUA : ChatFormatting.RED;
                out.add(Component.translatable("gui.kegare.cost.factor", GuiDraw.multiplier(f.multiplier()),
                        Component.translatable(f.translationKey())).withStyle(color));
            }
            out.add(Component.translatable("gui.kegare.cost.total", GuiDraw.multiplier(env.multiplier())).withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static float round(float value) {
        return Math.round(value * 10f) / 10f;
    }
}
