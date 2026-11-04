package com.cnethuka.kegare.technique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-player technique state (attachment {@code kegare:technique}, kept on death):
 * current technique, selected ability slot and cooldowns (keyed by ability ID, in level game time).
 */
public final class TechniqueData {
    public record Cooldown(long endTick, int duration) {
        public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(Cooldown::endTick),
                Codec.INT.fieldOf("duration").forGetter(Cooldown::duration)
        ).apply(i, Cooldown::new));
    }

    public static final Codec<TechniqueData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("technique").forGetter(d -> Optional.ofNullable(d.techniqueId)),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(d -> d.selectedSlot),
            Codec.unboundedMap(ResourceLocation.CODEC, Cooldown.CODEC).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns)
    ).apply(i, (technique, selected, cooldowns) -> new TechniqueData(technique.orElse(null), selected, new HashMap<>(cooldowns))));

    @Nullable
    private ResourceLocation techniqueId;
    private int selectedSlot;
    private final Map<ResourceLocation, Cooldown> cooldowns;

    public TechniqueData() {
        this(null, 0, new HashMap<>());
    }

    private TechniqueData(@Nullable ResourceLocation techniqueId, int selectedSlot, Map<ResourceLocation, Cooldown> cooldowns) {
        this.techniqueId = techniqueId;
        this.selectedSlot = selectedSlot;
        this.cooldowns = cooldowns;
    }

    public Optional<ResourceLocation> techniqueId() {
        return Optional.ofNullable(techniqueId);
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public Map<ResourceLocation, Cooldown> cooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    // Mutators are package-private: go through TechniqueService / CastManager.
    void setTechnique(@Nullable ResourceLocation id) {
        this.techniqueId = id;
        this.selectedSlot = 0;
        this.cooldowns.clear();
    }

    void setSelectedSlot(int slot) {
        this.selectedSlot = slot;
    }

    void putCooldown(ResourceLocation ability, Cooldown cooldown) {
        cooldowns.put(ability, cooldown);
    }

    void pruneCooldowns(long now) {
        cooldowns.values().removeIf(c -> c.endTick() <= now);
    }
}
