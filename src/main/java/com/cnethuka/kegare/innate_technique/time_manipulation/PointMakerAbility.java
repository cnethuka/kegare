package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innate_technique.swap.Swapper;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Point Maker (INSTANT, two stages). With no mark out: mark the top of the block you're looking at (only you see it), as you are now.
 * With one out: return to it, facing as you faced, shedding every effect gained since, and healed by {@code healFraction} of max
 * health, never more than you've lost since marking. It never touches Malice. The mark itself starts no cooldown; the return does, or
 * the mark running out. Special: Rewind.
 */
public final class PointMakerAbility extends TimeAbility {
    private static final Visual MARK = Visual.animation(TimeIds.ANIM_POINT_MAKER_MARK);
    private static final Visual RETURN = Visual.animation(TimeIds.ANIM_POINT_MAKER_RETURN);

    PointMakerAbility() {
        super("point_maker", CastType.INSTANT);
    }

    /** Mark or return, whichever comes next (the HUD and Sorcerer screen show the current one). */
    @Override
    public double cost(ServerPlayer p) {
        return PointMarks.has(p) ? TimeConfig.RETURN_COST.get() : TimeConfig.MARK_COST.get();
    }

    /** Read after the cast: a mark still out means it was just made, which starts nothing. */
    @Override
    public int cooldownTicks(ServerPlayer p) {
        return PointMarks.has(p) ? 0 : TimeConfig.ticks(TimeConfig.POINT_MAKER_COOLDOWN);
    }

    @Override
    public int shownCooldownTicks(ServerPlayer p) {
        return TimeConfig.ticks(TimeConfig.POINT_MAKER_COOLDOWN);
    }

    @Override public Optional<Ability> special() { return Optional.of(TimeManipulation.REWIND); }
    @Override public boolean hasNoTarget() { return true; } // it moves only you

    @Override
    public boolean canCast(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        return PointMarks.get(p).map(m -> m.level.equals(p.level().dimension()))
                .orElseGet(() -> TimeTargeting.lookedAtBlock(p, TimeConfig.MARK_RANGE.get()).isPresent());
    }

    @Override
    public Component blockedMessage(ServerPlayer p) {
        return PointMarks.has(p) ? Component.translatable("message.kegare.time.mark_elsewhere")
                : Component.translatable("message.kegare.time.no_block", Long.toString(Math.round(TimeConfig.MARK_RANGE.get())));
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Optional<PointMarks.Mark> mark = PointMarks.get(p);
        if (mark.isPresent()) {
            returnTo(p, mark.get());
            return;
        }
        BlockHitResult hit = TimeTargeting.lookedAtBlock(p, TimeConfig.MARK_RANGE.get()).orElseThrow();
        PointMarks.mark(p, hit.getBlockPos(), TimeTargeting.topOf(p, hit.getBlockPos()));
        VisualService.playOnce(p, MARK);
        TimeManipulation.sound(p, KegareSounds.TIME_CLICK, 0.8f, 1.15f);
    }

    /** The return: back to the mark, as it says. Package-visible for the tests. */
    static void returnTo(ServerPlayer p, PointMarks.Mark mark) {
        boolean low = p.getHealth() < TimeAdvancements.SECOND_CHANCE_HEALTH;
        List<Vec3> path = new ArrayList<>(mark.path);
        path.add(p.position());
        TimeManipulation.sound(p, KegareSounds.TIME_CLICK, 1f, 0.9f);
        Swapper.moveTo(p, mark.standAt, mark.yaw, mark.pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        for (MobEffectInstance effect : List.copyOf(p.getActiveEffects())) {
            if (!mark.effects.contains(effect.getEffect())) p.removeEffect(effect.getEffect());
        }
        float heal = PointMarks.heal(mark.health, p.getHealth(), p.getMaxHealth(), TimeConfig.RETURN_HEAL.get());
        if (heal > 0f) p.setHealth(Math.min(mark.health, p.getHealth() + heal));
        PointMarks.clear(p);
        VisualService.playOnce(p, RETURN);
        TimeManipulation.sound(p, KegareSounds.TIME_CLICK, 1f, 0.9f);
        TimeManipulation.fx(p, TimeManipulation.fx(TimeNet.Fx.Kind.RETURN, p.getId(), path, 0f, 0));
        if (low) TimeAdvancements.secondChanceStarted(p);
    }

    @Override
    public Object[] descriptionArgs() {
        return new Object[] {com.cnethuka.kegare.config.ConfigText.number(TimeConfig.MARK_RANGE),
                com.cnethuka.kegare.config.ConfigText.number(TimeConfig.MARK_SECONDS),
                com.cnethuka.kegare.config.ConfigText.percent(TimeConfig.RETURN_HEAL)};
    }
}
