package com.cnethuka.kegare.mixin;

import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How long until a ranged mob's next shot (witches, drowned with tridents, the wither's main head): Six Eyes reads it (DESIGN §3.32). Read only. */
@Mixin(RangedAttackGoal.class)
public interface RangedAttackGoalAccessor {
    @Accessor("attackTime")
    int kegare$attackTime();
}
