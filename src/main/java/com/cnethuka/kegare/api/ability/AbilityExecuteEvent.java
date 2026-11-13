package com.cnethuka.kegare.api.ability;

import com.cnethuka.kegare.innatetechnique.AbilityContext;

/** Notification around {@code Ability.execute}, inside the cast's {@code CastScope}. Not cancellable. */
public abstract class AbilityExecuteEvent extends AbilityLifecycleEvent {
    private AbilityExecuteEvent(AbilityContext context) {
        super(context);
    }

    /** After all press/release validation and payment, immediately before {@code execute}. */
    public static final class Pre extends AbilityExecuteEvent {
        public Pre(AbilityContext context) {
            super(context);
        }
    }

    /**
     * Immediately after {@code execute} returns successfully. An INSTANT cast which called
     * {@code CastManager.fizzleKeepingCost} is a fizzle, so it does not fire this event.
     */
    public static final class Post extends AbilityExecuteEvent {
        public Post(AbilityContext context) {
            super(context);
        }
    }
}
