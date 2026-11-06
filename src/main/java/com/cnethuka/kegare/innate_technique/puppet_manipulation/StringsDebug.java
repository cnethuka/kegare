package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * DEBUG (op command {@code /kegare strings unmovable <player> <true|false>}): a stand-in for a future immunity. Cancels
 * {@link StringsTargetEvent} for that player. Nothing in the game cancels the event otherwise.
 */
@EventBusSubscriber(modid = Kegare.MODID)
public final class StringsDebug {
    private static final Set<UUID> UNMOVABLE = new HashSet<>();

    private StringsDebug() {}

    public static void setUnmovable(UUID player, boolean on) {
        if (on) UNMOVABLE.add(player);
        else UNMOVABLE.remove(player);
    }

    @SubscribeEvent
    static void onTarget(StringsTargetEvent event) {
        if (UNMOVABLE.contains(event.getTarget().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        UNMOVABLE.clear();
    }
}
