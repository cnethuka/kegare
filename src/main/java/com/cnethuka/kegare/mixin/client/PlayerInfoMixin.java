package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.innate_technique.swap.client.DisguiseClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swap's disguises: a disguised player's skin is the skin of who they look like (body, arm model, cape, and their
 * head in the tab list). Never for the local player's own entry: everyone sees themselves normally.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void kegare$disguisedSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = DisguiseClient.skinFor(getProfile().getId());
        if (skin != null) cir.setReturnValue(skin);
    }
}
