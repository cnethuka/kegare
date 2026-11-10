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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Accelerate (TOGGLE, DESIGN §3.26): your own clock runs {@code rate} times as fast. Walking, sprinting and swimming (attribute
 * modifiers, not a potion), attack speed, mining speed, item use ({@link #onUseTick}) and every OTHER cooldown of your technique all
 * take that factor; its own cooldown doesn't run while it's on anyway. Full control with no momentum, unlike Blur; jumping and gravity
 * are unchanged. Malice upkeep per second. A winding click on; while it runs, a quick tick and faint afterimages (drawn by the client
 * from its loop). Special: Fast Forward.
 */
public final class AccelerateAbility extends TimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .toggleOn(TimeIds.ANIM_ACCELERATE_TOGGLE, null)
            .toggleLoop(null, TimeIds.FX_ACCELERATE)
            .build();
    /** The id of every modifier it puts on (movement, swimming, attack and mining speed). */
    static final ResourceLocation MODIFIER = Kegare.id("accelerate");
    static final String RATE = "accelerate";
    /** When each player switched it on (for Overclock). */
    private static final Map<UUID, Long> ON_SINCE = new HashMap<>();
    /** Fractional extra item-use ticks carried over from a non-whole rate, per entity (client and server each keep their own). */
    private static final Map<LivingEntity, Double> USE_CARRY = new WeakHashMap<>();

    AccelerateAbility() {
        super("accelerate", CastType.TOGGLE);
    }

    @Override public double cost(ServerPlayer p) { return TimeConfig.ACCELERATE_COST.get(); }
    @Override
    public Object[] descriptionArgs() {
        return new Object[] {com.cnethuka.kegare.config.ConfigText.number(TimeConfig.ACCELERATE_RATE)};
    }
    @Override public double upkeepPerSecond(ServerPlayer p) { return TimeConfig.ACCELERATE_UPKEEP.get(); }
    @Override public int cooldownTicks(ServerPlayer p) { return TimeConfig.ticks(TimeConfig.ACCELERATE_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public Optional<Ability> special() { return Optional.of(TimeManipulation.FAST_FORWARD); }
    @Override public boolean hasNoTarget() { return true; }

    public boolean isActive(ServerPlayer player) {
        return CastManager.state(player).toggles().containsKey(id());
    }

    /** The attributes its clock speeds up, each by the same factor. */
    private static List<Holder<Attribute>> attributes() {
        return List.of(Attributes.MOVEMENT_SPEED, NeoForgeMod.SWIM_SPEED, Attributes.ATTACK_SPEED, Attributes.BLOCK_BREAK_SPEED);
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        double rate = TimeConfig.ACCELERATE_RATE.get();
        for (Holder<Attribute> attribute : attributes()) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null) instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, rate - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        InnateTechnique technique = ctx.innateTechnique();
        CooldownRates.set(p, new CooldownRates.Rate(RATE, rate, key -> !key.equals(id()) && technique.findAbility(key).isPresent(), Long.MAX_VALUE));
        ON_SINCE.put(p.getUUID(), p.level().getGameTime());
        TimeManipulation.sound(p, KegareSounds.TIME_WIND, 1f, 1f);
    }

    @Override
    public void tick(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        long now = p.level().getGameTime();
        if (now % 5 == 0) { // fast ticking, heard where each listener sees them (too fast for the eye, §3.30)
            com.cnethuka.kegare.sight.DelayedSight.playSound(p, KegareSounds.TIME_TICK, net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 1.6f);
        }
        Long since = ON_SINCE.get(p.getUUID());
        if (since != null && now - since >= TimeAdvancements.OVERCLOCK_TICKS) TimeAdvancements.award(p, TimeAdvancements.OVERCLOCK);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        ServerPlayer p = ctx.player();
        for (Holder<Attribute> attribute : attributes()) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null) instance.removeModifier(MODIFIER);
        }
        CooldownRates.remove(p, RATE);
        ON_SINCE.remove(p.getUUID());
        USE_CARRY.remove(p);
    }

    static void forget(UUID player) {
        ON_SINCE.remove(player);
    }

    /**
     * This entity's clock rate: Accelerate's rate while it's on, else 1. Read from the movement modifier, which the game already syncs
     * to every client, so client-side item use keeps step with the server without a new packet.
     */
    public static double rate(LivingEntity entity) {
        AttributeInstance movement = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier ours = movement == null ? null : movement.getModifier(MODIFIER);
        return ours == null ? 1.0 : 1.0 + ours.amount();
    }

    /**
     * Item use (eating, drinking, a bow, a crossbow, a shield) counts down {@code rate} times as fast, on both sides. Never below 1
     * left: the game finishes the use itself when it counts off the last tick.
     */
    static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        LivingEntity entity = event.getEntity();
        double extra = rate(entity) - 1 + USE_CARRY.getOrDefault(entity, 0.0);
        if (extra <= 1.0e-6) {
            USE_CARRY.remove(entity);
            return;
        }
        int whole = (int) extra;
        USE_CARRY.put(entity, extra - whole);
        if (whole > 0) event.setDuration(Math.max(1, event.getDuration() - whole));
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
