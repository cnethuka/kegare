package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.impact.ImpactKind;
import com.cnethuka.kegare.impact.Impacts;
import com.cnethuka.kegare.innate_technique.swap.Swapper;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.progression.Landings;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Rewind (special of Point Maker, INSTANT): snaps the player or mob you're looking at back to where it was {@code seconds} ago.
 * Position only (health, effects and facing stay as they are now), nudged out of blocks, fall reset. Fizzles for free with no unbroken
 * record of where it was (it changed dimension, or a mob wasn't near a Time Manipulation holder that long), within
 * {@link #ALREADY_THERE} of where it was then (nothing would visibly happen), and on a boss (DESIGN §4). Fires {@link TimeTargetEvent}
 * first; if cancelled it fizzles and the Malice stays spent.
 */
public final class RewindAbility extends TimeAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(TimeIds.ANIM_REWIND_CAST, null).build();
    /** Within this many blocks of where it was then, there's nowhere to send it back to: a free fizzle. */
    static final double ALREADY_THERE = 1.0;

    RewindAbility() {
        super("rewind", CastType.INSTANT, () -> TimeManipulation.POINT_MAKER);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.REWIND_COST.get(); }
    @Override
    public Object[] descriptionArgs() {
        return new Object[] {com.cnethuka.kegare.config.ConfigText.number(TimeConfig.RANGE),
                com.cnethuka.kegare.config.ConfigText.number(TimeConfig.REWIND_SECONDS)};
    }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.REWIND_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    private static int ticks() {
        return TimeConfig.ticks(TimeConfig.REWIND_SECONDS);
    }

    /** Where the target goes back to, if it has an unbroken past and that's somewhere else. */
    private static Optional<List<Vec3>> pathBack(LivingEntity target) {
        return PositionHistory.path(target, ticks()).filter(path -> !alreadyThere(target, path));
    }

    private static boolean alreadyThere(LivingEntity target, List<Vec3> path) {
        return path.get(0).distanceToSqr(target.position()) <= ALREADY_THERE * ALREADY_THERE;
    }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return TimeTargeting.lookedAtLiving(ctx.player()).filter(t -> !TimeTargeting.immune(t) && pathBack(t).isPresent()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer p) {
        Optional<LivingEntity> target = TimeTargeting.lookedAtLiving(p);
        if (target.isEmpty()) return Component.translatable("message.kegare.time.no_target");
        if (TimeTargeting.immune(target.get())) return Component.translatable("message.kegare.time.immune", target.get().getDisplayName());
        Optional<List<Vec3>> path = PositionHistory.path(target.get(), ticks());
        if (path.isPresent() && alreadyThere(target.get(), path.get())) {
            return Component.translatable("message.kegare.time.already_there", target.get().getDisplayName());
        }
        return Component.translatable("message.kegare.time.no_history", target.get().getDisplayName());
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        LivingEntity target = TimeTargeting.lookedAtLiving(caster).orElse(null);
        List<Vec3> path = target == null ? null : pathBack(target).orElse(null);
        if (target == null || path == null || TimeTargeting.immune(target)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        if (NeoForge.EVENT_BUS.post(new TimeTargetEvent(caster, target, this)).isCanceled()) {
            caster.displayClientMessage(Component.translatable("message.kegare.time.refused"), true);
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        Vec3 then = path.get(0);
        // Not So Fast: a player who'd run 8 blocks or more further from the caster in that time.
        double ran = target.position().distanceTo(caster.position()) - then.distanceTo(caster.position());
        TimeManipulation.sound(target, KegareSounds.TIME_REVERSE_TICK, 1f, 1f);
        Swapper.moveTo(target, then, target.getYRot(), target.getXRot());
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.resetFallDistance();
        TimeStops.moved(target); // if held in a Standstill, it's held here now
        TimeManipulation.sound(target, KegareSounds.TIME_REVERSE_TICK, 1f, 0.85f);
        TimeManipulation.fx(target, TimeManipulation.fx(TimeNet.Fx.Kind.REWIND, target.getId(), path, 0f, 0));
        Landings.land(caster, target, this); // no damage, so the landing is counted here
        Impacts.hit(caster, target, ImpactKind.TECHNIQUE, 0, 0, 0, false);
        if (target instanceof ServerPlayer && ran >= TimeAdvancements.NOT_SO_FAST_BLOCKS) TimeAdvancements.award(caster, TimeAdvancements.NOT_SO_FAST);
    }
}
