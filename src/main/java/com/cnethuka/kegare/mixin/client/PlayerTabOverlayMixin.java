package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.innate_technique.swap.client.DisguiseClient;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swap's disguises: a disguised player's tab-list name is the name of who they look like (their own entry excepted). */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
    @Shadow
    public abstract Component getNameForDisplay(PlayerInfo playerInfo);

    @Inject(method = "getNameForDisplay", at = @At("HEAD"), cancellable = true)
    private void kegare$disguisedName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        PlayerInfo shown = DisguiseClient.shownInfo(playerInfo.getProfile().getId());
        if (shown != null) cir.setReturnValue(DisguiseClient.resolving(() -> getNameForDisplay(shown)));
    }
}
