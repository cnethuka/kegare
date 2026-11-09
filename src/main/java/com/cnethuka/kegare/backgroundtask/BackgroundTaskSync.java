package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.progression.Unlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps each player's own client up to date on their Background Tasks: sent to them alone, when it changes. */
public final class BackgroundTaskSync {
    /** What each player was last sent (the only recipient ever is the player themself). */
    private static final Map<UUID, BackgroundSyncPayload> SENT = new HashMap<>();

    private BackgroundTaskSync() {}

    static BackgroundSyncPayload build(ServerPlayer player) {
        int count = BackgroundTaskService.slotCount(player);
        if (count == 0) return BackgroundSyncPayload.NONE; // Severed: no Background view
        List<BackgroundSyncPayload.SlotView> slots = new ArrayList<>();
        BackgroundTaskData data = BackgroundTaskService.data(player);
        for (int i = 0; i < count; i++) {
            BackgroundTaskData.Slot s = data.slot(i);
            boolean paused = s.task().map(id -> data.paused.contains(id)).orElse(false);
            slots.add(new BackgroundSyncPayload.SlotView(s.task(), s.on(), paused));
        }
        List<BackgroundSyncPayload.TaskView> tasks = new ArrayList<>();
        for (BackgroundTask task : BackgroundTasks.all()) {
            tasks.add(new BackgroundSyncPayload.TaskView(task.id(), Unlocks.has(player, task.id()), task.drainPerSecond(player)));
        }
        return new BackgroundSyncPayload(count, slots, tasks, "");
    }

    static void sync(ServerPlayer player) {
        BackgroundSyncPayload now = build(player);
        if (now.equals(SENT.get(player.getUUID()))) return;
        SENT.put(player.getUUID(), now);
        PacketDistributor.sendToPlayer(player, now);
    }

    /** The answer to a request: fresh state, and the refusal on the action bar and the screen's status line. */
    static void answer(ServerPlayer player, BackgroundTaskResult result) {
        BackgroundSyncPayload now = build(player);
        SENT.put(player.getUUID(), now);
        String key = result.translationKey();
        if (key != null) player.displayClientMessage(Component.translatable(key), true);
        PacketDistributor.sendToPlayer(player, key == null ? now : now.withRefusal(key));
    }

    /** Who has been sent Background Task state (tests: only ever the players themselves). */
    static Map<UUID, BackgroundSyncPayload> sent() {
        return SENT;
    }

    static void forget(UUID player) {
        SENT.remove(player);
    }

    static void clear() {
        SENT.clear();
    }
}
