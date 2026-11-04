package com.cnethuka.kegare.technique;

import com.cnethuka.kegare.ce.CeData;
import com.cnethuka.kegare.ce.CeService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
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

    public static void handleIntent(ServerPlayer player, CastIntent intent) {
        switch (intent) {
            case CAST_PRESS -> tryCast(player, CastSlot.SELECTED);
            case CAST_RELEASE -> release(player, CastSlot.SELECTED);
            case ULTIMATE_PRESS -> tryCast(player, CastSlot.ULTIMATE);
            case ULTIMATE_RELEASE -> release(player, CastSlot.ULTIMATE);
            case NEXT_ABILITY -> TechniqueService.cycle(player, 1);
            case PREVIOUS_ABILITY -> TechniqueService.cycle(player, -1);
        }
    }

    private record Resolved(Technique technique, Ability ability) {}

    /** Finds the ability behind a key, or reports why there is none. */
    @Nullable
    private static Resolved resolve(ServerPlayer player, CastSlot slot) {
        if (!player.isAlive() || player.isSpectator()) return null;
        CeData ce = CeService.get(player);
        if (!ce.isBorn()) return null;
        if (ce.isSevered()) {
            feedback(player, CastResult.SEVERED, null);
            return null;
        }
        Optional<Technique> technique = TechniqueService.current(player);
        if (technique.isEmpty()) {
            feedback(player, CastResult.NO_TECHNIQUE, null);
            return null;
        }
        Ability ability = slot == CastSlot.SELECTED
                ? technique.get().ability(TechniqueService.get(player).selectedSlot())
                : technique.get().ultimate().orElse(null);
        if (ability == null) {
            feedback(player, slot == CastSlot.ULTIMATE ? CastResult.NO_ULTIMATE : CastResult.NO_TECHNIQUE, null);
            return null;
        }
        return new Resolved(technique.get(), ability);
    }

    /** Shared checks. Every future technique goes through these. */
    public static CastResult validate(ServerPlayer player, AbilityContext ctx) {
        Ability ability = ctx.ability();
        if (state(player).casting != null) return CastResult.BUSY;
        if (CombatHelper.isStunned(player)) return CastResult.STUNNED;
        if (TechniqueService.cooldownRemaining(player, ability) > 0) return CastResult.ON_COOLDOWN;
        if (!CeService.canAfford(player, ability.cost(player))) return CastResult.NOT_ENOUGH_CE;
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
            endToggle(player, resolved.technique(), activeToggle, EndReason.RELEASED);
            return;
        }

        float startCharge = ability.castType() == CastType.CHARGED ? 0f : 1f;
        AbilityContext ctx = new AbilityContext(player, resolved.technique(), ability, slot, startCharge, 0);
        CastResult result = validate(player, ctx);
        if (result != CastResult.OK) {
            feedback(player, result, ability);
            return;
        }

        long now = player.level().getGameTime();
        switch (ability.castType()) {
            case INSTANT -> {
                if (!CeService.trySpend(player, ability.cost(player))) return;
                ability.execute(ctx);
                TechniqueService.startCooldown(player, ability);
            }
            // Cost is checked now but paid on release.
            case CHARGED -> state.casting = new CastState.Active(ability, slot, now);
            case CHANNELED -> {
                if (!CeService.trySpend(player, ability.cost(player))) return;
                state.casting = new CastState.Active(ability, slot, now);
                ability.execute(ctx);
            }
            case TOGGLE -> {
                if (!CeService.trySpend(player, ability.cost(player))) return;
                state.toggles.put(ability.id(), new CastState.Active(ability, slot, now));
                ability.execute(ctx);
                player.displayClientMessage(Component.translatable("message.kegare.toggle.on", ability.displayName()), true);
            }
        }
        PlayerSync.markDirty(player);
    }

    private static void release(ServerPlayer player, CastSlot slot) {
        CastState state = state(player);
        CastState.Active active = state.casting;
        if (active == null || active.slot != slot) return;
        Optional<Technique> technique = TechniqueService.current(player);
        if (technique.isEmpty()) {
            state.casting = null;
            PlayerSync.markDirty(player);
            return;
        }
        switch (active.ability.castType()) {
            case CHARGED -> releaseCharge(player, technique.get(), active);
            case CHANNELED -> endChannel(player, technique.get(), active, EndReason.RELEASED);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ ticking

    /** Called every server tick for living players. */
    public static void tick(ServerPlayer player) {
        CastState state = state(player);
        if (state.isIdle()) return;
        Optional<Technique> technique = TechniqueService.current(player);
        if (technique.isEmpty()) {
            stopAll(player, EndReason.CANCELLED);
            return;
        }
        Technique tech = technique.get();

        CastState.Active cast = state.casting;
        if (cast != null) {
            if (CombatHelper.isStunned(player)) {
                interrupt(player);
            } else {
                cast.activeTicks++;
                Ability ability = cast.ability;
                if (ability.castType() == CastType.CHARGED) {
                    ability.tick(new AbilityContext(player, tech, ability, cast.slot, chargeFraction(player, cast), cast.activeTicks));
                } else if (ability.castType() == CastType.CHANNELED) {
                    if (!CeService.trySpend(player, ability.upkeepPerSecond(player) / 20.0)) {
                        endChannel(player, tech, cast, EndReason.OUT_OF_CE);
                    } else {
                        ability.tick(new AbilityContext(player, tech, ability, cast.slot, 1f, cast.activeTicks));
                        int max = ability.maxChannelTicks(player);
                        if (max > 0 && cast.activeTicks >= max) endChannel(player, tech, cast, EndReason.EXPIRED);
                    }
                }
            }
        }

        for (CastState.Active toggle : List.copyOf(state.toggles.values())) {
            toggle.activeTicks++;
            Ability ability = toggle.ability;
            if (!CeService.trySpend(player, ability.upkeepPerSecond(player) / 20.0)) {
                endToggle(player, tech, toggle, EndReason.OUT_OF_CE);
            } else {
                ability.tick(new AbilityContext(player, tech, ability, toggle.slot, 1f, toggle.activeTicks));
            }
        }
    }

    // ------------------------------------------------------------------ ending

    /** Stun hook: cancels a charge (no cost, no cooldown) or ends a channel (with cooldown). Toggles keep running. */
    public static void interrupt(ServerPlayer player) {
        CastState state = state(player);
        CastState.Active cast = state.casting;
        if (cast == null) return;
        Optional<Technique> technique = TechniqueService.current(player);
        if (technique.isEmpty()) {
            state.casting = null;
            PlayerSync.markDirty(player);
            return;
        }
        if (cast.ability.castType() == CastType.CHANNELED) {
            endChannel(player, technique.get(), cast, EndReason.INTERRUPTED);
        } else {
            state.casting = null;
            cast.ability.onEnd(new AbilityContext(player, technique.get(), cast.ability, cast.slot, chargeFraction(player, cast), cast.activeTicks), EndReason.INTERRUPTED);
            PlayerSync.markDirty(player);
        }
        player.displayClientMessage(Component.translatable("message.kegare.cast.interrupted", cast.ability.displayName()), true);
    }

    /** Ends everything (death, logout, technique change). Channels and toggles still start their cooldowns. */
    public static void stopAll(ServerPlayer player, EndReason reason) {
        CastState state = state(player);
        if (state.isIdle()) return;
        Optional<Technique> technique = TechniqueService.current(player);
        if (technique.isEmpty()) {
            state.casting = null;
            state.toggles.clear();
            PlayerSync.markDirty(player);
            return;
        }
        CastState.Active cast = state.casting;
        if (cast != null) {
            if (cast.ability.castType() == CastType.CHANNELED) {
                endChannel(player, technique.get(), cast, reason);
            } else {
                state.casting = null;
                cast.ability.onEnd(new AbilityContext(player, technique.get(), cast.ability, cast.slot, chargeFraction(player, cast), cast.activeTicks), reason);
            }
        }
        for (CastState.Active toggle : List.copyOf(state.toggles.values())) {
            endToggle(player, technique.get(), toggle, reason);
        }
        PlayerSync.markDirty(player);
    }

    private static void releaseCharge(ServerPlayer player, Technique technique, CastState.Active cast) {
        CastState state = state(player);
        state.casting = null;
        PlayerSync.markDirty(player);
        Ability ability = cast.ability;
        int charged = chargedTicks(player, cast);
        AbilityContext ctx = new AbilityContext(player, technique, ability, cast.slot, chargeFraction(player, cast), charged);

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
        // Toggle upkeep may have drained the reserve while charging: re-check at release.
        if (!CeService.trySpend(player, ability.cost(player))) {
            ability.onEnd(ctx, EndReason.OUT_OF_CE);
            feedback(player, CastResult.NOT_ENOUGH_CE, ability);
            return;
        }
        ability.execute(ctx);
        ability.onEnd(ctx, EndReason.RELEASED);
        TechniqueService.startCooldown(player, ability);
    }

    private static void endChannel(ServerPlayer player, Technique technique, CastState.Active cast, EndReason reason) {
        state(player).casting = null;
        cast.ability.onEnd(new AbilityContext(player, technique, cast.ability, cast.slot, 1f, cast.activeTicks), reason);
        TechniqueService.startCooldown(player, cast.ability);
        if (reason == EndReason.OUT_OF_CE) {
            player.displayClientMessage(Component.translatable("message.kegare.cast.out_of_ce", cast.ability.displayName()), true);
        }
        PlayerSync.markDirty(player);
    }

    private static void endToggle(ServerPlayer player, Technique technique, CastState.Active toggle, EndReason reason) {
        state(player).toggles.remove(toggle.ability.id());
        toggle.ability.onEnd(new AbilityContext(player, technique, toggle.ability, toggle.slot, 1f, toggle.activeTicks), reason);
        TechniqueService.startCooldown(player, toggle.ability);
        if (reason == EndReason.RELEASED) {
            player.displayClientMessage(Component.translatable("message.kegare.toggle.off", toggle.ability.displayName()), true);
        } else if (reason == EndReason.OUT_OF_CE) {
            player.displayClientMessage(Component.translatable("message.kegare.cast.out_of_ce", toggle.ability.displayName()), true);
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
                    seconds(ability == null ? 0 : TechniqueService.cooldownRemaining(player, ability)));
            case NOT_ENOUGH_CE -> Component.translatable(result.translationKey(),
                    Integer.toString((int) Math.ceil((ability == null ? 0 : ability.cost(player)) - 1.0e-6)),
                    Integer.toString((int) Math.floor(CeService.get(player).current())));
            case STUNNED -> Component.translatable(result.translationKey(), seconds(CombatHelper.stunTicksRemaining(player)));
            default -> Component.translatable(result.translationKey(), name);
        };
        player.displayClientMessage(message, true);
    }

    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
