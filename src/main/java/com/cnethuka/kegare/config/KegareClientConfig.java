package com.cnethuka.kegare.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences (HUD placement). No gameplay values here. */
public final class KegareClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.IntValue HUD_X;
    public static final ModConfigSpec.IntValue HUD_Y;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("hud");
        HUD_ENABLED = b.define("enabled", true);
        HUD_X = b.comment("HUD offset from the left edge of the screen (GUI pixels).").defineInRange("offsetX", 4, 0, 4000);
        HUD_Y = b.comment("HUD offset from the top edge of the screen (GUI pixels).").defineInRange("offsetY", 4, 0, 4000);
        b.pop();
        SPEC = b.build();
    }

    private KegareClientConfig() {}
}
