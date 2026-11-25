package com.cnethuka.kegare.dominion;

import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.CastSlot;
import com.cnethuka.kegare.api.dominion.DominionContext;
import com.cnethuka.kegare.api.dominion.DominionUltimate;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import net.minecraft.server.level.ServerPlayer;

/** A technique's full Dominion (DESIGN §3.35), from its {@link DominionUltimate}. */
public final class FullDominions {
    private FullDominions() {}

    public static DominionSpec spec(DominionUltimate ultimate, ServerPlayer caster) {
        return new DominionSpec(ultimate.id(), true, ultimate.radius(caster), ultimate.innerRadius(caster), ultimate.durationTicks(caster), ultimate.barrierStrength(caster),
                DominionConfig.ticks(DominionConfig.DOMINION_BURNOUT), 1.0, ultimate.look(), ultimate.sureHit(), new DominionSpec.Hooks() {
                    @Override public void onOpen(DominionContext ctx) { ultimate.onOpen(ctx); }
                    @Override public void tickInside(DominionContext ctx) { ultimate.tickInside(ctx); }
                    @Override public void onClose(DominionContext ctx, boolean broken) { ultimate.onClose(ctx, broken); }
                    @Override public void furnish(com.cnethuka.kegare.api.dominion.DominionInterior interior) { ultimate.furnish(interior); }
                });
    }

    public static void open(DominionUltimate ultimate, AbilityContext ctx) {
        Dominions.open(ctx.player(), spec(ultimate, ctx.player()), release(ctx.slot()));
    }

    /** The key-up that lets go of the key a cast came from. */
    public static CastIntent release(CastSlot slot) {
        return switch (slot) {
            case SELECTED -> CastIntent.CAST_RELEASE;
            case SPECIAL -> CastIntent.SPECIAL_RELEASE;
            case ULTIMATE -> CastIntent.ULTIMATE_RELEASE;
        };
    }
}
