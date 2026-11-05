package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import net.minecraft.util.Mth;

/** What a puppet is doing, set for every puppet in range by Directive (which cycles through them in this order). */
public enum PuppetOrder {
    /** Walk after the owner. */
    FOLLOW("follow"),
    /** Stand where it is (and spin). */
    STAY("stay"),
    /** Follow, and attack whoever damages the owner (players or mobs). */
    GUARD("guard"),
    /** Walk to the block the owner was looking at, then stay there. */
    GO("go");

    private final String name;

    PuppetOrder(String name) {
        this.name = name;
    }

    public String serializedName() {
        return name;
    }

    public String translationKey() {
        return "puppet.kegare.order." + name;
    }

    public PuppetOrder next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static PuppetOrder byId(int id) {
        return values()[Mth.clamp(id, 0, values().length - 1)];
    }
}
