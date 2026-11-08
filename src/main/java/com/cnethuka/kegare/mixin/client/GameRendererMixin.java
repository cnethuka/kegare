package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.impact.client.ScreenFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Impact System's whole-screen step (DESIGN §3.23): once the world, your hand, the glowing outlines and vanilla's own post effect
 * are drawn, and before the HUD. Only reached while a world is drawn.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0))
    private void kegare$afterLevel(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ScreenFx.afterLevel(deltaTracker);
    }
}
