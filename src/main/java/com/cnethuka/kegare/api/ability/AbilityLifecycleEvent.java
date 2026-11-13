package com.cnethuka.kegare.api.ability;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import net.neoforged.bus.api.Event;

/** Base for the read-only ability lifecycle events posted on {@code NeoForge.EVENT_BUS}. */
public abstract class AbilityLifecycleEvent extends Event {
    private final AbilityContext context;

    protected AbilityLifecycleEvent(AbilityContext context) {
        this.context = context;
    }

    /** The cast's immutable context. Its player and world remain normal live server objects. */
    public final AbilityContext getContext() {
        return context;
    }
}
