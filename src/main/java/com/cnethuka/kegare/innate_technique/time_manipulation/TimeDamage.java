package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Damage type {@code kegare:time_manipulation} (data/kegare/damage_type/time_manipulation.json): Decelerate's thrown hand. */
public final class TimeDamage {
    public static final ResourceKey<DamageType> TIME = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("time_manipulation"));

    private TimeDamage() {}

    public static DamageSource source(Level level, Entity direct, @Nullable Entity caster) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TIME), direct, caster);
    }
}
