package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import net.minecraft.util.Mth;

/**
 * How much Malice a puppet was charged with (Puppet Creation's charge). It sets the puppet's cost and health
 * ({@code [puppet_manipulation.puppet_creation.<tier>]}), how brightly it glows, and how hard Curio Burst hits.
 */
public enum PuppetTier {
    /** Released before the half charge: faint glow. */
    TAP("tap"),
    /** Released before full charge: normal glow. */
    HALF("half"),
    /** Fully charged: bright glow and an outline. */
    FULL("full");

    private final String name;

    PuppetTier(String name) {
        this.name = name;
    }

    public String serializedName() {
        return name;
    }

    public String translationKey() {
        return "puppet.kegare.tier." + name;
    }

    public double cost() {
        return PuppetConfig.TIER_COST[ordinal()].get();
    }

    public float health() {
        return PuppetConfig.TIER_HEALTH[ordinal()].get().floatValue();
    }

    public static PuppetTier byId(int id) {
        return values()[Mth.clamp(id, 0, values().length - 1)];
    }

    /** The tier for a charge (0..1 of the full charge). */
    public static PuppetTier forCharge(float charge) {
        if (charge >= 1f - 1.0e-4f) return FULL;
        double full = PuppetConfig.CREATION_FULL_CHARGE.get();
        double half = Math.min(PuppetConfig.CREATION_HALF_CHARGE.get(), full);
        return charge * full >= half - 1.0e-4 ? HALF : TAP;
    }
}
