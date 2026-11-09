package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.sight.DelayedSight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Too fast for the eye (DESIGN §3.30): a mob turns to where it sees a delayed player, where they were. Bosses see the real position. */
@Mixin(LookControl.class)
public abstract class LookControlSightMixin {
    @Shadow @Final protected Mob mob;

    @Shadow public abstract void setLookAt(double x, double y, double z, float deltaYaw, float deltaPitch);

    @Inject(method = "setLookAt(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("HEAD"), cancellable = true)
    private void kegare$lookAtTheImage(Entity entity, float deltaYaw, float deltaPitch, CallbackInfo ci) {
        if (!DelayedSight.misleads(mob, entity)) return;
        Vec3 at = DelayedSight.seenAt(mob, entity);
        setLookAt(at.x, at.y + entity.getEyeHeight(), at.z, deltaYaw, deltaPitch);
        ci.cancel();
    }
}
