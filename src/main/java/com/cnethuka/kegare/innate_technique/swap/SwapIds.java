package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import net.minecraft.resources.ResourceLocation;

/** Swap's IDs: player animations (assets/kegare/player_animations/swap.json) and Photon effects (assets/kegare/fx/swap/). */
public final class SwapIds {
    // Player Animation Library animations
    public static final ResourceLocation ANIM_POSITIONS_CLAP = Kegare.id("positions_clap");
    public static final ResourceLocation ANIM_TOTAL_SWAP_CLAP = Kegare.id("total_swap_clap");
    public static final ResourceLocation ANIM_SWITCHBACK_CAST = Kegare.id("switchback_cast");
    public static final ResourceLocation ANIM_SWITCHBACK_SNAP = Kegare.id("switchback_snap");
    public static final ResourceLocation ANIM_AWARENESS_CAST = Kegare.id("awareness_cast");
    public static final ResourceLocation ANIM_MIMIC_CAST = Kegare.id("mimic_cast");
    public static final ResourceLocation ANIM_ANCHOR_THROW = Kegare.id("anchor_throw");
    public static final ResourceLocation ANIM_ANCHOR_RECALL = Kegare.id("anchor_recall");
    public static final ResourceLocation ANIM_SEND_OFF_CAST = Kegare.id("send_off_cast");
    public static final ResourceLocation ANIM_EXCHANGE_CAST = Kegare.id("exchange_cast");
    public static final ResourceLocation ANIM_MOMENTUM_CAST = Kegare.id("momentum_cast");

    // Effects (teal; never Rime's ice blue). Photon's are authored in SwapFxAuthoring; what Swap draws itself is in SwapFx.
    public static final ResourceLocation FX_SWAP_DEPART = fx("swap_depart");
    public static final ResourceLocation FX_SWAP_ARRIVE = fx("swap_arrive");
    /** The line between where someone was and where they went (SwapLink; drawn by SwapFx). */
    public static final ResourceLocation FX_SWAP_LINK = fx("swap_link");
    public static final ResourceLocation FX_SWITCHBACK_TIMER = fx("switchback_timer");
    public static final ResourceLocation FX_AWARENESS_SHIMMER = fx("awareness_shimmer");
    public static final ResourceLocation FX_AWARENESS_FLIP = fx("awareness_flip");
    public static final ResourceLocation FX_MIMIC_FLICKER = fx("mimic_flicker");
    /** An effect crossing over in an Exchange (SwapLink.travel; drawn by SwapFx: a line and a bead along it). */
    public static final ResourceLocation FX_EXCHANGE_TRANSFER = fx("exchange_transfer");
    /** Momentum Exchange, on both: the waist ring and the streaks (drawn by SwapFx; no Photon). */
    public static final ResourceLocation FX_MOMENTUM_BURST = fx("momentum_burst");
    /** Total Swap, on both: the wide rings (drawn by SwapFx; no Photon). */
    public static final ResourceLocation FX_TOTAL_SWAP_BURST = fx("total_swap_burst");

    /** Both ends of every swap, played at a position and naming who moved (the afterimage and the flicker are drawn from them). */
    public static final Visual DEPART = Visual.effect(FX_SWAP_DEPART, Visual.Anchor.BODY);
    public static final Visual ARRIVE = Visual.effect(FX_SWAP_ARRIVE, Visual.Anchor.BODY);
    /** Switchback: the countdown ring on both, from the cast until the swap back (drawn by SwapFx). */
    public static final Visual SWITCHBACK_TIMER = Visual.effect(FX_SWITCHBACK_TIMER, Visual.Anchor.FEET);
    public static final String SWITCHBACK_TIMER_KEY = "kegare:swap/switchback_timer/";
    /** On a disguised player, sent to that player only so they know it's on: the corner brackets on their screen (SwapHud). */
    public static final Visual AWARENESS_SHIMMER = Visual.effect(FX_AWARENESS_SHIMMER, Visual.Anchor.BODY);
    /** A disguise landing on someone, played at them and naming them: the card-flip ripple down the body (drawn by SwapFx). */
    public static final Visual AWARENESS_FLIP = Visual.effect(FX_AWARENESS_FLIP, Visual.Anchor.BODY);
    public static final String AWARENESS_SHIMMER_KEY = "kegare:swap/awareness_shimmer";
    public static final Visual MOMENTUM_BURST = Visual.effect(FX_MOMENTUM_BURST, Visual.Anchor.BODY);
    public static final Visual TOTAL_SWAP_BURST = Visual.effect(FX_TOTAL_SWAP_BURST, Visual.Anchor.BODY);

    private SwapIds() {}

    private static ResourceLocation fx(String name) {
        return Kegare.id("swap/" + name);
    }
}
