package com.cnethuka.kegare.api.boss;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** A timing view of the current attack callback. Boss, target and world remain live server objects. */
public interface BossAttackContext {
    ServerLevel level();

    LivingEntity boss();

    /** Selected by the addon when the attack started. The framework never chooses or invalidates it. */
    @Nullable
    LivingEntity target();

    BossFight encounter();

    ResourceLocation attackId();

    UUID attackInstanceId();

    long gameTime();

    /** Zero on the attack's first tick. */
    long attackTicks();

    /** Zero on the current state's first tick. */
    long stateTicks();

    BossAttackState state();

    /** Tags a projectile, summon or other spawned entity for delayed outgoing-damage attribution. */
    <T extends Entity> T markOrigin(T entity);
}
