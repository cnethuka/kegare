package com.cnethuka.kegare.mixin;

import com.cnethuka.kegare.sight.DelayedSight;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Too fast for the eye (DESIGN §3.30): someone who starts watching a player while they're shown behind where they are is told the delayed
 * position from the first packet, never the real one.
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityPairingMixin {
    @WrapOperation(method = "sendPairingData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getAddEntityPacket(Lnet/minecraft/server/level/ServerEntity;)Lnet/minecraft/network/protocol/Packet;"))
    private Packet<ClientGamePacketListener> kegare$delayedSpawn(Entity entity, ServerEntity self, Operation<Packet<ClientGamePacketListener>> original,
                                                                @Local(argsOnly = true) ServerPlayer viewer) {
        Packet<ClientGamePacketListener> delayed = DelayedSight.spawnPacket(entity, viewer);
        return delayed != null ? delayed : original.call(entity, self);
    }
}
