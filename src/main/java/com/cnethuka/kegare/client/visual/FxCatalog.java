package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.innate_technique.rime.client.RimeFxAuthoring;
import com.lowdragmc.photon.client.fx.FX;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/** DEV TOOL: every effect authored in code, for {@code /kegare debug fx}. Add new authoring classes here. */
public final class FxCatalog {
    private FxCatalog() {}

    public static Map<ResourceLocation, Supplier<FX>> effects() {
        Map<ResourceLocation, Supplier<FX>> out = new LinkedHashMap<>();
        out.putAll(RimeFxAuthoring.effects());
        out.putAll(com.cnethuka.kegare.rmt.client.RmtFxAuthoring.effects());
        out.putAll(com.cnethuka.kegare.innate_technique.swap.client.SwapFxAuthoring.effects());
        out.putAll(com.cnethuka.kegare.innate_technique.puppet_manipulation.client.PuppetFxAuthoring.effects());
        out.putAll(com.cnethuka.kegare.melee.client.MeleeFxAuthoring.effects());
        return out;
    }
}
