package com.cnethuka.kegare.ce;

/**
 * Birth tier of a player's ce capacity. Plain Java (no Minecraft types) so the roll math is unit-testable.
 * Display names are in the lang file under {@link #translationKey()}.
 */
public enum CeTier {
    ZERO("zero"),
    LOW("low"),
    AVERAGE("average"),
    HIGH("high"),
    VAST("vast");

    private final String name;

    CeTier(String name) {
        this.name = name;
    }

    public String serializedName() {
        return name;
    }

    public String translationKey() {
        return "ce.kegare.tier." + name;
    }

    public static CeTier byName(String name) {
        for (CeTier tier : values()) {
            if (tier.name.equals(name)) return tier;
        }
        return AVERAGE;
    }
}
