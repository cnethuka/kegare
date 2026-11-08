package com.cnethuka.kegare.mixin.client;

import com.cnethuka.kegare.innate_technique.time_manipulation.client.TimeStopClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * An entity frozen in a Standstill is drawn at the end of its last tick every frame (TimeStopClient, DESIGN §3.26): its tick is skipped,
 * so anything drawn from its age plus the partial tick would otherwise shiver back and forth each tick.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"), index = 4)
    private float kegare$frozenPartialTick(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack,
                                           MultiBufferSource buffers) {
        return TimeStopClient.partialTick(entity, partialTick);
    }
}
