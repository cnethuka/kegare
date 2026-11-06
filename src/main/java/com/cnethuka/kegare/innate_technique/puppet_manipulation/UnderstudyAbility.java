package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.innate_technique.swap.SwapTargetEvent;
import com.cnethuka.kegare.innate_technique.swap.Swapper;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Understudy (INSTANT): swap places with the puppet you're looking at. It fires {@link SwapTargetEvent} with the
 * puppet as the target first, so whatever blocks swaps blocks this too (cancelled: the Malice stays spent, no cooldown).
 */
public final class UnderstudyAbility extends PuppetAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder().cast(PuppetIds.ANIM_UNDERSTUDY_CAST, null).build();

    UnderstudyAbility() {
        super("understudy", CastType.INSTANT);
    }

    @Override public double cost(ServerPlayer p) { return PuppetConfig.UNDERSTUDY_COST.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return PuppetConfig.ticks(PuppetConfig.UNDERSTUDY_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean hasNoTarget() { return true; } // swaps you with your own puppet
    @Override public java.util.Optional<com.cnethuka.kegare.innatetechnique.Ability> special() { return java.util.Optional.of(PuppetManipulation.STAND_IN); }

    @Override
    public boolean canCast(AbilityContext ctx) {
        return PuppetTargeting.lookedAtPuppet(ctx.player(), PuppetConfig.UNDERSTUDY_RANGE.get()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.curio_burst.no_puppet");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer owner = ctx.player();
        PuppetEntity puppet = PuppetTargeting.lookedAtPuppet(owner, PuppetConfig.UNDERSTUDY_RANGE.get()).orElse(null);
        if (puppet == null) {
            CastManager.fizzleKeepingCost(owner);
            return;
        }
        if (NeoForge.EVENT_BUS.post(new SwapTargetEvent(owner, puppet, this)).isCanceled()) {
            owner.displayClientMessage(Component.translatable("message.kegare.understudy.refused"), true);
            CastManager.fizzleKeepingCost(owner);
            return;
        }
        ServerLevel level = owner.serverLevel();
        Vec3 ownerAt = owner.position();
        Vec3 puppetAt = puppet.position();
        float ownerYaw = owner.getYRot();
        Swapper.moveTo(owner, puppetAt, ownerYaw, owner.getXRot());
        Swapper.moveTo(puppet, ownerAt, ownerYaw, 0f);
        Visual swap = Visual.effect(PuppetIds.FX_UNDERSTUDY_SWAP, Visual.Anchor.BODY);
        VisualService.playAt(level, ownerAt.add(0, 0.9, 0), swap);
        VisualService.playAt(level, puppetAt.add(0, 0.9, 0), swap);
        level.playSound(null, ownerAt.x, ownerAt.y, ownerAt.z, SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, puppetAt.x, puppetAt.y, puppetAt.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 0.6f);
    }
}
