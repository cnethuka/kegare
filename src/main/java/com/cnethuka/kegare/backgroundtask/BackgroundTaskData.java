package com.cnethuka.kegare.backgroundtask;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's Background Task slots (attachment {@code kegare:background_tasks}): saved, kept on death and through rerolls. Always
 * {@link BackgroundTaskService#MAX_SLOTS} entries, so a task in the third slot stays saved while they don't have it (DESIGN §3.31).
 */
public final class BackgroundTaskData {
    /** What sits in one slot, and whether it's switched on. */
    public record Slot(Optional<ResourceLocation> task, boolean on) {
        public static final Slot EMPTY = new Slot(Optional.empty(), false);
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("task").forGetter(Slot::task),
                Codec.BOOL.optionalFieldOf("on", false).forGetter(Slot::on)).apply(i, Slot::new));
    }

    public static final Codec<BackgroundTaskData> CODEC = Slot.CODEC.listOf().xmap(BackgroundTaskData::new, d -> List.copyOf(d.slots));

    final List<Slot> slots;
    /** Runtime only: the tasks paused right now (resumed on their own). */
    final Set<ResourceLocation> paused = new HashSet<>();
    /** Runtime only: the tasks that ran last tick. */
    final Set<ResourceLocation> ran = new HashSet<>();

    public BackgroundTaskData() {
        this(List.of());
    }

    BackgroundTaskData(List<Slot> saved) {
        slots = new ArrayList<>(saved);
        while (slots.size() < BackgroundTaskService.MAX_SLOTS) slots.add(Slot.EMPTY);
        while (slots.size() > BackgroundTaskService.MAX_SLOTS) slots.remove(slots.size() - 1);
    }

    public Slot slot(int index) {
        return slots.get(index);
    }
}
