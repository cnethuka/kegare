package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
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
            case NEXT_ABILITY -> InnateTechniqueService.cycle(player, 1);
            case PREVIOUS_ABILITY -> InnateTechniqueService.cycle(player, -1);
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
        Ability ability = slot == CastSlot.SELECTED
                ? innateTechnique.get().ability(InnateTechniqueService.get(player).selectedSlot())
                : innateTechnique.get().ultimate().orElse(null);
        if (ability == null) {
            feedback(player, slot == CastSlot.ULTIMATE ? CastResult.NO_ULTIMATE : CastResult.NO_INNATE_TECHNIQUE, null);
            return null;
        }
        return new Resolved(innateTechnique.get(), ability);
    }

    /** Shared checks. Every future innate technique goes through these. */
    public static CastResult validate(ServerPlayer player, AbilityContext ctx) {
        Ability ability = ctx.ability();
        if (state(player).casting != null) return CastResult.BUSY;
        if (CombatHelper.isStunned(player)) return CastResult.STUNNED;
        if (InnateTechniqueService.cooldownRemaining(player, ability) > 0) return CastResult.ON_COOLDOWN;
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
                if (!MaliceService.trySpend(player, ability.cost(player))) return;
                ability.execute(ctx);
                InnateTechniqueService.startCooldown(player, ability);
            }
            // Cost is checked now but paid on release.
            case CHARGED -> state.casting = new CastState.Active(ability, slot, now);
            case CHANNELED -> {
                if (!MaliceService.trySpend(player, ability.cost(player))) return;
                state.casting = new CastState.Active(ability, slot, now);
                ability.execute(ctx);
            }
            case TOGGLE -> {
                if (!MaliceService.trySpend(player, ability.cost(player))) return;
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
                    ability.tick(new AbilityContext(player, innateTechnique, ability, cast.slot, chargeFraction(player, cast), cast.activeTicks));
                } else if (ability.castType() == CastType.CHANNELED) {
                    if (!MaliceService.trySpend(player, ability.upkeepPerSecond(player) / 20.0)) {
                        endChannel(player, innateTechnique, cast, EndReason.OUT_OF_MALICE);
                    } else {
                        ability.tick(new AbilityContext(player, innateTechnique, ability, cast.slot, 1f, cast.activeTicks));
                        int max = ability.maxChannelTicks(player);
                        if (max > 0 && cast.activeTicks >= max) endChannel(player, innateTechnique, cast, EndReason.EXPIRED);
                    }
                }
            }
        }

        for (CastState.Active toggle : List.copyOf(state.toggles.values())) {
            toggle.activeTicks++;
            Ability ability = toggle.ability;
            if (!MaliceService.trySpend(player, ability.upkeepPerSecond(player) / 20.0)) {
                endToggle(player, innateTechnique, toggle, EndReason.OUT_OF_MALICE);
            } else {
                ability.tick(new AbilityContext(player, innateTechnique, ability, toggle.slot, 1f, toggle.activeTicks));
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
        // Toggle upkeep may have drained the reserve while charging: re-check at release.
        if (!MaliceService.trySpend(player, ability.cost(player))) {
            ability.onEnd(ctx, EndReason.OUT_OF_MALICE);
            feedback(player, CastResult.NOT_ENOUGH_MALICE, ability);
            return;
        }
        ability.execute(ctx);
        ability.onEnd(ctx, EndReason.RELEASED);
        InnateTechniqueService.startCooldown(player, ability);
    }

    private static void endChannel(ServerPlayer player, InnateTechnique innateTechnique, CastState.Active cast, EndReason reason) {
        state(player).casting = null;
        cast.ability.onEnd(new AbilityContext(player, innateTechnique, cast.ability, cast.slot, 1f, cast.activeTicks), reason);
        InnateTechniqueService.startCooldown(player, cast.ability);
        if (reason == EndReason.OUT_OF_MALICE) {
            player.displayClientMessage(Component.translatable("message.kegare.cast.out_of_malice", cast.ability.displayName()), true);
        }
        PlayerSync.markDirty(player);
    }

    private static void endToggle(ServerPlayer player, InnateTechnique innateTechnique, CastState.Active toggle, EndReason reason) {
        state(player).toggles.remove(toggle.ability.id());
        toggle.ability.onEnd(new AbilityContext(player, innateTechnique, toggle.ability, toggle.slot, 1f, toggle.activeTicks), reason);
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
            default -> Component.translatable(result.translationKey(), name);
        };
        player.displayClientMessage(message, true);
    }

    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
