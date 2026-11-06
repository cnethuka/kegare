package com.cnethuka.kegare.loadout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Per player, saved and kept on death (attachment {@code kegare:loadout}): what each regular ability slot holds, in
 * order (empty = nothing). The ultimate (V) is not part of it and can never change. {@code builtFor} is the innate
 * technique the layout belongs to: if it no longer matches, the layout is rebuilt from the default.
 */
public final class LoadoutData {
    public static final Codec<LoadoutData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(d -> d.slots.stream()
                    .map(s -> s.map(ResourceLocation::toString).orElse("")).toList()),
            ResourceLocation.CODEC.optionalFieldOf("built_for").forGetter(d -> Optional.ofNullable(d.builtFor)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("known", List.of()).forGetter(d -> List.copyOf(d.known))
    ).apply(i, (slots, builtFor, known) -> new LoadoutData(slots.stream()
            .map(s -> s.isEmpty() ? Optional.<ResourceLocation>empty() : Optional.ofNullable(ResourceLocation.tryParse(s))).toList(),
            builtFor.orElse(null), known)));

    final List<Optional<ResourceLocation>> slots;
    @Nullable
    ResourceLocation builtFor;
    /**
     * The innate technique's abilities this bar has already been offered. One not in here was added to the technique after the
     * bar was built: it gets a slot, once. If the player later clears it, it stays cleared.
     */
    final Set<ResourceLocation> known;

    public LoadoutData() {
        this(List.of(), null, List.of());
    }

    private LoadoutData(List<Optional<ResourceLocation>> slots, @Nullable ResourceLocation builtFor, List<ResourceLocation> known) {
        this.slots = new ArrayList<>(slots);
        this.builtFor = builtFor;
        this.known = new LinkedHashSet<>(known);
    }

    public List<Optional<ResourceLocation>> slots() {
        return Collections.unmodifiableList(slots);
    }

    public Optional<ResourceLocation> builtFor() {
        return Optional.ofNullable(builtFor);
    }
}
