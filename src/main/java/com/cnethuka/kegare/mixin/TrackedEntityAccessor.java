package com.cnethuka.kegare.mixin;

import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An entity's tracker: what it sends watchers lives in its {@link ServerEntity}. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    @Accessor("serverEntity")
    ServerEntity kegare$serverEntity();

    /** Who's watching it (too fast for the eye, DESIGN §3.30, sends each of them its own image). */
    @Accessor("seenBy")
    java.util.Set<net.minecraft.server.network.ServerPlayerConnection> kegare$seenBy();
}
