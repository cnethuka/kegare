package com.cnethuka.kegare.malice;

/**
 * Birth tier of a player's Malice capacity. Plain Java (no Minecraft types) so the roll math is unit-testable.
 * Display names are in the lang file under {@link #translationKey()}.
 */
public enum MaliceTier {
    ZERO("zero"),
    LOW("low"),
    AVERAGE("average"),
    HIGH("high"),
    VAST("vast");

    private final String name;

    MaliceTier(String name) {
        this.name = name;
    }

    public String serializedName() {
        return name;
    }

    public String translationKey() {
        return "malice.kegare.tier." + name;
    }

    public static MaliceTier byName(String name) {
        for (MaliceTier tier : values()) {
            if (tier.name.equals(name)) return tier;
        }
        return AVERAGE;
    }
}
