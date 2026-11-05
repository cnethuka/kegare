package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * On a Puppet Manipulation player ({@code kegare:puppet_owner}, saved, kept on death): the last order Directive gave
 * and, for Go, where. The HUD shows it, and new puppets take it.
 */
public final class PuppetOwnerData {
    public static final Codec<PuppetOwnerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("order", PuppetOrder.FOLLOW.serializedName()).forGetter(d -> d.order.serializedName()),
            BlockPos.CODEC.optionalFieldOf("go_target").forGetter(d -> Optional.ofNullable(d.goTarget))
    ).apply(i, PuppetOwnerData::new));

    PuppetOrder order = PuppetOrder.FOLLOW;
    @Nullable
    BlockPos goTarget;

    public PuppetOwnerData() {}

    private PuppetOwnerData(String order, Optional<BlockPos> goTarget) {
        for (PuppetOrder o : PuppetOrder.values()) if (o.serializedName().equals(order)) this.order = o;
        this.goTarget = goTarget.orElse(null);
    }

    public PuppetOrder order() {
        return order;
    }

    @Nullable
    public BlockPos goTarget() {
        return goTarget;
    }
}
