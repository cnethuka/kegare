package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.progression.Unlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Every Background Task, in the order they're shown (DESIGN §3.31). Registering one registers its unlock too. */
public final class BackgroundTasks {
    private static final Map<ResourceLocation, BackgroundTask> TASKS = new LinkedHashMap<>();

    private BackgroundTasks() {}

    public static <T extends BackgroundTask> T register(T task) {
        if (TASKS.putIfAbsent(task.id(), task) != null) throw new IllegalArgumentException("Background Task registered twice: " + task.id());
        Unlocks.register(task.id(), task.requirements());
        return task;
    }

    /** For tests, whose tasks mustn't outlive them: the task and its unlock entry go. */
    static void unregister(ResourceLocation id) {
        if (TASKS.remove(id) != null) Unlocks.unregister(id);
    }

    public static List<BackgroundTask> all() {
        return Collections.unmodifiableList(new ArrayList<>(TASKS.values()));
    }

    public static Optional<BackgroundTask> find(ResourceLocation id) {
        return Optional.ofNullable(TASKS.get(id));
    }
}
