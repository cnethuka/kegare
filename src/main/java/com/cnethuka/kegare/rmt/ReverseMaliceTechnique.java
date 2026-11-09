package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Reverse Malice Technique (shared ability, CHANNELED, DESIGN §3.17): while held it heals {@code healPerSecond} times the player's
 * output ({@link RmtOutput}) every second, a little every tick, so damage is healed as it lands. It's never refused at full health and
 * never ends there: it keeps running, and draining Malice, ready for the next hit. Never above max health, never absorption. Malice is
 * turned back on the body, so it is costly: a start cost plus a steep drain per second. Ends like every channel: release, out of
 * Malice, a stun or a freeze; the cooldown starts then. Severed players can never cast it. Numbers: {@link RmtConfig}.
 */
public final class ReverseMaliceTechnique extends BaseAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .channelLoop(null, RmtIds.FX_CHANNEL_AURA)
            .anchor(VisualPhase.CHANNEL_LOOP, Visual.Anchor.BODY)
            .build();
    /** A heal pulse at most this often while it's healing. */
    static final int PULSE_TICKS = 20;
    /** Told whenever a player's own channelled RMT heals (themselves or another): Background RMT's unlock counts it (DESIGN §3.31). */
    private static final java.util.List<java.util.function.BiConsumer<ServerPlayer, Float>> HEAL_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addHealListener(java.util.function.BiConsumer<ServerPlayer, Float> listener) {
        HEAL_LISTENERS.add(listener);
    }

    /** Told every tick a player channels RMT (Six Eyes: Strain and collapse risk fall while the brain is refreshed). */
    private static final java.util.List<java.util.function.Consumer<ServerPlayer>> CHANNEL_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addChannelListener(java.util.function.Consumer<ServerPlayer> listener) {
        CHANNEL_LISTENERS.add(listener);
    }

    public ReverseMaliceTechnique() {
        super(RmtIds.ABILITY, CastType.CHANNELED);
    }

    // Shared ability: no innate technique's surroundings modifier applies.
    @Override public double cost(ServerPlayer p) { return RmtConfig.COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return RmtConfig.UPKEEP.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return RmtConfig.ticks(RmtConfig.COOLDOWN); }
    @Override public int maxChannelTicks(ServerPlayer p) { return RmtConfig.ticks(RmtConfig.MAX_DURATION); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public com.cnethuka.kegare.loadout.AbilityCategory category() { return com.cnethuka.kegare.loadout.AbilityCategory.MALICE; }
    @Override public net.minecraft.network.chat.Component unlockHint() { return net.minecraft.network.chat.Component.translatable("ability.kegare.reverse_malice_technique.unlock"); }
    /** R + its key: healing another player, for those who know how. */
    @Override public java.util.Optional<com.cnethuka.kegare.innatetechnique.Ability> special() { return java.util.Optional.of(Rmt.ON_ANOTHER); }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
        RmtAdvancements.onCastStart(p);
    }

    @Override
    public void tick(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        RmtState state = GreenOrbService.state(p);
        double low = RmtConfig.LOW_HEALTH.get();
        if (p.getHealth() <= low) state.lowDuringChannel = true;
        for (var listener : CHANNEL_LISTENERS) listener.accept(p);
        // Burnt out: the technique is repaired first, and the body waits (DESIGN §3.17).
        if (!Repair.tick(p)) heal(p, p, RmtConfig.HEAL_PER_SECOND.get() * RmtOutput.of(p) / 20.0);
        if (state.lowDuringChannel && p.getHealth() > low) { // healed themselves out of it: alive in 10 s, and it counts
            state.lowDuringChannel = false;
            state.survivedLowAt = p.level().getGameTime() + RmtConfig.ticks(RmtConfig.SURVIVE_SECONDS);
        }
    }

    @Override
    public void onEnd(AbilityContext ctx, com.cnethuka.kegare.innatetechnique.EndReason reason) {
        GreenOrbService.state(ctx.player()).lowDuringChannel = false; // still low when it stopped: not healed out of it
        Repair.stop(ctx.player());
    }

    /**
     * One tick's healing on {@code target}: never above max health, never absorption, nothing at all at full health. The pulse (and its
     * chime) about once a second while it's actually healing. Returns how much it healed.
     */
    static float heal(ServerPlayer caster, net.minecraft.world.entity.LivingEntity target, double amount) {
        float before = target.getHealth();
        if (amount <= 0 || before >= target.getMaxHealth() || !target.isAlive()) return 0f;
        target.heal((float) amount);
        float healed = target.getHealth() - before;
        if (healed <= 0f) return 0f;
        for (var listener : HEAL_LISTENERS) listener.accept(caster, healed);
        RmtState state = GreenOrbService.state(caster);
        long now = caster.level().getGameTime();
        if (now - state.lastPulse >= PULSE_TICKS) {
            state.lastPulse = now;
            VisualService.playOnce(target, RmtIds.HEAL_PULSE);
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.3f);
        }
        return healed;
    }
}
