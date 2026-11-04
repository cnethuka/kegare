package com.cnethuka.kegare.innatetechnique.visual;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * What an ability looks like, per {@link VisualPhase}. Purely cosmetic: the server sends these to clients and never
 * waits on them. Declared by {@link com.cnethuka.kegare.innatetechnique.Ability#visuals()}.
 */
public final class AbilityVisuals {
    public static final AbilityVisuals NONE = new AbilityVisuals(new EnumMap<>(VisualPhase.class));

    private final Map<VisualPhase, Visual> phases;

    private AbilityVisuals(Map<VisualPhase, Visual> phases) {
        this.phases = Collections.unmodifiableMap(phases);
    }

    public Visual get(VisualPhase phase) {
        return phases.getOrDefault(phase, Visual.NONE);
    }

    /** Every effect ID used, for preloading on the client. */
    public Set<ResourceLocation> effects() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        phases.values().forEach(v -> v.effect().ifPresent(out::add));
        return out;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<VisualPhase, Visual> phases = new EnumMap<>(VisualPhase.class);

        private Builder put(VisualPhase phase, @Nullable ResourceLocation animation, @Nullable ResourceLocation effect, Visual.Anchor anchor) {
            phases.put(phase, Visual.of(animation, effect, anchor));
            return this;
        }

        public Builder cast(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.CAST, animation, effect, Visual.Anchor.EYES);
        }

        public Builder chargeLoop(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.CHARGE_LOOP, animation, effect, Visual.Anchor.EYES);
        }

        public Builder release(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.RELEASE, animation, effect, Visual.Anchor.EYES);
        }

        public Builder channelLoop(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.CHANNEL_LOOP, animation, effect, Visual.Anchor.EYES);
        }

        public Builder toggleOn(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.TOGGLE_ON, animation, effect, Visual.Anchor.BODY);
        }

        public Builder toggleLoop(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.TOGGLE_LOOP, animation, effect, Visual.Anchor.BODY);
        }

        public Builder toggleOff(@Nullable ResourceLocation animation, @Nullable ResourceLocation effect) {
            return put(VisualPhase.TOGGLE_OFF, animation, effect, Visual.Anchor.BODY);
        }

        public Builder projectileTrail(ResourceLocation effect) {
            return put(VisualPhase.PROJECTILE_TRAIL, null, effect, Visual.Anchor.MOTION);
        }

        public Builder impact(ResourceLocation effect) {
            return put(VisualPhase.IMPACT, null, effect, Visual.Anchor.BODY);
        }

        /** Overrides where a phase's effect sits on the entity. */
        public Builder anchor(VisualPhase phase, Visual.Anchor anchor) {
            Visual v = phases.get(phase);
            if (v != null) phases.put(phase, new Visual(v.animation(), v.effect(), anchor));
            return this;
        }

        public AbilityVisuals build() {
            return new AbilityVisuals(new EnumMap<>(phases));
        }
    }
}
