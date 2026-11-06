package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

/**
 * "This code is running on behalf of this player's cast of this ability." {@code CastManager} runs every execute, tick
 * and end inside one, which is what lets a hit, an effect or a spawned entity be traced back to the ability behind it.
 * Server thread only.
 */
public final class CastScope {
    private record Frame(ServerPlayer player, Ability ability) {}

    private static final Deque<Frame> STACK = new ArrayDeque<>();

    private CastScope() {}

    public static void run(ServerPlayer player, Ability ability, Runnable body) {
        STACK.push(new Frame(player, ability));
        try {
            body.run();
        } finally {
            STACK.pop();
        }
    }

    /** The innermost cast this player has running right now, if any. */
    @Nullable
    public static Ability current(ServerPlayer player) {
        for (Frame frame : STACK) if (frame.player == player) return frame.ability;
        return null;
    }

    /** The innermost cast anyone has running right now. */
    @Nullable
    public static ServerPlayer player() {
        Frame frame = STACK.peek();
        return frame == null ? null : frame.player;
    }

    @Nullable
    public static Ability ability() {
        Frame frame = STACK.peek();
        return frame == null ? null : frame.ability;
    }
}
