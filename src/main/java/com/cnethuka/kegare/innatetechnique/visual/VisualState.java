package com.cnethuka.kegare.innatetechnique.visual;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime-only (attachment {@code kegare:visual_state}, never saved or copied): the loops currently playing on an
 * entity, so players who start tracking it later still see them (a shell that is already up, a channel in progress).
 */
public final class VisualState {
    /**
     * @param startTick     game time the loop started (a late viewer's charge effect resumes at the right size)
     * @param lifetimeTicks overrides the effect's persistent particles' lifetime (0 = as authored)
     */
    public record Loop(Visual visual, int growTicks, long startTick, int lifetimeTicks) {
        public Loop(Visual visual, int growTicks, long startTick) {
            this(visual, growTicks, startTick, 0);
        }
    }

    final Map<String, Loop> loops = new LinkedHashMap<>();

    public Map<String, Loop> loops() {
        return java.util.Collections.unmodifiableMap(loops);
    }
}
