package com.cnethuka.kegare.timing;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Sub-tick timing's numbers (DESIGN §3.29): {@code [timing]} of {@code kegare-server.toml}. */
public final class TimingConfig {
    public static ModConfigSpec.DoubleValue TOLERANCE_MS;
    public static ModConfigSpec.DoubleValue PLAUSIBLE_MS;
    public static ModConfigSpec.DoubleValue SYNC_SECONDS;
    public static ModConfigSpec.BooleanValue PARRY_SUB_TICK;

    private TimingConfig() {}

    public static void define(ModConfigSpec.Builder b) {
        b.comment("Sub-tick timing: inputs the client stamps with its own clock, mapped to the server's, so timing can be judged finer than",
                "a 50 ms tick. A stamp is never taken as earlier than (arrival - ping - toleranceMs), nor later than its arrival.").push("timing");
        TOLERANCE_MS = b.comment("How much earlier than its arrival less the ping a stamp may still be taken as, in ms (jitter).")
                .defineInRange("toleranceMs", 15.0, 0.0, 1000.0);
        PLAUSIBLE_MS = b.comment("A stamp further than this from its arrival, in ms (or with no clock estimate yet), isn't believed at all: its",
                "arrival time is used instead.").defineInRange("plausibleMs", 1000.0, 50.0, 60_000.0);
        SYNC_SECONDS = b.comment("How often each player's clock is checked against the server's (a ping and its answer).")
                .defineInRange("clockSyncSeconds", 2.0, 0.25, 60.0);
        PARRY_SUB_TICK = b.comment("Judge Parry's perfect window from the defender's press against the attacker's swing, both stamped and mapped to",
                "server time, instead of from when each reached the server in ticks. Off: it was worse in some cases (DESIGN §3.29).")
                .define("parrySubTick", false);
        b.pop();
    }
}
