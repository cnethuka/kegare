package com.cnethuka.kegare.api.dominion;

import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.BaseAbility;
import com.cnethuka.kegare.api.ability.CastType;
import com.cnethuka.kegare.dominion.DominionConfig;
import com.cnethuka.kegare.dominion.Dominions;
import com.cnethuka.kegare.dominion.FullDominions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A technique's ultimate that is a full Dominion (DESIGN §3.35): give one to {@code InnateTechnique.Builder.ultimate}. Opening it takes a
 * full Dominion Meter and its {@link #cost} in Malice, and is refused (with nothing paid) to a Severed player, to a full brain meter, to
 * a caster whose Dominion is already open, and where it can't open. Kegare raises and closes the barrier, traps whoever is inside, takes
 * the hits on it, ends it and burns its caster out; it also runs clashes with other Dominions. What it does inside is yours.
 * <p>
 * Its barrier's size, how long it stays and how much it takes are {@code [dominion.full]}'s unless you override them. Its key is V, and
 * in a clash its caster has to keep holding it.
 */
public abstract class DominionUltimate extends BaseAbility {
    protected DominionUltimate(ResourceLocation id) {
        super(id, CastType.INSTANT);
    }

    /** What its inside looks like. */
    public abstract DominionLook look();

    /** Whether its technique's attacks inside can't miss: see {@link DominionContext#sureHitTargets}. */
    public boolean sureHit() {
        return true;
    }

    public double radius(ServerPlayer caster) {
        return DominionConfig.FULL_RADIUS.get();
    }

    public int durationTicks(ServerPlayer caster) {
        return DominionConfig.ticks(DominionConfig.FULL_DURATION);
    }

    public double barrierStrength(ServerPlayer caster) {
        return DominionConfig.FULL_BARRIER.get();
    }

    /** It has closed: whoever is inside is trapped. */
    public void onOpen(DominionContext ctx) {}

    /** Every tick it's open (closed and not contested). */
    public void tickInside(DominionContext ctx) {}

    /** It's ending: {@code broken} if its barrier broke or it lost a clash. */
    public void onClose(DominionContext ctx, boolean broken) {}

    /** Your own reasons to refuse it, before anything is paid, on top of Kegare's. */
    public boolean canOpen(AbilityContext ctx) {
        return true;
    }

    @Override
    public int cooldownTicks(ServerPlayer player) {
        return 20 * 180;
    }

    @Override
    public final boolean canCast(AbilityContext ctx) {
        return Dominions.refusal(ctx.player(), FullDominions.spec(this, ctx.player())).isEmpty() && canOpen(ctx);
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Dominions.refusal(player, FullDominions.spec(this, player)).orElseGet(() -> super.blockedMessage(player));
    }

    @Override
    public final void execute(AbilityContext ctx) {
        FullDominions.open(this, ctx);
    }
}
