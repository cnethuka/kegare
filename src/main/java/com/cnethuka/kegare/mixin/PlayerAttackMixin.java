package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.innate_technique.time_manipulation.TimeStops;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player's swing at something frozen in a Standstill (DESIGN §3.26). The hit is held for the resume, so {@code hurt} returns false
 * and vanilla would treat it as a miss, dropping the sweep (and its hits on anything else frozen), the crit, the sounds and the
 * weapon's wear. Here a held hit counts as landed; its knockback waits (TimeStops cancels it on the frozen).
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @WrapOperation(method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean kegare$heldCountsAsLanded(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        TimeStops.heldJustNow(null); // whatever was held before this swing isn't this swing's
        boolean landed = original.call(target, source, amount);
        return TimeStops.heldJustNow(target) || landed;
    }
}
