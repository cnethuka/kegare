package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.severed.SeveredBody;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Stamina (DESIGN §3.27), for the hunger healing costs, which the food bar charges itself rather than through the player. */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V"))
    private float kegare$healingStamina(float exhaustion, @Local(argsOnly = true) Player player) {
        return SeveredBody.exhaustion(player, exhaustion);
    }
}
