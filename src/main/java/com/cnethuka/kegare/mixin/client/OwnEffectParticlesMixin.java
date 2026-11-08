package com.cnethuka.kegare.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Your own effect particles (the swirls of Slowness, Speed and the rest) aren't spawned while you look out of your own eyes: they
 * rise round your body, which in first person is round your camera, so they drift right across the view. Rime's Slowness made them
 * a cloud of pale blue swirls in the middle of a fight. Everyone else still sees yours (they show who's slowed), and so do you in
 * third person.
 */
@Mixin(LivingEntity.class)
public abstract class OwnEffectParticlesMixin {
    @WrapWithCondition(method = "tickEffects",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private boolean kegare$notInYourOwnEyes(Level level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        Minecraft mc = Minecraft.getInstance();
        return (Object) this != mc.player || !mc.options.getCameraType().isFirstPerson();
    }
}
