package com.cnethuka.kegare.innatetechnique;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-player innate technique state (attachment {@code kegare:innate_technique}, kept on death):
 * current innate technique, selected ability slot and cooldowns (keyed by ability ID, in level game time).
 */
public final class InnateTechniqueData {
    public record Cooldown(long endTick, int duration) {
        public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(Cooldown::endTick),
                Codec.INT.fieldOf("duration").forGetter(Cooldown::duration)
        ).apply(i, Cooldown::new));
    }

    public static final Codec<InnateTechniqueData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("innate_technique").forGetter(d -> Optional.ofNullable(d.innateTechniqueId)),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(d -> d.selectedSlot),
            Codec.unboundedMap(ResourceLocation.CODEC, Cooldown.CODEC).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldowns)
    ).apply(i, (innateTechnique, selected, cooldowns) -> new InnateTechniqueData(innateTechnique.orElse(null), selected, new HashMap<>(cooldowns))));

    @Nullable
    private ResourceLocation innateTechniqueId;
    private int selectedSlot;
    private final Map<ResourceLocation, Cooldown> cooldowns;

    public InnateTechniqueData() {
        this(null, 0, new HashMap<>());
    }

    private InnateTechniqueData(@Nullable ResourceLocation innateTechniqueId, int selectedSlot, Map<ResourceLocation, Cooldown> cooldowns) {
        this.innateTechniqueId = innateTechniqueId;
        this.selectedSlot = selectedSlot;
        this.cooldowns = cooldowns;
    }

    public Optional<ResourceLocation> innateTechniqueId() {
        return Optional.ofNullable(innateTechniqueId);
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public Map<ResourceLocation, Cooldown> cooldowns() {
        return Collections.unmodifiableMap(cooldowns);
    }

    // Mutators are package-private: go through InnateTechniqueService / CastManager.
    void setInnateTechnique(@Nullable ResourceLocation id) {
        this.innateTechniqueId = id;
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
