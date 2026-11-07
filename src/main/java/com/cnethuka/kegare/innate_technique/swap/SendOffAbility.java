package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Special of Anchor (INSTANT): needs your anchor out. The player or mob you're looking at is swapped with the anchor instead of
 * you; the anchor is used up. No anchor (or nobody in sight): fizzles at no cost.
 */
public final class SendOffAbility extends SwapAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(SwapIds.ANIM_SEND_OFF_CAST, null).build();

    SendOffAbility() {
        super("send_off", CastType.INSTANT, () -> Swap.ANCHOR);
    }

    @Override public double cost(ServerPlayer p) { return SwapConfig.SEND_OFF_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return Anchors.has(ctx.player()) && SwapAbility.hasLivingTarget(ctx);
    }

    @Override boolean takesMobs() { return true; }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Anchors.has(player) ? super.blockedMessage(player) : Component.translatable("message.kegare.swap.no_anchor");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer caster = ctx.player();
        LivingEntity target = SwapTargeting.lookedAtLiving(caster).orElse(null);
        AnchorEntity anchor = Anchors.get(caster).orElse(null);
        if (target == null || anchor == null || !SwapTargeting.allowed(caster, target, this)) {
            CastManager.fizzleKeepingCost(caster);
            return;
        }
        Vec3 to = anchor.arrivalFor(target);
        SwapSounds.POSITIONS.at(target);
        SwapSounds.POSITIONS.at(anchor);
        Swapper.depart(target);
        Swapper.depart(anchor);
        SwapLink.play(caster.serverLevel(), Swapper.bodyPos(target), Swapper.bodyPos(anchor));
        Anchors.consume(caster, anchor);
        Swapper.moveTo(target, to);
        Swapper.arrive(target);
        SwapTracker.swapped(caster, target);
        SwapAdvancements.award(caster, SwapAdvancements.RETURN_TO_SENDER);
    }
}
