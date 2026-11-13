package com.cnethuka.kegare.api.ability;

import com.cnethuka.kegare.innatetechnique.AbilityContext;

/** Notification around a charge, channel or toggle tick, inside its {@code CastScope}. Not cancellable. */
public abstract class AbilityTickEvent extends AbilityLifecycleEvent {
    private AbilityTickEvent(AbilityContext context) {
        super(context);
    }

    /** Immediately after {@code Ability.tick} returns successfully. */
    public static final class Post extends AbilityTickEvent {
        public Post(AbilityContext context) {
            super(context);
        }
    }
}
