package com.cnethuka.kegare.api.domain;

import com.cnethuka.kegare.api.ability.AbilityContext;
import com.cnethuka.kegare.api.ability.BaseAbility;
import com.cnethuka.kegare.api.ability.CastType;
import com.cnethuka.kegare.domain.DomainConfig;
import com.cnethuka.kegare.domain.Domains;
import com.cnethuka.kegare.domain.DomainExpansions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A technique's ultimate that is a Domain Expansion (DESIGN §3.35): give one to {@code InnateTechnique.Builder.ultimate}. Opening it takes a
 * Domain Expansion Meter and its {@link #cost} in Malice, and is refused (with nothing paid) to a Severed player, to a full brain meter, to
 * a caster whose Domain is already open, and where it can't open. Kegare raises and closes the barrier, traps whoever is inside, takes
 * the hits on it, ends it and burns its caster out; it also runs clashes with other Domains. What it does inside is yours.
 * <p>
 * Its barrier's size, its inner space's size, how long it stays and how much it takes are {@code [domain.full]}'s unless you override
 * them. Sealed, everyone inside is taken into its inner space: a plot of real blocks of its own, its floor from its look, furnished by
 * {@link #furnish}. Its key is V, and in a clash its caster has to keep holding it.
 */
public abstract class DomainExpansion extends BaseAbility {
    protected DomainExpansion(ResourceLocation id) {
        super(id, CastType.INSTANT);
    }

    /** What its inside looks like. */
    public abstract DomainLook look();

    /** Whether its technique's attacks inside can't miss: see {@link DomainContext#sureHitTargets}. */
    public boolean sureHit() {
        return true;
    }

    public double radius(ServerPlayer caster) {
        return DomainConfig.FULL_RADIUS.get();
    }

    /**
     * How long it holds once closed before it breaks, before Kegare scales it by its caster's mastery (their Grade and their technique's
     * Refinement: from {@code domain.mastery.leastShare} of this to {@code mostShare} of it).
     */
    public int durationTicks(ServerPlayer caster) {
        return DomainConfig.ticks(DomainConfig.FULL_DURATION);
    }

    public double barrierStrength(ServerPlayer caster) {
        return DomainConfig.FULL_BARRIER.get();
    }

    /** How far its inner space reaches (bigger than its shell: the inside is bigger than it looks). */
    public int innerRadius(ServerPlayer caster) {
        return DomainConfig.FULL_INNER_RADIUS.get();
    }

    /** Furnishes its inner space as it's built, after Kegare has laid its floor (and ruins). Nothing by default. */
    public void furnish(DomainInterior interior) {}

    /** It has closed: whoever is inside is trapped. */
    public void onOpen(DomainContext ctx) {}

    /** Every tick it's open (closed and not contested). */
    public void tickInside(DomainContext ctx) {}

    /** It's ending: {@code broken} if its barrier broke or it lost a clash. */
    public void onClose(DomainContext ctx, boolean broken) {}

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
        return Domains.refusal(ctx.player(), DomainExpansions.spec(this, ctx.player())).isEmpty() && canOpen(ctx);
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Domains.refusal(player, DomainExpansions.spec(this, player)).orElseGet(() -> super.blockedMessage(player));
    }

    @Override
    public final void execute(AbilityContext ctx) {
        DomainExpansions.open(this, ctx);
    }
}
