package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.sight.DelayedSight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Too fast for the eye (DESIGN §3.30): a mob swings at where it sees a delayed player (where they were), and the swing lands only if the
 * player is really there too: hits are judged against the real position, and no reach is widened. Bosses see the real position.
 */
@Mixin(Mob.class)
public abstract class MobSightMixin {
    @Shadow protected abstract net.minecraft.world.phys.AABB getAttackBoundingBox();

    @Inject(method = "isWithinMeleeAttackRange", at = @At("HEAD"), cancellable = true)
    private void kegare$swingAtTheImage(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (DelayedSight.misleads(self, target)) cir.setReturnValue(getAttackBoundingBox().intersects(DelayedSight.seenHitbox(self, target)));
    }

    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void kegare$missWhereTheyWere(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (target instanceof LivingEntity living && DelayedSight.misleads(self, living) && !getAttackBoundingBox().intersects(living.getHitbox())) {
            cir.setReturnValue(false); // swung at where they were; they've gone
        }
    }
}
