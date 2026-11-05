package com.cnethuka.kegare.loadout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Per player, saved and kept on death (attachment {@code kegare:shared_abilities}): which shared abilities are
 * unlocked (they belong to the player: rerolls keep them), plus when the last green-orb roll happened.
 */
public final class SharedAbilityData {
    public static final Codec<SharedAbilityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("unlocked", List.of()).forGetter(d -> List.copyOf(d.unlocked)),
            Codec.LONG.optionalFieldOf("last_orb_roll", Long.MIN_VALUE).forGetter(d -> d.lastOrbRoll)
    ).apply(i, SharedAbilityData::new));

    final Set<ResourceLocation> unlocked;
    /** Game time of the last green-orb chance roll (MIN_VALUE: never). */
    long lastOrbRoll;

    public SharedAbilityData() {
        this(List.of(), Long.MIN_VALUE);
    }

    private SharedAbilityData(List<ResourceLocation> unlocked, long lastOrbRoll) {
        this.unlocked = new LinkedHashSet<>(unlocked);
        this.lastOrbRoll = lastOrbRoll;
    }

    public Set<ResourceLocation> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public boolean isUnlocked(ResourceLocation id) {
        return unlocked.contains(id);
    }

    public long lastOrbRoll() {
        return lastOrbRoll;
    }

    public void setLastOrbRoll(long tick) {
        this.lastOrbRoll = tick;
    }
}
