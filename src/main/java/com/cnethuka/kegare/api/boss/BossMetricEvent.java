package com.cnethuka.kegare.api.boss;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Posted after a custom metric changes; the encounter snapshot already contains its new value. */
public final class BossMetricEvent extends Event {
    private final BossFightSnapshot encounter;
    private final ResourceLocation metricId;
    private final Number value;

    public BossMetricEvent(BossFightSnapshot encounter, ResourceLocation metricId, Number value) {
        this.encounter = encounter;
        this.metricId = metricId;
        this.value = value;
    }

    public BossFightSnapshot getEncounter() { return encounter; }
    public ResourceLocation getMetricId() { return metricId; }
    public Number getValue() { return value; }
}
