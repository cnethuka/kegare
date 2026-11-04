package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class KegareTags {
    /** All talisman items (only the reroll talisman for now). */
    public static final TagKey<Item> TALISMANS = TagKey.create(Registries.ITEM, Kegare.id("talismans"));

    private KegareTags() {}
}
