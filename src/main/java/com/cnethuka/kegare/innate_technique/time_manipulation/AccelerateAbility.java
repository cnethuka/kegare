package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.CooldownRates;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.sound.KegareSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Accelerate (TOGGLE): while it's on, Speed, faster attacks, and every OTHER cooldown of your innate technique runs {@code cooldownRate}
 * times as fast (its own doesn't run while it's on anyway). Malice upkeep per second. A winding click switches it on; a quick tick
 * while it runs, and faint afterimages (the client draws them from its loop). Special: Fast Forward.
 */
public final class AccelerateAbility extends TimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .toggleOn(TimeIds.ANIM_ACCELERATE_TOGGLE, null)
            .toggleLoop(null, TimeIds.FX_ACCELERATE)
            .build();
    static final ResourceLocation ATTACK_SPEED = Kegare.id("accelerate");
    static final String RATE = "accelerate";
    /** How long each Speed it puts on lasts; it's put on again before it runs out. */
    private static final int SPEED_TICKS = 30;
    /** When each player switched it on (for Overclock). */
    private static final Map<UUID, Long> ON_SINCE = new HashMap<>();

    AccelerateAbility() {
        super("accelerate", CastType.TOGGLE);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.ACCELERATE_COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return TimeConfig.ACCELERATE_UPKEEP.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.ACCELERATE_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public Optional<Ability> special() { return Optional.of(TimeManipulation.FAST_FORWARD); }
    @Override public boolean hasNoTarget() { return true; }

    public boolean isActive(ServerPlayer player) {
        return CastManager.state(player).toggles().containsKey(id());
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        speed(p);
        AttributeInstance attack = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null) attack.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_SPEED, TimeConfig.ACCELERATE_ATTACK_SPEED.get(),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        InnateTechnique technique = ctx.innateTechnique();
        CooldownRates.set(p, new CooldownRates.Rate(RATE, TimeConfig.ACCELERATE_COOLDOWN_RATE.get(),
                key -> !key.equals(id()) && technique.findAbility(key).isPresent(), Long.MAX_VALUE));
        ON_SINCE.put(p.getUUID(), p.level().getGameTime());
        TimeManipulation.sound(p, KegareSounds.TIME_WIND, 1f, 1f);
    }

    @Override
    public void tick(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        long now = p.level().getGameTime();
        if (now % 10 == 0) speed(p);
        if (now % 5 == 0) TimeManipulation.sound(p, KegareSounds.TIME_TICK, 0.35f, 1.6f); // the clock running fast
        Long since = ON_SINCE.get(p.getUUID());
        if (since != null && now - since >= TimeAdvancements.OVERCLOCK_TICKS) TimeAdvancements.award(p, TimeAdvancements.OVERCLOCK);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        ServerPlayer p = ctx.player();
        MobEffectInstance speed = p.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed != null && speed.getDuration() <= SPEED_TICKS && speed.getAmplifier() == TimeConfig.ACCELERATE_SPEED_AMPLIFIER.get()) {
            p.removeEffect(MobEffects.MOVEMENT_SPEED); // ours (one from a potion would last longer)
        }
        AttributeInstance attack = p.getAttribute(Attributes.ATTACK_SPEED);
        if (attack != null) attack.removeModifier(ATTACK_SPEED);
        CooldownRates.remove(p, RATE);
        ON_SINCE.remove(p.getUUID());
    }

    static void forget(UUID player) {
        ON_SINCE.remove(player);
    }

    private static void speed(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_TICKS, TimeConfig.ACCELERATE_SPEED_AMPLIFIER.get(), false, false, true));
    }

    /** Fast Forward: {@code ticks} off every cooldown of the technique except this one's and the ultimate's. Returns how many it touched. */
    static int fastForward(ServerPlayer p, InnateTechnique technique, long ticks) {
        ResourceLocation ultimate = technique.ultimate().map(Ability::cooldownKey).orElse(null);
        java.util.Set<ResourceLocation> keys = new java.util.HashSet<>();
        for (Ability a : technique.everyAbility()) keys.add(a.cooldownKey());
        keys.remove(TimeManipulation.ACCELERATE.cooldownKey());
        keys.remove(ultimate);
        int touched = 0;
        for (ResourceLocation key : keys) {
            if (InnateTechniqueService.get(p).cooldowns().containsKey(key)) touched++;
            InnateTechniqueService.shiftCooldown(p, key, -ticks, true);
        }
        return touched;
    }
}
