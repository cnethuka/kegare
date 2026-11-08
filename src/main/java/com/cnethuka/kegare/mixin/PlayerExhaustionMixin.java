package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.severed.SeveredBody;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Stamina (DESIGN §3.27): a Severed player feels less of the hunger from sprinting, jumping, attacking, mining and taking hits. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float kegare$stamina(float exhaustion) {
        return SeveredBody.exhaustion((Player) (Object) this, exhaustion);
    }
}
