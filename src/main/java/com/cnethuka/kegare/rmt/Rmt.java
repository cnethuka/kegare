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
    /** Its special: healing another player. */
    public static final RmtOnAnother ON_ANOTHER = new RmtOnAnother();

    private Rmt() {}

    public static void init(IEventBus modBus) {
        GreenOrbService.register(modBus);
        RmtOnAnother.register();
        com.cnethuka.kegare.progression.FightTracker.addListener(report -> RmtFatigue.onFightEnded(report.player()));
        com.cnethuka.kegare.loadout.SharedAbilityService.addUnlockListener((player, ability) -> {
            if (ability == REVERSE_MALICE_TECHNIQUE) com.cnethuka.kegare.progression.InsightHooks.unlockedRmt(player);
        });
        VisualHooks.registerEffect(RmtIds.FX_HEAL_PULSE);
        VisualHooks.registerEffect(RmtIds.FX_ORB);
        VisualHooks.registerEffect(RmtIds.FX_TRAIL_DOT);
        VisualHooks.registerEffect(RmtIds.FX_UNLOCK_BURST);
    }
}
