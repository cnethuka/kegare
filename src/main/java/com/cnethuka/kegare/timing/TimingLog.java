package com.cnethuka.kegare.timing;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /kegare debug spark log <on|off>} (DESIGN §3.8): the numbers behind each timed judgment, to the ops who asked for them and
 * nobody else. Black Spark's tries (§3.28) and Parry's two timings side by side (§3.29).
 */
public final class TimingLog {
    private static final Set<UUID> LISTENING = new HashSet<>();

    private TimingLog() {}

    public static void set(ServerPlayer op, boolean on) {
        if (on) LISTENING.add(op.getUUID());
        else LISTENING.remove(op.getUUID());
    }

    public static boolean any() {
        return !LISTENING.isEmpty();
    }

    public static void send(MinecraftServer server, String line) {
        if (LISTENING.isEmpty()) return;
        for (UUID id : LISTENING) {
            ServerPlayer op = server.getPlayerList().getPlayer(id);
            if (op != null) op.sendSystemMessage(Component.literal(line));
        }
    }

    public static void clear() {
        LISTENING.clear();
    }
}
