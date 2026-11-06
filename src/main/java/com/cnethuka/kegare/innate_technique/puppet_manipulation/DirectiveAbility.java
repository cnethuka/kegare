package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Directive (INSTANT): every puppet within {@code range} takes the next order, Follow, Stay, Guard, Go, Follow...
 * (Go walks to the block you're looking at; with no block in sight the cycle skips it.) The order is remembered: the
 * HUD shows it and new puppets take it.
 */
public final class DirectiveAbility extends PuppetAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .cast(PuppetIds.ANIM_DIRECTIVE_CAST, PuppetIds.FX_DIRECTIVE_PULSE)
            .anchor(VisualPhase.CAST, Visual.Anchor.FEET)
            .build();

    DirectiveAbility() {
        super("directive", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.DIRECTIVE_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return PuppetConfig.ticks(PuppetConfig.DIRECTIVE_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean hasNoTarget() { return true; } // orders your own puppets
    @Override public java.util.Optional<com.cnethuka.kegare.innatetechnique.Ability> special() { return java.util.Optional.of(PuppetManipulation.FOCUS); }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer owner = ctx.player();
        PuppetOwnerData data = owner.getData(PuppetRegistry.OWNER.get());
        PuppetOrder next = data.order.next();
        BlockPos goTarget = null;
        if (next == PuppetOrder.GO) {
            goTarget = goTarget(owner);
            if (goTarget == null) next = next.next(); // nothing to point at: skip Go
        }
        give(owner, next, goTarget);
    }

    /** Sets the owner's order and gives it to every puppet in range. Returns how many heard it. */
    static int give(ServerPlayer owner, PuppetOrder order, @Nullable BlockPos goTarget) {
        PuppetOwnerData data = owner.getData(PuppetRegistry.OWNER.get());
        data.order = order;
        data.goTarget = order == PuppetOrder.GO ? goTarget : null;
        List<PuppetEntity> heard = Puppets.near(owner, PuppetConfig.DIRECTIVE_RANGE.get());
        for (PuppetEntity puppet : heard) {
            puppet.setOrder(order, goTarget);
            VisualService.playOnce(puppet, Visual.effect(PuppetIds.FX_DIRECTIVE_PULSE, Visual.Anchor.FEET));
        }
        owner.displayClientMessage(Component.translatable("message.kegare.directive.order",
                Component.translatable(order.translationKey()), heard.size()), true);
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS,
                0.7f, 0.8f + 0.2f * order.ordinal());
        PuppetHud.markDirty(owner);
        return heard.size();
    }

    /** Where Go sends them: the spot in front of the block face under the crosshair (on top of it for a floor). */
    @Nullable
    static BlockPos goTarget(ServerPlayer owner) {
        BlockHitResult hit = PuppetTargeting.lookedAtBlock(owner, PuppetConfig.DIRECTIVE_RANGE.get()).orElse(null);
        if (hit == null) return null;
        Direction face = hit.getDirection();
        return face == Direction.DOWN ? hit.getBlockPos().below(2) : hit.getBlockPos().relative(face);
    }
}
