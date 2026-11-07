package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Reverse Malice Technique (shared ability, CHANNELED): hold its number key to heal at the end of every full second;
 * release to stop. Malice is turned back on the body, so it is costly: a start cost plus a steep drain per second.
 * Like every channel it ends on release, when Malice runs out, on a stun or freeze (the shared cast rules), and here
 * also once there is nothing left to heal; the cooldown starts then. No special: R fizzles. Severed players can never
 * cast it (the cast pipeline refuses them). Numbers: {@link RmtConfig}.
 */
public final class ReverseMaliceTechnique extends BaseAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .channelLoop(null, RmtIds.FX_CHANNEL_AURA)
            .anchor(VisualPhase.CHANNEL_LOOP, Visual.Anchor.BODY)
            .build();

    public ReverseMaliceTechnique() {
        super(RmtIds.ABILITY, CastType.CHANNELED);
    }

    // Shared ability: no innate technique's surroundings modifier applies.
    @Override public double cost(ServerPlayer p) { return RmtConfig.COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return RmtConfig.UPKEEP.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return RmtConfig.ticks(RmtConfig.COOLDOWN); }
    @Override public int maxChannelTicks(ServerPlayer p) { return RmtConfig.ticks(RmtConfig.MAX_DURATION); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    /** Nothing to heal: refused before anything is paid. */
    @Override
    public boolean canCast(AbilityContext ctx) {
        return ctx.player().getHealth() < ctx.player().getMaxHealth();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.rmt.not_hurt");
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
        RmtAdvancements.onCastStart(p);
    }

    @Override
    public void tick(AbilityContext ctx) {
        if (ctx.activeTicks() <= 0 || ctx.activeTicks() % 20 != 0) return;
        ServerPlayer p = ctx.player();
        p.heal(RmtConfig.HEAL_PER_SECOND.get().floatValue());
        VisualService.playOnce(p, RmtIds.HEAL_PULSE);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.3f);
        if (p.getHealth() >= p.getMaxHealth()) {
            p.displayClientMessage(Component.translatable("message.kegare.rmt.healed"), true);
            CastManager.finishChannel(p);
        }
    }
}
