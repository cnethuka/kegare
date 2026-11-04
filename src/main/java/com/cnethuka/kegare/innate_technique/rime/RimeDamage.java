package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Damage type {@code kegare:rime} (data/kegare/damage_type/rime.json). */
public final class RimeDamage {
    public static final ResourceKey<DamageType> RIME = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("rime"));

    private RimeDamage() {}

    /**
     * @param direct the projectile (direct entity)
     * @param caster who cast it (source entity), credited with kills
     */
    public static DamageSource source(Level level, Entity direct, @Nullable Entity caster) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RIME), direct, caster);
    }
}
