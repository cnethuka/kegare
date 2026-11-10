package com.cnethuka.kegare.config;

import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Numbers from {@code kegare-server.toml} formatted for player-facing text, so a description says what the server is set to (its
 * config reaches each client on join). Before that, such as in a menu, the default.
 */
public final class ConfigText {
    private ConfigText() {}

    private static double value(ModConfigSpec.ConfigValue<? extends Number> value) {
        try {
            return value.get().doubleValue();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault().doubleValue();
        }
    }

    /** Whole numbers without a point ("3"), anything else to one place ("1.5"). */
    public static String number(ModConfigSpec.ConfigValue<? extends Number> value) {
        double v = value(value);
        if (Math.abs(v - Math.rint(v)) < 1.0e-9) return Long.toString(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** A share as a percentage ("30%", "5%", "2.5%"). */
    public static String percent(ModConfigSpec.ConfigValue<? extends Number> share) {
        double v = value(share) * 100;
        if (Math.abs(v - Math.rint(v)) < 1.0e-9) return Math.round(v) + "%";
        return String.format(Locale.ROOT, "%.1f%%", v);
    }
}
