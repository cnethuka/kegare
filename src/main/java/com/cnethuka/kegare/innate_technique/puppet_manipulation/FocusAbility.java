package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Special of Directive (INSTANT): every puppet in range swarms the player or mob you're looking at, until it dies, gets
 * away or a new Directive is given; then they go back to their order. The target is marked for everyone to see.
 */
public final class FocusAbility extends PuppetAbility.Special {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(PuppetIds.ANIM_FOCUS_POINT, null).build();

    FocusAbility() {
        super("focus", CastType.INSTANT, () -> PuppetManipulation.DIRECTIVE);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.FOCUS_COST.get(); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    private static Optional<LivingEntity> target(ServerPlayer owner) {
        return PuppetTargeting.lookedAtLiving(owner, PuppetConfig.FOCUS_TARGET_RANGE.get())
                .filter(e -> !(e instanceof PuppetEntity p && p.isOwnedBy(owner)));
    }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return target(ctx.player()).isPresent() && !Puppets.near(ctx.player(), PuppetConfig.DIRECTIVE_RANGE.get()).isEmpty();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable(target(player).isEmpty() ? "message.kegare.focus.no_target" : "message.kegare.focus.no_puppets");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer owner = ctx.player();
        LivingEntity target = target(owner).orElse(null);
        if (target == null) {
            CastManager.fizzleKeepingCost(owner);
            return;
        }
        int swarming = 0;
        for (PuppetEntity puppet : Puppets.near(owner, PuppetConfig.DIRECTIVE_RANGE.get())) {
            if (puppet.attack(target)) swarming++;
        }
        VisualService.playOnce(target, Visual.effect(PuppetIds.FX_FOCUS_MARK, Visual.Anchor.BODY));
        owner.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8f, 1.6f);
        owner.displayClientMessage(Component.translatable("message.kegare.focus.swarm", swarming, target.getDisplayName()), true);
    }
}
