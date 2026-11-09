package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Unlocks;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.TraitService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Background Tasks' slots and running (DESIGN §3.31). Every Malice user has {@link #SLOTS}; a Six Eyes bearer has
 * {@link #SIX_EYES_SLOTS}, from the start, because of how much processing the eyes can take. Losing the eyes switches off the task in
 * the third slot (it stays there, saved, for if they get the eyes again); gaining them gives the third slot at once. Severed players
 * have none. Which task sits in a slot changes only out of a fight, like the loadout; switching a slotted task on or off works any time.
 * <p>
 * A slotted task that's on, unlocked and in a slot they have <em>runs</em> unless it's paused: its own rule
 * ({@link BackgroundTask#paused}), or it can't pay its drain this tick. A paused task pays nothing, does nothing and resumes on its own.
 */
public final class BackgroundTaskService {
    /** Slots saved per player (the most anyone can have). */
    public static final int MAX_SLOTS = 3;
    public static final int SLOTS = 2;
    public static final int SIX_EYES_SLOTS = 3;

    private BackgroundTaskService() {}

    static BackgroundTaskData data(Player player) {
        return player.getData(KegareAttachments.BACKGROUND_TASKS);
    }

    /** How many slots they have: 0 Severed, 3 bearing the Six Eyes, else 2. Private: only their own Sorcerer screen shows it. */
    public static int slotCount(Player player) {
        if (MaliceService.isSevered(player)) return 0;
        return TraitService.has(player, KegareTraits.SIX_EYES.get()) ? SIX_EYES_SLOTS : SLOTS;
    }

    public static BackgroundTaskData.Slot slot(Player player, int index) {
        return data(player).slot(index);
    }

    /** Puts a task in a slot (or empties it, {@code task} null). A task already in another slot moves, swapping with what was here. */
    public static BackgroundTaskResult set(ServerPlayer player, int slot, @Nullable ResourceLocation task) {
        if (MaliceService.isSevered(player)) return BackgroundTaskResult.SEVERED;
        if (slot < 0 || slot >= slotCount(player)) return BackgroundTaskResult.BAD_SLOT;
        if (CombatHelper.isInCombat(player)) return BackgroundTaskResult.IN_COMBAT;
        BackgroundTaskData data = data(player);
        if (task == null) {
            data.slots.set(slot, BackgroundTaskData.Slot.EMPTY);
            return BackgroundTaskResult.OK;
        }
        if (BackgroundTasks.find(task).isEmpty()) return BackgroundTaskResult.UNKNOWN;
        if (!Unlocks.has(player, task)) return BackgroundTaskResult.LOCKED;
        BackgroundTaskData.Slot here = data.slots.get(slot);
        if (here.task().filter(task::equals).isPresent()) return BackgroundTaskResult.OK;
        for (int i = 0; i < data.slots.size(); i++) {
            if (i != slot && data.slots.get(i).task().filter(task::equals).isPresent()) {
                data.slots.set(i, i < slotCount(player) ? here : BackgroundTaskData.Slot.EMPTY);
            }
        }
        data.slots.set(slot, new BackgroundTaskData.Slot(Optional.of(task), true)); // placed means on
        return BackgroundTaskResult.OK;
    }

    /** Switches a slot's task on or off: any time, mid-fight included. */
    public static BackgroundTaskResult setOn(ServerPlayer player, int slot, boolean on) {
        if (MaliceService.isSevered(player)) return BackgroundTaskResult.SEVERED;
        if (slot < 0 || slot >= slotCount(player)) return BackgroundTaskResult.BAD_SLOT;
        BackgroundTaskData data = data(player);
        BackgroundTaskData.Slot here = data.slots.get(slot);
        if (here.task().isEmpty()) return BackgroundTaskResult.EMPTY_SLOT;
        if (on && !Unlocks.has(player, here.task().get())) return BackgroundTaskResult.LOCKED;
        data.slots.set(slot, new BackgroundTaskData.Slot(here.task(), on));
        return BackgroundTaskResult.OK;
    }

    /** Slotted in a slot they have, on and unlocked: it runs unless paused. */
    public static boolean isActive(Player player, ResourceLocation task) {
        if (MaliceService.isSevered(player)) return false;
        BackgroundTaskData data = data(player);
        int count = slotCount(player);
        for (int i = 0; i < count; i++) {
            BackgroundTaskData.Slot s = data.slots.get(i);
            if (s.on() && s.task().filter(task::equals).isPresent()) return Unlocks.has(player, task);
        }
        return false;
    }

    /** Running right now (this tick): active, paid for and not paused. */
    public static boolean isRunning(Player player, ResourceLocation task) {
        return data(player).ran.contains(task);
    }

    public static boolean isPaused(Player player, ResourceLocation task) {
        return isActive(player, task) && data(player).paused.contains(task);
    }

    /** Every tick, on the server: what runs pays and does its work; what stopped is told so. */
    public static void tick(ServerPlayer player) {
        BackgroundTaskData data = data(player);
        int count = slotCount(player);
        // Slots they no longer have (the eyes lost): the task stays, switched off.
        for (int i = count; i < data.slots.size(); i++) {
            BackgroundTaskData.Slot s = data.slots.get(i);
            if (s.on()) data.slots.set(i, new BackgroundTaskData.Slot(s.task(), false));
        }
        Set<ResourceLocation> ran = new java.util.LinkedHashSet<>();
        Set<ResourceLocation> paused = new HashSet<>();
        for (int i = 0; i < count; i++) {
            BackgroundTaskData.Slot s = data.slots.get(i);
            if (!s.on() || s.task().isEmpty() || !Unlocks.has(player, s.task().get())) continue;
            BackgroundTask task = BackgroundTasks.find(s.task().get()).orElse(null);
            if (task == null) continue;
            boolean isPaused = task.paused(player, data.paused.contains(task.id()));
            if (!isPaused) {
                double cost = task.drainPerSecond(player) / 20.0;
                if (cost > 0 && !MaliceService.trySpend(player, cost)) isPaused = true;
            }
            if (isPaused) paused.add(task.id());
            else ran.add(task.id());
        }
        // What runs this tick is known before any task acts, so isRunning is right inside tick and stopped.
        Set<ResourceLocation> before = new HashSet<>(data.ran);
        data.ran.clear();
        data.ran.addAll(ran);
        data.paused.clear();
        data.paused.addAll(paused);
        for (ResourceLocation id : before) {
            if (!ran.contains(id)) BackgroundTasks.find(id).ifPresent(t -> t.stopped(player));
        }
        for (ResourceLocation id : ran) BackgroundTasks.find(id).ifPresent(t -> t.tick(player));
        BackgroundTaskSync.sync(player);
    }

    /** Admin and tests: every slot emptied (saved too). */
    public static void reset(ServerPlayer player) {
        BackgroundTaskData data = data(player);
        Set<ResourceLocation> before = new HashSet<>(data.ran);
        data.ran.clear();
        data.paused.clear();
        for (ResourceLocation id : before) BackgroundTasks.find(id).ifPresent(t -> t.stopped(player));
        for (int i = 0; i < data.slots.size(); i++) data.slots.set(i, BackgroundTaskData.Slot.EMPTY);
    }
}
