package com.cnethuka.kegare.innatetechnique;

/** Innate technique rarity. Plain Java (no Minecraft types) so the pity math is unit-testable. */
public enum InnateTechniqueRarity {
    COMMON("common", 0xB8B8B8),
    RARE("rare", 0x55AAFF),
    EPIC("epic", 0xC77DFF),
    LEGENDARY("legendary", 0xFFB733);

    private final String name;
    private final int color;

    InnateTechniqueRarity(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public String serializedName() {
        return name;
    }

    /** RGB text color used for innate technique names. */
    public int color() {
        return color;
    }

    public String translationKey() {
        return "innate_technique.kegare.rarity." + name;
    }
}
