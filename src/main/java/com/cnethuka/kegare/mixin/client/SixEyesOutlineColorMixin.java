package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.sixeyes.client.SightClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The outline's color is read off the entity's team color: what the eyes see wins (their Malice color, a pulse's white). Client only. */
@Mixin(Entity.class)
public abstract class SixEyesOutlineColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void kegare$sixEyes(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide) return;
        Integer color = SightClient.outlineColor(self);
        if (color != null) cir.setReturnValue(color);
    }
}
