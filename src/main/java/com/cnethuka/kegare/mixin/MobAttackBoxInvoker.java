package com.cnethuka.kegare.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A mob's own melee reach, its own overrides included: Six Eyes measures how close its target is to it (DESIGN §3.32). Read only. */
@Mixin(Mob.class)
public interface MobAttackBoxInvoker {
    @Invoker("getAttackBoundingBox")
    AABB kegare$attackBox();
}
