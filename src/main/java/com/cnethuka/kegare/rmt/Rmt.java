package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import net.neoforged.bus.api.IEventBus;
import com.cnethuka.kegare.loadout.SharedAbilities;

/**
 * Reverse Malice Technique: the first shared ability (DESIGN §3.17). Registered at mod construction. Locked until
 * the player finds the green orb ({@link GreenOrbService}).
 */
public final class Rmt {
    public static final ReverseMaliceTechnique REVERSE_MALICE_TECHNIQUE = SharedAbilities.register(new ReverseMaliceTechnique());

    private Rmt() {}

    public static void init(IEventBus modBus) {
        GreenOrbService.register(modBus);
        VisualHooks.registerEffect(RmtIds.FX_HEAL_PULSE);
        VisualHooks.registerEffect(RmtIds.FX_ORB);
        VisualHooks.registerEffect(RmtIds.FX_TRAIL_DOT);
        VisualHooks.registerEffect(RmtIds.FX_UNLOCK_BURST);
    }
}
