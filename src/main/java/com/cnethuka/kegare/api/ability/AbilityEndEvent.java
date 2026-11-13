package com.cnethuka.kegare.api.ability;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.EndReason;

/** Notification after an ability's end hook, inside its {@code CastScope}. Not cancellable. */
public abstract class AbilityEndEvent extends AbilityLifecycleEvent {
    private final EndReason reason;

    private AbilityEndEvent(AbilityContext context, EndReason reason) {
        super(context);
        this.reason = reason;
    }

    public final EndReason getReason() {
        return reason;
    }

    /** Immediately after {@code Ability.onEnd} returns successfully. */
    public static final class Post extends AbilityEndEvent {
        public Post(AbilityContext context, EndReason reason) {
            super(context, reason);
        }
    }
}
