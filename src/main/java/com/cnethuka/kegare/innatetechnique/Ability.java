package com.cnethuka.kegare.innatetechnique;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * One castable ability. All methods run on the logical server; the client only sends intents.
 * <p>
 * Lifecycle per {@link CastType} (enforced by {@link CastManager}):
 * <ul>
 *   <li>INSTANT: press, then validate, pay {@link #cost}, {@link #execute}, cooldown.</li>
 *   <li>CHARGED: press, then validate and start charging ({@link #tick} each tick). On release, if charged for at
 *       least {@link #minChargeTicks}, it pays {@link #cost}, calls {@link #execute} with {@code ctx.charge()} in
 *       0..1, calls {@link #onEnd} (RELEASED), then starts the cooldown. Early release or a stun:
 *       {@link #onEnd} without cost or cooldown.</li>
 *   <li>CHANNELED: press, then validate, pay {@link #cost}, {@link #execute} (start), then each tick pays
 *       {@link #upkeepPerSecond}/20 and calls {@link #tick}. Release, out of Malice, max duration or stun:
 *       {@link #onEnd}, then cooldown.</li>
 *   <li>TOGGLE: press when off, then validate, pay {@link #cost}, {@link #execute} (on), then each tick pays
 *       upkeep and calls {@link #tick}. Press again or run out of Malice: {@link #onEnd}, then cooldown.
 *       Stuns do not switch toggles off.</li>
 * </ul>
 * Toggles and channels are runtime-only: they end on death and logout and never persist.
 * Costs and cooldowns take the caster so future traits can scale them.
 */
public interface Ability {
    ResourceLocation id();

    CastType castType();

    /** Malice paid when the cast fires (INSTANT / CHARGED release) or starts (CHANNELED / TOGGLE). */
    double cost(ServerPlayer player);

    /** Cooldown in ticks, started after firing (INSTANT / CHARGED) or after ending (CHANNELED / TOGGLE). */
    int cooldownTicks(ServerPlayer player);

    /** Malice drained per second while a CHANNELED or TOGGLE ability is running. */
    default double upkeepPerSecond(ServerPlayer player) {
        return 0;
    }

    /** CHARGED only: release before this many ticks cancels the cast. */
    default int minChargeTicks(ServerPlayer player) {
        return 0;
    }

    /** CHARGED only: ticks to reach full charge (charge fraction caps at 1). */
    default int maxChargeTicks(ServerPlayer player) {
        return 20;
    }

    /** CHANNELED only: max duration in ticks; 0 = until released or out of Malice. */
    default int maxChannelTicks(ServerPlayer player) {
        return 0;
    }

    /** Extra ability-specific validation, checked after the shared checks. */
    default boolean canCast(AbilityContext ctx) {
        return true;
    }

    /** The effect. See the class docs for when it runs per cast type. */
    void execute(AbilityContext ctx);

    /** Every server tick while charging, channeling or toggled on. */
    default void tick(AbilityContext ctx) {}

    /** A charge, channel or toggle ended (not called for INSTANT). */
    default void onEnd(AbilityContext ctx, EndReason reason) {}

    /**
     * Cosmetics per phase (player animations and VFX). Sent to clients when the server acts; never waited on.
     * See {@link com.cnethuka.kegare.innatetechnique.visual.VisualPhase} for when each phase plays.
     */
    default com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals visuals() {
        return com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals.NONE;
    }

    default String descriptionId() {
        return Util.makeDescriptionId("ability", id());
    }

    default Component displayName() {
        return Component.translatable(descriptionId());
    }
}
