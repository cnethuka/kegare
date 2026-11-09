package com.cnethuka.kegare.reinforcement;

import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.melee.MeleeHits;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Reinforcement (DESIGN §3.16): what it does to the body. Its strength is 1 while the ability is on; other sources (Background
 * Reinforcement, §3.31) offer a strength of their own, and the strongest applies: they don't stack. At strength {@code s} you take
 * {@code reductionPhysical × s} less physical damage, {@code reductionTechnique × s} less technique damage, and have
 * {@code knockbackResistance × s} more knockback resistance. The void, /kill and starving are untouched.
 */
public final class Reinforcement {
    public static final ReinforcementAbility REINFORCEMENT = SharedAbilities.registerForEveryone(new ReinforcementAbility());
    /** Other sources of reinforcement (Background Reinforcement): each says how strong it is on a player right now, 0 to 1. */
    private static final List<ToDoubleFunction<ServerPlayer>> SOURCES = new CopyOnWriteArrayList<>();

    private Reinforcement() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, Reinforcement::onDamage);
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
        double s = ReinforcementAbility.isOn(player) ? 1.0 : 0.0;
        for (ToDoubleFunction<ServerPlayer> source : SOURCES) s = Math.max(s, source.applyAsDouble(player));
        return Math.max(0, Math.min(1, s));
    }

    /** Keeps the knockback resistance in step with how reinforced they are now (a source changed: call this). */
    public static void update(ServerPlayer player) {
        set(player, strength(player));
    }

    /** As {@link #update(ServerPlayer)}, with the ability just turned on or off. */
    static void update(ServerPlayer player, boolean abilityOn) {
        set(player, Math.max(abilityOn ? 1.0 : 0.0, backgroundStrength(player)));
    }

    /** Keeps the knockback resistance in step with the strength. */
    private static void set(ServerPlayer player, double s) {
        AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb == null) return;
        double amount = ReinforcementConfig.KNOCKBACK_RESISTANCE.get() * Math.max(0, Math.min(1, s));
        AttributeModifier current = kb.getModifier(ReinforcementIds.KNOCKBACK_MODIFIER);
        if (amount <= 0) {
            if (current != null) kb.removeModifier(ReinforcementIds.KNOCKBACK_MODIFIER);
        } else if (current == null || Math.abs(current.amount() - amount) > 1.0e-9) { // only when it changes: no resend every tick
            kb.addOrUpdateTransientModifier(new AttributeModifier(ReinforcementIds.KNOCKBACK_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** The strongest source other than the ability (Background Reinforcement), 0 if none. */
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
        return MeleeHits.of(source, level) == MeleeHits.Kind.TECHNIQUE ? ReinforcementConfig.REDUCTION_TECHNIQUE.get()
                : ReinforcementConfig.REDUCTION_PHYSICAL.get();
    }
}
