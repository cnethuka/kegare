package com.cnethuka.kegare.innatetechnique;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * Runtime-only cast state (attachment {@code kegare:cast_state}, never saved, never copied).
 * Holds the one charge/channel in progress plus every active toggle.
 */
public final class CastState {
    /** One running charge, channel or toggle. */
    public static final class Active {
        final Ability ability;
        final CastSlot slot;
        final long startTick;
        int activeTicks;

        Active(Ability ability, CastSlot slot, long startTick) {
            this.ability = ability;
            this.slot = slot;
            this.startTick = startTick;
        }

        public Ability ability() { return ability; }
        public CastSlot slot() { return slot; }
        public long startTick() { return startTick; }
        public int activeTicks() { return activeTicks; }
    }

    @Nullable
    Active casting;
    final Map<ResourceLocation, Active> toggles = new LinkedHashMap<>();
    /** Set during an INSTANT cast's execute: the cast fizzled after paying, so no cooldown starts. */
    boolean noCooldown;

    @Nullable
    public Active casting() {
        return casting;
    }

    public Map<ResourceLocation, Active> toggles() {
        return java.util.Collections.unmodifiableMap(toggles);
    }

    public boolean isIdle() {
        return casting == null && toggles.isEmpty();
    }
}
