package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * IDs Kegare has renamed, read as their new names wherever a player's saved data holds them (loadout slots, Background Task slots,
 * unlocks), in the world's files and in Postgres alike: Reinforcement became Flow, Background Reinforcement Background Flow, and Sustained
 * Flow Steady Output. Saved again, they're written under the new name.
 */
public final class RenamedIds {
    static final Map<ResourceLocation, ResourceLocation> RENAMED = Map.of(
            Kegare.id("reinforcement"), Kegare.id("flow"),
            Kegare.id("background_reinforcement"), Kegare.id("background_flow"),
            Kegare.id("sustained_flow"), Kegare.id("steady_output"));

    /** {@link ResourceLocation#CODEC}, reading a renamed ID as its new one. */
    public static final Codec<ResourceLocation> CODEC = ResourceLocation.CODEC.xmap(RenamedIds::current, id -> id);

    private RenamedIds() {}

    /** The ID as it's called now. */
    public static ResourceLocation current(ResourceLocation id) {
        return RENAMED.getOrDefault(id, id);
    }
}
