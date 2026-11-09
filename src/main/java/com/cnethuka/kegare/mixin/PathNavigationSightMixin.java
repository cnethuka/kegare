package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.sight.DelayedSight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Too fast for the eye (DESIGN §3.30): a mob paths to where it sees a delayed player, where they were. Bosses see the real position. Every
 * navigation that has its own way to path to an entity (ground, flying, climbing) gets it.
 */
@Mixin({PathNavigation.class, GroundPathNavigation.class, FlyingPathNavigation.class, WallClimberNavigation.class})
public abstract class PathNavigationSightMixin {
    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void kegare$pathToTheImage(Entity entity, int accuracy, CallbackInfoReturnable<Path> cir) {
        Mob mob = ((PathNavigationAccessor) this).kegare$mob();
        if (DelayedSight.misleads(mob, entity)) {
            cir.setReturnValue(((PathNavigation) (Object) this).createPath(BlockPos.containing(DelayedSight.seenAt(mob, entity)), accuracy));
        }
    }
}
