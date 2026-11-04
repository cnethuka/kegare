package com.cnethuka.kegare.trait;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Set of trait IDs a player has (attachment {@code kegare:traits}, kept on death). */
public final class TraitData {
    public static final Codec<TraitData> CODEC = ResourceLocation.CODEC.listOf()
            .xmap(list -> new TraitData(new LinkedHashSet<>(list)), data -> new ArrayList<>(data.traits));

    private final Set<ResourceLocation> traits;

    public TraitData() {
        this(new LinkedHashSet<>());
    }

    private TraitData(Set<ResourceLocation> traits) {
        this.traits = traits;
    }

    public boolean has(ResourceLocation id) {
        return traits.contains(id);
    }

    public Set<ResourceLocation> view() {
        return Collections.unmodifiableSet(traits);
    }

    // Mutators are package-private: go through TraitService (it enforces uniqueness).
    boolean add(ResourceLocation id) {
        return traits.add(id);
    }

    boolean remove(ResourceLocation id) {
        return traits.remove(id);
    }
}
