package com.cnethuka.kegare.technique;

import net.minecraft.resources.ResourceLocation;

/** Convenience base holding an ability's ID and cast type. */
public abstract class BaseAbility implements Ability {
    private final ResourceLocation id;
    private final CastType castType;

    protected BaseAbility(ResourceLocation id, CastType castType) {
        this.id = id;
        this.castType = castType;
    }

    @Override
    public final ResourceLocation id() {
        return id;
    }

    @Override
    public final CastType castType() {
        return castType;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
