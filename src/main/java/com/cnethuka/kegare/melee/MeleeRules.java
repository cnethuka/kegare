package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/** Whether someone can use the layer at all right now. */
public final class MeleeRules {
    private static final List<Predicate<ServerPlayer>> BLOCKERS = new CopyOnWriteArrayList<>();

    private MeleeRules() {}

    /** Something a technique does to a player that rules the layer out (possessing: the body isn't theirs to reinforce). */
    public static void addBlocker(Predicate<ServerPlayer> blocker) {
        BLOCKERS.add(blocker);
    }

    static boolean canAct(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && MaliceService.get(player).isBorn() && !CombatHelper.isStunned(player)
                && !isBlocked(player);
    }

    static boolean isBlocked(ServerPlayer player) {
        return BLOCKERS.stream().anyMatch(blocker -> blocker.test(player));
    }
}
