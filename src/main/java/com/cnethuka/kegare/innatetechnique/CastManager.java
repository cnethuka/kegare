package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.progression.CastScope;
import com.cnethuka.kegare.progression.Landings;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side cast pipeline. Clients send {@link CastIntent}s; everything else is validated here.
 * See {@link Ability} for the per-{@link CastType} lifecycle.
 */
public final class CastManager {
    private CastManager() {}

    public static CastState state(ServerPlayer player) {
        return player.getData(KegareAttachments.CAST_STATE);
    }

    // ------------------------------------------------------------------ intents

    /**
     * Gets first say over a key intent, before the cast pipeline (e.g. while possessing a puppet, R returns and nothing
     * else casts). Returns true when it handled the intent, which then goes no further.
     */
    @FunctionalInterface
    public interface Interceptor {
        boolean intercept(ServerPlayer player, CastIntent intent);
    }

    private static final List<Interceptor> INTERCEPTORS = new CopyOnWriteArrayList<>();

    public static void addInterceptor(Interceptor interceptor) {
        INTERCEPTORS.add(interceptor);
    }

    private static final List<Consumer<ServerPlayer>> START_LISTENERS = new CopyOnWriteArrayList<>();

    /** Told when a cast really starts: past every check and, unless it's a charge (which pays on release), paid for. */
    public static void addStartListener(Consumer<ServerPlayer> listener) {
        START_LISTENERS.add(listener);
    }

    private static void started(ServerPlayer player) {
        START_LISTENERS.forEach(listener -> listener.accept(player));
    }

    public static void handleIntent(ServerPlayer player, CastIntent intent) {
        for (Interceptor interceptor : INTERCEPTORS) {
            if (interceptor.intercept(player, intent)) return;
        }
        switch (intent) {
            case CAST_PRESS -> tryCast(player, CastSlot.SELECTED);
            case CAST_RELEASE -> release(player, CastSlot.SELECTED);
            case SPECIAL_PRESS -> tryCast(player, CastSlot.SPECIAL);
            case SPECIAL_RELEASE -> release(player, CastSlot.SPECIAL);
            case ULTIMATE_PRESS -> tryCast(player, CastSlot.ULTIMATE);
            case ULTIMATE_RELEASE -> release(player, CastSlot.ULTIMATE);
        }
    }

    private record Resolved(InnateTechnique innateTechnique, Ability ability) {}

    /** Finds the ability behind a key, or reports why there is none. */
    @Nullable
    private static Resolved resolve(ServerPlayer player, CastSlot slot) {
        if (!player.isAlive() || player.isSpectator()) return null;
        MaliceData malice = MaliceService.get(player);
        if (!malice.isBorn()) return null;
        if (malice.isSevered()) {
            feedback(player, CastResult.SEVERED, null);
            return null;
        }
        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (innateTechnique.isEmpty()) {
            feedback(player, CastResult.NO_INNATE_TECHNIQUE, null);
            return null;
        }
        // Regular keys cast what the loadout holds (a technique or shared ability); V is always the ultimate.
        Ability ability = switch (slot) {
            case SELECTED, SPECIAL -> LoadoutService.abilityAt(player, InnateTechniqueService.get(player).selectedSlot());
            case ULTIMATE -> innateTechnique.get().ultimate().orElse(null);
        };
        if (ability == null) {
            CastResult why = slot != CastSlot.ULTIMATE ? CastResult.EMPTY_SLOT
                    : innateTechnique.get().ultimateLocked() ? CastResult.ULTIMATE_LOCKED : CastResult.NO_ULTIMATE;
            feedback(player, why, null);
            return null;
        }
        // R: the selected ability's special. None defined: fizzle, nothing paid.
        if (slot == CastSlot.SPECIAL) {
            Optional<Ability> special = ability.special();
            if (special.isEmpty()) {
                feedback(player, CastResult.NO_SPECIAL, ability);
                return null;
            }
            ability = special.get();
        }
        return new Resolved(innateTechnique.get(), ability);
    }

    /** Shared checks. Every innate technique (base casts and specials alike) goes through these. */
    public static CastResult validate(ServerPlayer player, AbilityContext ctx) {
        Ability ability = ctx.ability();
        if (!com.cnethuka.kegare.progression.TechniqueUnlocks.isUnlocked(player, ability)) return CastResult.REFINEMENT_LOCKED;
        if (Burnout.blocks(player, ability)) return CastResult.BURNT_OUT;
        if (state(player).casting != null) return CastResult.BUSY;
        if (CombatHelper.isStunned(player)) return CastResult.STUNNED;
        if (InnateTechniqueService.cooldownRemaining(player, ability) > 0) return CastResult.ON_COOLDOWN;
        if (unmetRequirement(player, ability).isPresent()) return CastResult.REQUIREMENT_UNMET;
        if (!MaliceService.canAfford(player, ability.cost(player))) return CastResult.NOT_ENOUGH_MALICE;
        if (!ability.canCast(ctx)) return CastResult.BLOCKED;
        return CastResult.OK;
    }

    /**
     * The cast entry point (cast/ultimate key down). Resolves the ability server-side, runs the shared checks
     * (busy, stun, cooldown, cost, ability hook), then starts it per its {@link CastType}.
     * Pressing an active TOGGLE switches it off instead.
     */
    public static void tryCast(ServerPlayer player, CastSlot slot) {
        Resolved resolved = resolve(player, slot);
        if (resolved == null) return;
        Ability ability = resolved.ability();
        CastState state = state(player);

        // Pressing an active toggle switches it off (always allowed, even while stunned).
        CastState.Active activeToggle = state.toggles.get(ability.id());
        if (ability.castType() == CastType.TOGGLE && activeToggle != null) {
            endToggle(player, resolved.innateTechnique(), activeToggle, EndReason.RELEASED);
            return;
        }

        float startCharge = ability.castType() == CastType.CHARGED ? 0f : 1f;
        AbilityContext ctx = new AbilityContext(player, resolved.innateTechnique(), ability, slot, startCharge, 0);
        CastResult result = validate(player, ctx);
        if (result != CastResult.OK) {
            feedback(player, result, ability);
            return;
        }

        long now = player.level().getGameTime();
        switch (ability.castType()) {
            case INSTANT -> {
                if (!pay(player, ability, ability.cost(player))) return;
                started(player);
                state.noCooldown = false;
                CastScope.run(player, ability, () -> ability.execute(ctx));
                if (state.noCooldown) { // fizzled after paying (see fizzleKeepingCost): no cooldown, no cast visuals
                    state.noCooldown = false;
                } else {
                    wentOff(player, ability);
                    InnateTechniqueService.startCooldown(player, ability);
                    VisualService.ability(player, ability, VisualPhase.CAST);
                }
            }
            // Cost is checked now but paid on release.
            case CHARGED -> {
                started(player);
                state.casting = new CastState.Active(ability, slot, now);
                VisualService.ability(player, ability, VisualPhase.CHARGE_LOOP, ability.maxChargeTicks(player));
            }
            case CHANNELED -> {
                if (!pay(player, ability, ability.cost(player))) return;
                started(player);
                state.casting = new CastState.Active(ability, slot, now);
                CastScope.run(player, ability, () -> ability.execute(ctx));
                wentOff(player, ability);
                VisualService.ability(player, ability, VisualPhase.CAST);
                VisualService.ability(player, ability, VisualPhase.CHANNEL_LOOP);
            }
            case TOGGLE -> {
                if (!pay(player, ability, ability.cost(player))) return;
                started(player);
                state.toggles.put(ability.id(), new CastState.Active(ability, slot, now));
                CastScope.run(player, ability, () -> ability.execute(ctx));
                wentOff(player, ability);
                VisualService.ability(player, ability, VisualPhase.TOGGLE_ON);
                VisualService.ability(player, ability, VisualPhase.TOGGLE_LOOP);
                player.displayClientMessage(Component.translatable("message.kegare.toggle.on", ability.displayName()), true);
            }
        }
        PlayerSync.markDirty(player);
    }

    /** Pays for a cast, and tells the shared hit path what it cost. */
    /** Changes what an active toggle's upkeep drains each second (Sustained Flow, DESIGN §3.31). Never a start cost, never a channel. */
    @FunctionalInterface
    public interface ToggleUpkeepModifier {
        double modify(ServerPlayer player, Ability ability, double perSecond);
    }

    private static final java.util.List<ToggleUpkeepModifier> TOGGLE_UPKEEP = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addToggleUpkeepModifier(ToggleUpkeepModifier modifier) {
        TOGGLE_UPKEEP.add(modifier);
    }

    /** What an active toggle drains a second: its upkeep, then every modifier. */
    public static double toggleUpkeep(ServerPlayer player, Ability ability) {
        double perSecond = ability.upkeepPerSecond(player);
        for (ToggleUpkeepModifier modifier : TOGGLE_UPKEEP) perSecond = modifier.modify(player, ability, perSecond);
        return Math.max(0.0, perSecond);
    }

    private static boolean pay(ServerPlayer player, Ability ability, double amount) {
        if (!MaliceService.trySpend(player, amount)) return false;
        Landings.spent(player, ability, amount);
        com.cnethuka.kegare.impact.CastMalice.paid(player, ability, amount); // the Impact System's Malice input (DESIGN §3.23)
        return true;
    }

    /** The cast actually went off: an ability with no target of its own has now done all it will. */
    private static void wentOff(ServerPlayer player, Ability ability) {
        if (ability.hasNoTarget()) Landings.usedWithoutTarget(player, ability);
    }

    /**
     * Call from an INSTANT ability's {@code execute}: the cast fizzled after its Malice was paid (e.g. something
     * refused the effect at the last moment). The Malice stays spent, but no cooldown starts and the cast visuals
     * don't play.
     */
    public static void fizzleKeepingCost(ServerPlayer player) {
        state(player).noCooldown = true;
    }

    /** The first of the ability's {@link Requirement}s the player doesn't meet right now. */
    public static Optional<Requirement> unmetRequirement(ServerPlayer player, Ability ability) {
        for (Requirement requirement : ability.requirements()) {
            if (!requirement.isMet(player)) return Optional.of(requirement);
        }
        return Optional.empty();
    }

    public static boolean isToggleActive(ServerPlayer player, Ability toggle) {
        return state(player).toggles.containsKey(toggle.id());
    }

    /**
     * Ends the running channel from code as if it had run its course (e.g. a heal with nothing left to heal):
     * {@code onEnd} with {@link EndReason#EXPIRED}, then the cooldown. Safe to call from the channel's own tick.
     */
    public static void finishChannel(ServerPlayer player) {
        CastState.Active cast = state(player).casting;
        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (cast == null || cast.ability.castType() != CastType.CHANNELED || innateTechnique.isEmpty()) return;
        endChannel(player, innateTechnique.get(), cast, EndReason.EXPIRED);
    }

    /**
     * Switches a running TOGGLE off from code (e.g. a special that uses it up, with {@link EndReason#CONSUMED}).
     * It ends as usual: {@code onEnd}, then its cooldown. Returns whether it was on.
     */
    public static boolean switchOff(ServerPlayer player, Ability toggle, EndReason reason) {
        CastState.Active active = state(player).toggles.get(toggle.id());
        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (active == null || innateTechnique.isEmpty()) return false;
        endToggle(player, innateTechnique.get(), active, reason);
        return true;
    }

    private static void release(ServerPlayer player, CastSlot slot) {
        CastState state = state(player);
        CastState.Active active = state.casting;
        if (active == null || active.slot != slot) return;
        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (innateTechnique.isEmpty()) {
            state.casting = null;
            PlayerSync.markDirty(player);
            return;
        }
        switch (active.ability.castType()) {
            case CHARGED -> releaseCharge(player, innateTechnique.get(), active);
            case CHANNELED -> endChannel(player, innateTechnique.get(), active, EndReason.RELEASED);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ ticking

    /** Called every server tick for living players. */
    public static void tick(ServerPlayer player) {
        CastState state = state(player);
        if (state.isIdle()) return;
        Optional<InnateTechnique> current = InnateTechniqueService.current(player);
        if (current.isEmpty()) {
            stopAll(player, EndReason.CANCELLED);
            return;
        }
        InnateTechnique innateTechnique = current.get();

        CastState.Active cast = state.casting;
        if (cast != null) {
            if (CombatHelper.isStunned(player)) {
                interrupt(player);
            } else {
                cast.activeTicks++;
                Ability ability = cast.ability;
                if (ability.castType() == CastType.CHARGED) {
                    AbilityContext tickCtx = new AbilityContext(player, innateTechnique, ability, cast.slot, chargeFraction(player, cast), cast.activeTicks);
                    CastScope.run(player, ability, () -> ability.tick(tickCtx));
                } else if (ability.castType() == CastType.CHANNELED) {
                    if (unmetRequirement(player, ability).isPresent()) {
                        feedback(player, CastResult.REQUIREMENT_UNMET, ability);
                        endChannel(player, innateTechnique, cast, EndReason.CANCELLED);
                    } else if (!pay(player, ability, ability.upkeepPerSecond(player) / 20.0)) {
                        endChannel(player, innateTechnique, cast, EndReason.OUT_OF_MALICE);
                    } else {
                        AbilityContext tickCtx = new AbilityContext(player, innateTechnique, ability, cast.slot, 1f, cast.activeTicks);
                        CastScope.run(player, ability, () -> ability.tick(tickCtx));
                        int max = ability.maxChannelTicks(player);
                        // (the tick may have finished the channel itself)
                        if (state.casting == cast && max > 0 && cast.activeTicks >= max) endChannel(player, innateTechnique, cast, EndReason.EXPIRED);
                    }
                }
            }
        }

        for (CastState.Active toggle : List.copyOf(state.toggles.values())) {
            if (!state.toggles.containsValue(toggle)) continue; // another toggle's tick switched this one off
            toggle.activeTicks++;
            Ability ability = toggle.ability;
            if (unmetRequirement(player, ability).isPresent()) {
                feedback(player, CastResult.REQUIREMENT_UNMET, ability);
                endToggle(player, innateTechnique, toggle, EndReason.CANCELLED);
            } else if (!pay(player, ability, toggleUpkeep(player, ability) / 20.0)) {
                endToggle(player, innateTechnique, toggle, EndReason.OUT_OF_MALICE);
            } else {
                AbilityContext tickCtx = new AbilityContext(player, innateTechnique, ability, toggle.slot, 1f, toggle.activeTicks);
                CastScope.run(player, ability, () -> ability.tick(tickCtx));
            }
        }
    }

    // ------------------------------------------------------------------ ending

    /** Stun hook: cancels a charge (no cost, no cooldown) or ends a channel (with cooldown). Toggles keep running. */
    public static void interrupt(ServerPlayer player) {
        CastState state = state(player);
        CastState.Active cast = state.casting;
        if (cast == null) return;
        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (innateTechnique.isEmpty()) {
            state.casting = null;
            PlayerSync.markDirty(player);
            return;
        }
        if (cast.ability.castType() == CastType.CHANNELED) {
            endChannel(player, innateTechnique.get(), cast, EndReason.INTERRUPTED);
        } else {
            state.casting = null;
            VisualService.stopAbility(player, cast.ability, VisualPhase.CHARGE_LOOP);
            cast.ability.onEnd(new AbilityContext(player, innateTechnique.get(), cast.ability, cast.slot, chargeFraction(player, cast), cast.activeTicks), EndReason.INTERRUPTED);
            PlayerSync.markDirty(player);
        }
        player.displayClientMessage(Component.translatable("message.kegare.cast.interrupted", cast.ability.displayName()), true);
    }

    /** Ends everything (death, logout, innate technique change). Channels and toggles still start their cooldowns. */
    public static void stopAll(ServerPlayer player, EndReason reason) {
        CastState state = state(player);
        if (state.isIdle()) return;
        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (innateTechnique.isEmpty()) {
            state.casting = null;
            state.toggles.clear();
            PlayerSync.markDirty(player);
            return;
        }
        CastState.Active cast = state.casting;
        if (cast != null) {
            if (cast.ability.castType() == CastType.CHANNELED) {
                endChannel(player, innateTechnique.get(), cast, reason);
            } else {
                state.casting = null;
                VisualService.stopAbility(player, cast.ability, VisualPhase.CHARGE_LOOP);
                cast.ability.onEnd(new AbilityContext(player, innateTechnique.get(), cast.ability, cast.slot, chargeFraction(player, cast), cast.activeTicks), reason);
            }
        }
        for (CastState.Active toggle : List.copyOf(state.toggles.values())) {
            endToggle(player, innateTechnique.get(), toggle, reason);
        }
        PlayerSync.markDirty(player);
    }

    private static void releaseCharge(ServerPlayer player, InnateTechnique innateTechnique, CastState.Active cast) {
        CastState state = state(player);
        state.casting = null;
        PlayerSync.markDirty(player);
        Ability ability = cast.ability;
        VisualService.stopAbility(player, ability, VisualPhase.CHARGE_LOOP);
        int charged = chargedTicks(player, cast);
        AbilityContext ctx = new AbilityContext(player, innateTechnique, ability, cast.slot, chargeFraction(player, cast), charged);

        if (charged < ability.minChargeTicks(player)) {
            ability.onEnd(ctx, EndReason.CANCELLED);
            feedback(player, CastResult.CHARGE_TOO_SHORT, ability);
            return;
        }
        if (CombatHelper.isStunned(player)) {
            ability.onEnd(ctx, EndReason.INTERRUPTED);
            feedback(player, CastResult.STUNNED, ability);
            return;
        }
        // A requirement (e.g. a toggle the special needs) may have lapsed while charging.
        if (unmetRequirement(player, ability).isPresent()) {
            ability.onEnd(ctx, EndReason.CANCELLED);
            feedback(player, CastResult.REQUIREMENT_UNMET, ability);
            return;
        }
        // Ability-specific release check (e.g. a target in range): fizzles for free.
        if (!ability.canRelease(ctx)) {
            ability.onEnd(ctx, EndReason.CANCELLED);
            feedback(player, CastResult.BLOCKED, ability);
            return;
        }
        // Toggle upkeep may have drained the reserve while charging, and a charge may cost more the longer it was
        // held (Ability#releaseCost): re-check at release.
        double cost = ability.releaseCost(player, ctx.charge());
        if (!pay(player, ability, cost)) {
            ability.onEnd(ctx, EndReason.OUT_OF_MALICE);
            player.displayClientMessage(Component.translatable(CastResult.NOT_ENOUGH_MALICE.translationKey(),
                    Integer.toString((int) Math.ceil(cost - 1.0e-6)), Integer.toString((int) Math.floor(MaliceService.get(player).current()))), true);
            return;
        }
        CastScope.run(player, ability, () -> ability.execute(ctx));
        wentOff(player, ability);
        ability.onEnd(ctx, EndReason.RELEASED);
        InnateTechniqueService.startCooldown(player, ability);
        VisualService.ability(player, ability, VisualPhase.RELEASE);
    }

    private static void endChannel(ServerPlayer player, InnateTechnique innateTechnique, CastState.Active cast, EndReason reason) {
        state(player).casting = null;
        VisualService.stopAbility(player, cast.ability, VisualPhase.CHANNEL_LOOP);
        AbilityContext endCtx = new AbilityContext(player, innateTechnique, cast.ability, cast.slot, 1f, cast.activeTicks);
        CastScope.run(player, cast.ability, () -> cast.ability.onEnd(endCtx, reason));
        InnateTechniqueService.startCooldown(player, cast.ability);
        if (reason == EndReason.OUT_OF_MALICE) {
            player.displayClientMessage(Component.translatable("message.kegare.cast.out_of_malice", cast.ability.displayName()), true);
        }
        PlayerSync.markDirty(player);
    }

    private static void endToggle(ServerPlayer player, InnateTechnique innateTechnique, CastState.Active toggle, EndReason reason) {
        state(player).toggles.remove(toggle.ability.id());
        VisualService.stopAbility(player, toggle.ability, VisualPhase.TOGGLE_LOOP);
        if (reason != EndReason.DEATH && reason != EndReason.LOGOUT && reason != EndReason.DIMENSION_CHANGE && reason != EndReason.CONSUMED) {
            VisualService.ability(player, toggle.ability, VisualPhase.TOGGLE_OFF);
        }
        AbilityContext endCtx = new AbilityContext(player, innateTechnique, toggle.ability, toggle.slot, 1f, toggle.activeTicks);
        CastScope.run(player, toggle.ability, () -> toggle.ability.onEnd(endCtx, reason));
        InnateTechniqueService.startCooldown(player, toggle.ability);
        if (reason == EndReason.RELEASED) {
            player.displayClientMessage(Component.translatable("message.kegare.toggle.off", toggle.ability.displayName()), true);
        } else if (reason == EndReason.OUT_OF_MALICE) {
            player.displayClientMessage(Component.translatable("message.kegare.cast.out_of_malice", toggle.ability.displayName()), true);
        }
        PlayerSync.markDirty(player);
    }

    private static int chargedTicks(ServerPlayer player, CastState.Active cast) {
        return (int) Math.max(0, player.level().getGameTime() - cast.startTick);
    }

    private static float chargeFraction(ServerPlayer player, CastState.Active cast) {
        int max = Math.max(1, cast.ability.maxChargeTicks(player));
        return Math.min(1f, chargedTicks(player, cast) / (float) max);
    }

    // ------------------------------------------------------------------ feedback

    private static void feedback(ServerPlayer player, CastResult result, @Nullable Ability ability) {
        if (result.translationKey() == null) return;
        Component name = ability != null ? ability.displayName() : Component.empty();
        Component message = switch (result) {
            case ON_COOLDOWN -> Component.translatable(result.translationKey(), name,
                    seconds(ability == null ? 0 : InnateTechniqueService.cooldownRemaining(player, ability)));
            case NOT_ENOUGH_MALICE -> Component.translatable(result.translationKey(),
                    Integer.toString((int) Math.ceil((ability == null ? 0 : ability.cost(player)) - 1.0e-6)),
                    Integer.toString((int) Math.floor(MaliceService.get(player).current())));
            case STUNNED -> Component.translatable(result.translationKey(), seconds(CombatHelper.stunTicksRemaining(player)));
            case BURNT_OUT -> Component.translatable(result.translationKey(), seconds(Burnout.remaining(player)));
            case BLOCKED -> ability != null ? ability.blockedMessage(player) : Component.translatable(result.translationKey(), name);
            case REQUIREMENT_UNMET -> Component.translatable(result.translationKey(), name,
                    ability == null ? Component.empty() : unmetRequirement(player, ability).map(Requirement::description).orElse(Component.empty()));
            case REFINEMENT_LOCKED -> Component.translatable(result.translationKey(),
                    ability == null ? 0 : com.cnethuka.kegare.progression.TechniqueUnlocks.requiredLevel(player, ability));
            default -> Component.translatable(result.translationKey(), name);
        };
        player.displayClientMessage(message, true);
    }

    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
