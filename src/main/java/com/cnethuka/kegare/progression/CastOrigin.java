package com.cnethuka.kegare.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * Which player's cast of which ability spawned an entity. {@link CastScope} tags whatever joins the world during a
 * cast, so a projectile or a summon a future technique adds is attributed without any code of its own.
 */
public record CastOrigin(UUID caster, ResourceLocation ability) {
    public static final Codec<CastOrigin> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("caster").forGetter(CastOrigin::caster),
            ResourceLocation.CODEC.fieldOf("ability").forGetter(CastOrigin::ability)
    ).apply(i, CastOrigin::new));

    /** Never read: entities only have this attachment once tagged. */
    public static final CastOrigin NONE = new CastOrigin(new UUID(0, 0), ResourceLocation.withDefaultNamespace("none"));
}
