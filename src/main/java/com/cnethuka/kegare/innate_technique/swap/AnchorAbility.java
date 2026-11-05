package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Anchor (INSTANT, two stages). With no anchor out: throw one ({@link AnchorEntity}); it sticks where it lands and
 * times out after {@code lifetime}. With one out: swap yourself with it (it is used up). The throw costs and cools
 * down a little; the recall pays the rest and starts the real cooldown. Special: Send Off.
 */
public final class AnchorAbility extends SwapAbility {
    private static final Visual THROW = Visual.animation(SwapIds.ANIM_ANCHOR_THROW);
    private static final Visual RECALL = Visual.animation(SwapIds.ANIM_ANCHOR_RECALL);

    AnchorAbility() {
        super("anchor", CastType.INSTANT);
    }

    /** Throw or recall, whichever comes next (the HUD and Sorcerer screen show the current one). */
    @Override
    public double cost(ServerPlayer p) {
        return Anchors.has(p) ? SwapConfig.ANCHOR_RECALL_COST.get() : SwapConfig.ANCHOR_THROW_COST.get();
    }

    /** Read after the cast: an anchor still out means it was just thrown. */
    @Override
    public int cooldownTicks(ServerPlayer p) {
        return Anchors.has(p) ? SwapConfig.ticks(SwapConfig.ANCHOR_THROW_COOLDOWN) : SwapConfig.ticks(SwapConfig.ANCHOR_COOLDOWN);
    }

    @Override public Optional<Ability> special() { return Optional.of(Swap.SEND_OFF); }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        Optional<AnchorEntity> anchor = Anchors.get(caster);
        if (anchor.isEmpty()) {
            throwAnchor(caster);
        } else {
            recall(caster, anchor.get());
        }
    }

    private void throwAnchor(ServerPlayer caster) {
        AnchorEntity anchor = new AnchorEntity(caster, caster.level(), SwapConfig.ticks(SwapConfig.ANCHOR_LIFETIME));
        anchor.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0f, SwapConfig.ANCHOR_SPEED.get().floatValue(), 0.5f);
        caster.level().addFreshEntity(anchor);
        Anchors.set(caster, anchor);
        VisualService.playOnce(caster, THROW);
        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    private void recall(ServerPlayer caster, AnchorEntity anchor) {
        if (!SwapTargeting.allowed(caster, anchor, this)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        Vec3 to = anchor.arrivalFor(caster);
        Swapper.depart(caster);
        Swapper.depart(anchor);
        SwapLink.play(caster.serverLevel(), Swapper.bodyPos(caster), Swapper.bodyPos(anchor));
        Anchors.consume(caster, anchor);
        Swapper.moveTo(caster, to);
        Swapper.arrive(caster);
        VisualService.playOnce(caster, RECALL);
    }
}
