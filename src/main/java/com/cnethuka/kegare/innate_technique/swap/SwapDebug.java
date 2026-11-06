package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * DEBUG (op command {@code /kegare debug swap unswappable <player> <on>}): a stand-in for a future immunity.
 * Cancels {@link SwapTargetEvent} for swaps that target a marked player, through the same event any real immunity
 * would use. Runtime only; nothing is saved.
 */
@EventBusSubscriber(modid = Kegare.MODID)
public final class SwapDebug {
    private static final Set<UUID> UNSWAPPABLE = new HashSet<>();

    private SwapDebug() {}

    public static void setUnswappable(UUID player, boolean on) {
        if (on) UNSWAPPABLE.add(player);
        else UNSWAPPABLE.remove(player);
    }

    public static boolean isUnswappable(UUID player) {
        return UNSWAPPABLE.contains(player);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onSwapTarget(SwapTargetEvent event) {
        if (UNSWAPPABLE.contains(event.getTarget().getUUID())) event.setCanceled(true);
    }

    static void clear() {
        UNSWAPPABLE.clear();
    }
}
