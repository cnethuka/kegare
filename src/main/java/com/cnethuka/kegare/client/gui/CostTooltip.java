package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.network.SyncPayload;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * An ability's CURRENT Malice cost where you stand (the server includes environment multipliers such as Rime's cold
 * and heat). Reads the latest sync; display only.
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
}
