package com.cnethuka.kegare.dominion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player taken into a Dominion's inner space came from (DESIGN §3.35): the level, the spot and which way they faced. Empty when
 * they're not in one.
 */
public record InnerReturn(Optional<ResourceKey<Level>> level, Vec3 pos, float yRot, float xRot) {
    public static final InnerReturn NONE = new InnerReturn(Optional.empty(), Vec3.ZERO, 0f, 0f);
    public static final Codec<InnerReturn> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("level").forGetter(InnerReturn::level),
            Vec3.CODEC.fieldOf("pos").forGetter(InnerReturn::pos),
            Codec.FLOAT.fieldOf("y_rot").forGetter(InnerReturn::yRot),
            Codec.FLOAT.fieldOf("x_rot").forGetter(InnerReturn::xRot)).apply(i, InnerReturn::new));

    public boolean present() {
        return level.isPresent();
    }
}
