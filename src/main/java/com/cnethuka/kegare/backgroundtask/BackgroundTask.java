package com.cnethuka.kegare.backgroundtask;

import com.cnethuka.kegare.progression.UnlockRequirements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A Background Task (DESIGN §3.31): something a sorcerer keeps running without thinking, separate from the ability bar. Weaker than
 * its ability on the bar, but it takes no bar slot and leaves the hands free. Each task has an ID, a display name and description
 * ({@code background_task.<namespace>.<path>} and {@code .desc}, the description filled from {@link #descriptionArgs}), an icon (GUI sprite
 * {@code <namespace>:background/<path>}), its unlock (an {@link com.cnethuka.kegare.progression.Unlocks} entry under the same ID, its
 * requirement shown as {@code background_task.<namespace>.<path>.unlock}), its Malice drain and its effect. A new task subclasses this and
 * registers itself ({@link BackgroundTasks#register}); the system needs no change.
 */
public abstract class BackgroundTask {
    private final ResourceLocation id;

    protected BackgroundTask(ResourceLocation id) {
        this.id = id;
    }

    public final ResourceLocation id() {
        return id;
    }

    /** What earns it: registered in the Unlocks framework under {@link #id()}. */
    public abstract UnlockRequirements requirements();

    /** Malice a second while it runs (not while it's paused). */
    public abstract double drainPerSecond(ServerPlayer player);

    /** Paused for now: it pays nothing and does nothing, and resumes on its own. {@code wasPaused}: whether it was paused last tick. */
    public boolean paused(ServerPlayer player, boolean wasPaused) {
        return false;
    }

    /** Every tick it runs (paid for, not paused). */
    public void tick(ServerPlayer player) {}

    /** It stopped running: switched off, taken out of its slot, paused, or its slot went. */
    public void stopped(ServerPlayer player) {}

    public Object[] descriptionArgs() {
        return new Object[0];
    }

    public final String translationKey() {
        return "background_task." + id.getNamespace() + "." + id.getPath();
    }

    public final Component name() {
        return Component.translatable(translationKey());
    }

    public final Component description() {
        return Component.translatable(translationKey() + ".desc", descriptionArgs());
    }

    /** Its requirement, shown with a padlock while it's locked. */
    public final Component requirement() {
        return Component.translatable(translationKey() + ".unlock");
    }

    public final ResourceLocation icon() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "background/" + id.getPath());
    }
}
