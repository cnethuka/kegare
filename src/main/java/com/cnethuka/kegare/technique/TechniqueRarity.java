package com.cnethuka.kegare.technique;

/** Technique rarity. Plain Java (no Minecraft types) so the pity math is unit-testable. */
public enum TechniqueRarity {
    COMMON("common", 0xB8B8B8),
    RARE("rare", 0x55AAFF),
    EPIC("epic", 0xC77DFF),
    LEGENDARY("legendary", 0xFFB733);

    private final String name;
    private final int color;

    TechniqueRarity(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public String serializedName() {
        return name;
    }

    /** RGB text color used for technique names. */
    public int color() {
        return color;
    }

    public String translationKey() {
        return "technique.kegare.rarity." + name;
    }
}
