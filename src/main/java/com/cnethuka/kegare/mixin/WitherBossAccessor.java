package com.cnethuka.kegare.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** When the wither's two side heads fire next (the game tick): Six Eyes reads it (DESIGN §3.32). Read only: bosses are never disabled. */
@Mixin(WitherBoss.class)
public interface WitherBossAccessor {
    @Accessor("nextHeadUpdate")
    int[] kegare$nextHeadUpdate();
}
