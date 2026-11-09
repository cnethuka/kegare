package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.sixeyes.client.SightClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Six Eyes' outlines (DESIGN §3.32) use vanilla's glow pass: crisp, through walls, at every effects setting. Only for a bearer. */
@Mixin(Minecraft.class)
public class SixEyesGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void kegare$sixEyes(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SightClient.outlineColor(entity) != null) cir.setReturnValue(true);
    }
}
