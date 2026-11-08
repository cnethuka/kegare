package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.impact.client.DeathBurst;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob a hit killed goes out with the Impact System's death burst instead of vanilla's death smoke (DeathBurst, DESIGN §3.23). */
@Mixin(LivingEntity.class)
public abstract class DeathPoofMixin {
    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void kegare$deathBurst(CallbackInfo ci) {
        if (DeathBurst.replace((LivingEntity) (Object) this)) ci.cancel();
    }
}
