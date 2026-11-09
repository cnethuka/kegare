package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.registry.KegareAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Health a player has healed in total with their own channelled Reverse Malice Technique, on themselves or another (attachment
 * {@code kegare:rmt_healed}, saved, kept on death and through rerolls): what unlocks Background RMT (DESIGN §3.31).
 */
public final class RmtHealed {
    public static final Codec<RmtHealed> CODEC = Codec.DOUBLE.xmap(RmtHealed::new, d -> d.healed);

    double healed;

    public RmtHealed() {}

    RmtHealed(double healed) {
        this.healed = healed;
    }

    public static double get(Player player) {
        return player.getData(KegareAttachments.RMT_HEALED).healed;
    }

    /** Adds to the count; when it crosses what unlocks Background RMT, the unlock is checked at once. */
    static void add(ServerPlayer player, double amount) {
        if (amount <= 0) return;
        RmtHealed data = player.getData(KegareAttachments.RMT_HEALED);
        double before = data.healed;
        data.healed += amount;
        double needed = BackgroundTaskConfig.RMT_HEAL_TO_UNLOCK.get();
        if (before < needed && data.healed >= needed) com.cnethuka.kegare.progression.Unlocks.evaluate(player);
    }

    /** Admin: sets the count, and checks the unlock. */
    public static void set(ServerPlayer player, double amount) {
        player.getData(KegareAttachments.RMT_HEALED).healed = Math.max(0, amount);
        com.cnethuka.kegare.progression.Unlocks.evaluate(player);
    }
}
