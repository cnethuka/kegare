package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.CastSlot;
import com.cnethuka.kegare.api.domain.DomainContext;
import com.cnethuka.kegare.api.domain.DomainExpansion;
import com.cnethuka.kegare.innatetechnique.CastIntent;
import net.minecraft.server.level.ServerPlayer;

/** A technique's Domain Expansion (DESIGN §3.35), from its {@link DomainExpansion}. */
public final class DomainExpansions {
    private DomainExpansions() {}

    public static DomainSpec spec(DomainExpansion ultimate, ServerPlayer caster) {
        return new DomainSpec(ultimate.id(), true, ultimate.radius(caster), ultimate.innerRadius(caster), DomainMastery.durationTicks(caster, true, ultimate.durationTicks(caster)), ultimate.barrierStrength(caster),
                DomainConfig.ticks(DomainConfig.DOMAIN_BURNOUT), 1.0, ultimate.look(), ultimate.sureHit(), new DomainSpec.Hooks() {
                    @Override public void onOpen(DomainContext ctx) { ultimate.onOpen(ctx); }
                    @Override public void tickInside(DomainContext ctx) { ultimate.tickInside(ctx); }
                    @Override public void onClose(DomainContext ctx, boolean broken) { ultimate.onClose(ctx, broken); }
                    @Override public void furnish(com.cnethuka.kegare.api.domain.DomainInterior interior) { ultimate.furnish(interior); }
                });
    }

    public static void open(DomainExpansion ultimate, AbilityContext ctx) {
        Domains.open(ctx.player(), spec(ultimate, ctx.player()), release(ctx.slot()));
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
