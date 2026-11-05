package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * On a Puppet Manipulation player ({@code kegare:puppet_owner}, saved, kept on death): the last order Directive gave
 * (the HUD shows it; new puppets take it).
 */
public final class PuppetOwnerData {
    public static final Codec<PuppetOwnerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("order", PuppetOrder.FOLLOW.serializedName()).forGetter(d -> d.order.serializedName())
    ).apply(i, PuppetOwnerData::new));

    PuppetOrder order = PuppetOrder.FOLLOW;

    public PuppetOwnerData() {}

    private PuppetOwnerData(String order) {
        for (PuppetOrder o : PuppetOrder.values()) if (o.serializedName().equals(order)) this.order = o;
    }

    public PuppetOrder order() {
        return order;
    }
}
