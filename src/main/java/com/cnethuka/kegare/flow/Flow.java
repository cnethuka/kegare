package com.cnethuka.kegare.flow;

import com.cnethuka.kegare.api.ability.AbilityCategory;
import com.cnethuka.kegare.fists.SecondImpact;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.melee.Dash;
import com.cnethuka.kegare.melee.MeleeHits;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Flow (DESIGN §3.16): what it does to the body. Its strength is 1 while the ability is on; other sources (Background Flow, §3.31) offer a
 * strength of their own, and the strongest applies: they don't stack. At strength {@code s}:
 * <ul>
 *   <li>Offense: every hit you deal does {@code offense × s} more, on top of Reinforced Fists' bonus.</li>
 *   <li>Defense: you take {@code reductionPhysical × s} less physical damage and {@code reductionTechnique × s} less technique damage, and
 *   have {@code knockbackResistance × s} more knockback resistance. The void, /kill and starving are untouched.</li>
 *   <li>Movement: {@code movementSpeed × s} faster, and Dash recharges {@code dashRecharge × s} sooner.</li>
 *   <li>Innate Technique: its abilities cost {@code techniqueCost × s} less (casts, releases and upkeep).</li>
 * </ul>
 */
public final class Flow {
    public static final FlowAbility FLOW = SharedAbilities.registerForEveryone(new FlowAbility());
    /** Other sources of Flow (Background Flow): each says how strong it is on a player right now, 0 to 1. */
    private static final List<ToDoubleFunction<ServerPlayer>> SOURCES = new CopyOnWriteArrayList<>();

    private Flow() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Flow::onDamage);
        NeoForge.EVENT_BUS.addListener(Flow::onIncoming);
        CastManager.addAbilityCostModifier((player, ability, amount) -> ability.category() == AbilityCategory.INNATE
                ? amount * (1.0 - FlowConfig.TECHNIQUE_COST.get() * strength(player)) : amount);
        Dash.addRechargeModifier((player, ticks) -> (int) Math.round(ticks * (1.0 - FlowConfig.DASH_RECHARGE.get() * strength(player))));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            if (p.tickCount % 10 == 0) update(p);
            if (AuraTell.active(p)) AuraTell.sync(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer viewer) AuraTell.onStartTracking(viewer, e.getTarget());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) AuraTell.forget(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) AuraTell.forget(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> AuraTell.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> AuraTell.clear());
    }

    public static void addSource(ToDoubleFunction<ServerPlayer> source) {
        SOURCES.add(source);
    }

    /** How reinforced they are right now: 1 with the ability on, else the strongest other source, else 0. */
    public static double strength(ServerPlayer player) {
        double s = FlowAbility.isOn(player) ? 1.0 : 0.0;
        for (ToDoubleFunction<ServerPlayer> source : SOURCES) s = Math.max(s, source.applyAsDouble(player));
        return Math.max(0, Math.min(1, s));
    }

    /** Keeps the attribute modifiers (knockback resistance, speed) in step with how strong it is now (a source changed: call this). */
    public static void update(ServerPlayer player) {
        set(player, strength(player));
    }

    /** As {@link #update(ServerPlayer)}, with the ability just turned on or off. */
    static void update(ServerPlayer player, boolean abilityOn) {
        set(player, Math.max(abilityOn ? 1.0 : 0.0, backgroundStrength(player)));
    }

    /** Keeps the knockback resistance and the speed in step with the strength. */
    private static void set(ServerPlayer player, double s) {
        s = Math.max(0, Math.min(1, s));
        modifier(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), FlowIds.KNOCKBACK_MODIFIER, FlowConfig.KNOCKBACK_RESISTANCE.get() * s,
                AttributeModifier.Operation.ADD_VALUE);
        modifier(player.getAttribute(Attributes.MOVEMENT_SPEED), FlowIds.SPEED_MODIFIER, FlowConfig.MOVEMENT_SPEED.get() * s,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void modifier(AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (amount <= 0) {
            if (current != null) attribute.removeModifier(id);
        } else if (current == null || Math.abs(current.amount() - amount) > 1.0e-9) { // only when it changes: no resend every tick
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /**
     * Offense: more on every hit they deal, melee or weapon, projectile or technique, before the target's defences; at the same step as
     * Reinforced Fists' bonus, so the two multiply. A Second Impact is a share of a Strike that's already had it.
     */
    private static void onIncoming(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity() || SecondImpact.isSecondImpact(source)) return;
        double s = strength(attacker);
        if (s > 0) event.setAmount(event.getAmount() * (float) (1.0 + FlowConfig.OFFENSE.get() * s));
    }

    /** The strongest source other than the ability (Background Flow), 0 if none. */
    public static double backgroundStrength(ServerPlayer player) {
        double s = 0;
        for (ToDoubleFunction<ServerPlayer> source : SOURCES) s = Math.max(s, source.applyAsDouble(player));
        return s;
    }

    /** Less damage: after the game's own defences, as a share of what's left. */
    private static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        double s = strength(player);
        if (s <= 0) return;
        double share = reduction(event.getSource(), player.serverLevel()) * s;
        if (share > 0) event.setNewDamage(event.getNewDamage() * (float) (1.0 - share));
    }

    /** The full-strength reduction for a hit of this kind. */
    static double reduction(DamageSource source, ServerLevel level) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE)) return 0;
        return MeleeHits.of(source, level) == MeleeHits.Kind.TECHNIQUE ? FlowConfig.REDUCTION_TECHNIQUE.get()
                : FlowConfig.REDUCTION_PHYSICAL.get();
    }
}
