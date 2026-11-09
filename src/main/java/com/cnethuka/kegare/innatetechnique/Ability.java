package com.cnethuka.kegare.innatetechnique;

import java.util.List;
import java.util.Optional;
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
 * <p>
 * <b>Specials.</b> An ability may define a {@link #special()}: a second ability (see {@link SpecialAbility}) cast
 * with R while this one is selected. It runs the same lifecycle and shared checks under its own cast type, plus its
 * {@link #requirements()}, and shares this ability's cooldown through {@link #cooldownKey()}.
 * Costs and cooldowns take the caster so future traits can scale them.
 */
public interface Ability {
    ResourceLocation id();

    CastType castType();

    /** Malice paid when the cast fires (INSTANT / CHARGED release) or starts (CHANNELED / TOGGLE). */
    double cost(ServerPlayer player);

    /**
     * What the HUD and Sorcerer screen quote. Usually just {@link #cost}; different for an ability whose press is free right
     * now because it would fizzle (R with no string) but which costs something once it can act.
     */
    default double shownCost(ServerPlayer player) {
        return cost(player);
    }

    /**
     * CHARGED only: what the release actually costs, for a charge of {@code charge} (0..1). Default: {@link #cost}.
     * Override when the cost grows with the charge (e.g. tiers); {@link #cost} is then the cheapest release, which is
     * what pressing the key checks and what the HUD shows.
     */
    default double releaseCost(ServerPlayer player, float charge) {
        return cost(player);
    }

    /** Cooldown in ticks, started after firing (INSTANT / CHARGED) or after ending (CHANNELED / TOGGLE). */
    int cooldownTicks(ServerPlayer player);

    /**
     * The cooldown the Sorcerer screen quotes. Usually {@link #cooldownTicks}; different for a two-stage ability, whose
     * first stage only has a moment's lockout.
     */
    default int shownCooldownTicks(ServerPlayer player) {
        return cooldownTicks(player);
    }

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

    /**
     * CHARGED only: checked at release, before anything is paid (e.g. "is there ground in range where I aim").
     * False: the cast fizzles with {@link #blockedMessage}, no cost and no cooldown.
     */
    default boolean canRelease(AbilityContext ctx) {
        return true;
    }

    /** Action-bar message when {@link #canCast} or {@link #canRelease} says no. */
    default Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.cast.blocked", displayName());
    }

    /** The effect. See the class docs for when it runs per cast type. */
    void execute(AbilityContext ctx);

    /** Every server tick while charging, channeling or toggled on. */
    default void tick(AbilityContext ctx) {}

    /** A charge, channel or toggle ended (not called for INSTANT). */
    default void onEnd(AbilityContext ctx, EndReason reason) {}

    /**
     * This ability's special (cast with R), if it has one. A special is an ability in its own right (own cast type,
     * cost, effect and visuals) that shares this ability's cooldown. Build one on {@link SpecialAbility}. Default: none.
     */
    default Optional<Ability> special() {
        return Optional.empty();
    }

    /** Specials only: the ability this is the special of. */
    default Optional<Ability> base() {
        return Optional.empty();
    }

    default boolean isSpecial() {
        return base().isPresent();
    }

    /**
     * The cooldown this ability starts and checks. A special uses its base ability's, so casting either one puts both
     * on cooldown.
     */
    default ResourceLocation cooldownKey() {
        return base().map(Ability::cooldownKey).orElse(id());
    }

    /** Conditions checked with the shared cast checks (see {@link Requirement}). Unmet: fizzle at no cost. */
    default List<Requirement> requirements() {
        return List.of();
    }

    /**
     * True for an ability with nothing to land on (it moves or orders your own things). It then counts for Refinement
     * and Assessment when used in a real fight, instead of when it hits. Anything that can hit leaves this false: its
     * landings are found on their own (see {@link com.cnethuka.kegare.progression.Landings}).
     */
    default boolean hasNoTarget() {
        return false;
    }

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

    /** Which tab it's under (DESIGN §3.16): an innate technique's own are {@link com.cnethuka.kegare.loadout.AbilityCategory#INNATE}. */
    default com.cnethuka.kegare.loadout.AbilityCategory category() {
        return com.cnethuka.kegare.loadout.AbilityCategory.INNATE;
    }

    /** What unlocks it, for its tile while it's locked (a shared ability's; the technique's own show the Refinement level instead). */
    default Component unlockHint() {
        return Component.translatable("gui.kegare.loadout.locked.desc");
    }

    default Component displayName() {
        return Component.translatable(descriptionId());
    }

    /** What its description's placeholders read: the configured numbers it states ({@code config/ConfigText}), in order. */
    default Object[] descriptionArgs() {
        return new Object[0];
    }
}
