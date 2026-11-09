package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.sight.DelayedSight;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Too fast for the eye (DESIGN §3.30): while a player is shown behind where they are, vanilla's movement updates for them reach only the
 * viewers who see the real position (a Six Eyes bearer). Everyone else gets the delayed image from {@link DelayedSight} instead, so the real
 * position never reaches them. Everything else the tracker sends goes to everyone as usual.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityBroadcastMixin {
    @Shadow @Final Entity entity;
    @Shadow @Final private Set<ServerPlayerConnection> seenBy;

    @Inject(method = "broadcast", at = @At("HEAD"), cancellable = true)
    private void kegare$delayedImage(Packet<?> packet, CallbackInfo ci) {
        if (!DelayedSight.isMovement(packet) || !DelayedSight.hidden(entity)) return;
        for (ServerPlayerConnection connection : seenBy) {
            if (DelayedSight.seesReal(connection.getPlayer(), entity)) connection.send(packet);
        }
        ci.cancel();
    }
}
