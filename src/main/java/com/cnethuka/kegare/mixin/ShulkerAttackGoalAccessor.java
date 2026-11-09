package com.cnethuka.kegare.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How long until a shulker's next bullet: Six Eyes reads it (DESIGN §3.32). Read only. */
@Mixin(targets = "net.minecraft.world.entity.monster.Shulker$ShulkerAttackGoal")
public interface ShulkerAttackGoalAccessor {
    @Accessor("attackTime")
    int kegare$attackTime();
}
