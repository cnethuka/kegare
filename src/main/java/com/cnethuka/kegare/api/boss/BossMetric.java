package com.cnethuka.kegare.api.boss;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** A namespaced Long or Double custom encounter metric. Equality is by ID and numeric type. */
public final class BossMetric<T extends Number> {
    private final ResourceLocation id;
    private final Class<T> valueType;

    private BossMetric(ResourceLocation id, Class<T> valueType) {
        this.id = Objects.requireNonNull(id);
        this.valueType = valueType;
    }

    public static BossMetric<Long> longMetric(ResourceLocation id) {
        return new BossMetric<>(id, Long.class);
    }

    public static BossMetric<Double> doubleMetric(ResourceLocation id) {
        return new BossMetric<>(id, Double.class);
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<T> valueType() {
        return valueType;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof BossMetric<?> that && id.equals(that.id) && valueType.equals(that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, valueType);
    }

    @Override
    public String toString() {
        return id + " (" + valueType.getSimpleName() + ")";
    }
}
