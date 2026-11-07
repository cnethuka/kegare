package com.cnethuka.kegare.mixin;

import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the tracker believes watchers have the entity: its next relative move is measured from here. */
@Mixin(ServerEntity.class)
public interface ServerEntityAccessor {
    @Accessor("positionCodec")
    VecDeltaCodec kegare$positionCodec();
}
