package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.domain.DomainBounds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A closed Domain's barrier stops moves at it (DESIGN §3.35): the move the game worked out is cut where it meets the shell. */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void kegare$stopAtTheBarrier(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 limited = DomainBounds.limit((Entity) (Object) this, cir.getReturnValue());
        if (limited != cir.getReturnValue()) cir.setReturnValue(limited);
    }
}
