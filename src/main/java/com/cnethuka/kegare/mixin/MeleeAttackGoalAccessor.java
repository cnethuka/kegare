package com.cnethuka.kegare.mixin;

import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How long until a melee mob's next swing: Six Eyes reads it a moment before it comes (DESIGN §3.32). Read only. */
@Mixin(MeleeAttackGoal.class)
public interface MeleeAttackGoalAccessor {
    @Accessor("ticksUntilNextAttack")
    int kegare$ticksUntilNextAttack();
}
