package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.FightTracker;
import com.cnethuka.kegare.progression.MeleeMoves;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Reinforcement, held: the first moments are the perfect window, and after that it's a guard that drains Malice. Letting go costs
 * a recovery with no parry at all, so mashing it leaves you open. All of it is timed here on the server, from when the press
 * arrived, and a modified client can't get a second window without sitting out the recovery.
 */
public final class Parry {
    private Parry() {}

    static void press(ServerPlayer player) {
        MeleeState state = MeleeLayer.state(player);
        long now = player.level().getGameTime();
        if (state.guarding || now < state.recoveryUntil || !MeleeRules.canAct(player)) return;
        if (CastManager.state(player).casting() != null) return; // mid charge or channel: hands are busy
        MeleeNumbers numbers = MeleeNumbers.of(player);
        if (!MaliceService.canAfford(player, numbers.guardMinMalice())) {
            player.displayClientMessage(Component.translatable("message.kegare.melee.no_malice", Component.translatable("key.kegare.parry")), true);
            return;
        }
        state.guarding = true;
        state.guardStart = now;
        state.guardStartMs = com.cnethuka.kegare.timing.SubTick.mark(player, com.cnethuka.kegare.timing.TimedInput.PARRY);
        state.parriedThisGuard = false;
        state.guardLoopAt = 0;
        VisualService.startLoop(player, MeleeIds.GUARD_LOOP, Visual.animation(MeleeIds.ANIM_PARRY_GUARD), 0);
        MeleeLayer.syncHud(player);
    }

    static void release(ServerPlayer player) {
        MeleeState state = MeleeLayer.state(player);
        if (state.guarding) end(player, state);
    }

    /** The guard comes down, however that happened, and the recovery starts. */
    static void end(ServerPlayer player, MeleeState state) {
        state.guarding = false;
        state.recoveryUntil = player.level().getGameTime() + MeleeNumbers.of(player).recoveryTicks();
        VisualService.stopLoop(player, MeleeIds.GUARD_LOOP);
        // Caught nothing and exposed: the failed recovery shows. After a clean parry they just lower their hands.
        if (!state.parriedThisGuard) VisualService.playOnce(player, Visual.animation(MeleeIds.ANIM_PARRY_RECOVERY));
        MeleeLayer.syncHud(player);
    }

    static void tick(ServerPlayer player, MeleeState state) {
        if (!state.guarding) return;
        if (!MeleeRules.canAct(player)) {
            end(player, state);
            return;
        }
        MeleeNumbers numbers = MeleeNumbers.of(player);
        long held = player.level().getGameTime() - state.guardStart;
        if (numbers.guardMaxTicks() > 0 && held >= numbers.guardMaxTicks()) {
            end(player, state);
            return;
        }
        if (held >= numbers.perfectTicks() && !MaliceService.trySpend(player, numbers.guardDrainPerTick())) {
            end(player, state);
            return;
        }
        if (state.guardLoopAt > 0 && player.level().getGameTime() >= state.guardLoopAt) {
            state.guardLoopAt = 0;
            VisualService.startLoop(player, MeleeIds.GUARD_LOOP, Visual.animation(MeleeIds.ANIM_PARRY_GUARD), 0);
        }
    }

    /** They're guarding and it still counts: a stun landing this very tick has already taken it. */
    static boolean holding(ServerPlayer player, MeleeState state) {
        return state.guarding && !CombatHelper.isStunned(player);
    }

    static boolean inPerfectWindow(ServerPlayer player, MeleeState state) {
        return holding(player, state) && player.level().getGameTime() - state.guardStart < MeleeNumbers.of(player).perfectTicks();
    }

    /**
     * Is a hit from {@code attacker} landing now in the perfect window? By ticks (how long ago the press reached the server), unless
     * {@code [timing] parrySubTick} is on: then the defender's press against the attacker's swing, both stamped and mapped to server time
     * (DESIGN §3.29), 0 to the window's length apart. Both are worked out every time, for the log.
     */
    static boolean inPerfectWindow(ServerPlayer player, MeleeState state, LivingEntity attacker) {
        if (!holding(player, state)) return false;
        int window = MeleeNumbers.of(player).perfectTicks();
        long held = player.level().getGameTime() - state.guardStart;
        boolean byTicks = held < window;
        double gap = swingMs(attacker) - state.guardStartMs;
        boolean bySubTick = subTickPerfect(gap, window * 50.0);
        if (com.cnethuka.kegare.timing.TimingLog.any()) {
            com.cnethuka.kegare.timing.TimingLog.send(player.server, String.format(java.util.Locale.ROOT,
                    "Parry %s (%s) <- %s (%s): by ticks %s (held %d of %d ticks), sub-tick %s (swing %.1f ms after the press, window %d ms)%s",
                    player.getGameProfile().getName(), com.cnethuka.kegare.timing.SubTick.describe(player), attacker.getName().getString(),
                    attacker instanceof ServerPlayer ap ? com.cnethuka.kegare.timing.SubTick.describe(ap) : "a mob", byTicks ? "PERFECT" : "no", held, window,
                    bySubTick ? "PERFECT" : "no", gap, window * 50, com.cnethuka.kegare.timing.TimingConfig.PARRY_SUB_TICK.get() ? " [sub-tick used]" : " [ticks used]"));
        }
        return com.cnethuka.kegare.timing.TimingConfig.PARRY_SUB_TICK.get() ? bySubTick : byTicks;
    }

    /** The sub-tick rule: the swing came after the press, and less than the window after it. */
    static boolean subTickPerfect(double swingAfterPressMs, double windowMs) {
        return swingAfterPressMs >= 0 && swingAfterPressMs < windowMs;
    }

    /** When the attacker swung: a player's stamped attack if it's this tick's, else now (a mob's hit has no stamp). */
    private static double swingMs(LivingEntity attacker) {
        if (attacker instanceof ServerPlayer p && p.hasData(MeleeLayer.ATTACHMENT) && MeleeLayer.state(p).swingTick == p.level().getGameTime()) {
            var at = com.cnethuka.kegare.timing.SubTick.time(p, com.cnethuka.kegare.timing.TimedInput.ATTACK);
            if (at.isPresent()) return at.getAsDouble();
        }
        return com.cnethuka.kegare.timing.SubTick.nowMs();
    }

    /** A melee hit met in the perfect window. Bosses never get here: their hits are only reduced. */
    static void parried(ServerPlayer player, MeleeState state, LivingEntity attacker) {
        state.parriedThisGuard = true;
        state.guardLoopAt = player.level().getGameTime() + MeleeIds.PARRY_PERFECT_TICKS;
        VisualService.playOnce(player, Visual.animation(MeleeIds.ANIM_PARRY_PERFECT));
        MeleeImpact.PARRY_PERFECT.fire(player.serverLevel(), contact(player, attacker), player, attacker);
        if (attacker instanceof ServerPlayer rusher && ReinforcedStrike.fromRush(rusher)) {
            // A Closing Line's Strike caught (DESIGN §3.27): it's spent, and they're staggered harder for having committed to it.
            ReinforcedStrike.clear(rusher, MeleeLayer.state(rusher));
            stagger(attacker, com.cnethuka.kegare.severed.SeveredConfig.ticks(com.cnethuka.kegare.severed.SeveredConfig.PARRY_STAGGER_SECONDS.get()),
                    com.cnethuka.kegare.severed.SeveredConfig.PARRY_STAGGER_SLOWNESS.get());
        } else {
            stagger(attacker);
        }
        ReinforcedStrike.earn(player, state);
        if (attacker instanceof ServerPlayer opponent && Attribution.isRealTarget(player, opponent) && swungFullyCharged(opponent)) {
            FightTracker.meleeMove(player, opponent, MeleeMoves.PERFECT_PARRY);
        }
    }

    /** A swing has started, and this is how charged it was. */
    static void noteSwing(ServerPlayer player) {
        MeleeState state = MeleeLayer.state(player);
        state.swingCharge = player.getAttackStrengthScale(0.5f);
        state.swingTick = player.level().getGameTime();
    }

    /** Did the hit being handled come from a real, fully charged swing? A tapped one is too easy to feed someone. */
    static boolean swungFullyCharged(ServerPlayer player) {
        MeleeState state = MeleeLayer.state(player);
        return state.swingTick == player.level().getGameTime() && state.swingCharge >= MeleeConfig.PARRY_ASSESSMENT_CHARGE.get();
    }

    /** Slowed, with their attack cooldown started over. Their abilities aren't touched, and bosses can't be staggered (DESIGN §4). */
    static void stagger(LivingEntity attacker) {
        stagger(attacker, MeleeConfig.ticks(MeleeConfig.PARRY_STAGGER_SECONDS.get()), MeleeConfig.PARRY_STAGGER_SLOWNESS.get());
    }

    static void stagger(LivingEntity attacker, int ticks, int slowness) {
        if (CombatHelper.isBoss(attacker)) return;
        if (ticks > 0) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, slowness, false, false));
        if (attacker instanceof ServerPlayer player) {
            player.resetAttackStrengthTicker();
            MeleeLayer.sendAttackReset(player); // the client keeps its own copy, for the swing and the indicator
        }
        VisualService.playOnce(attacker, Visual.animation(MeleeIds.ANIM_STAGGER));
        MeleeImpact.STAGGER.fire((net.minecraft.server.level.ServerLevel) attacker.level(), attacker.position(), attacker);
    }

    /** Where the two met: out from the defender's chest, toward the attacker. */
    static Vec3 contact(LivingEntity defender, LivingEntity attacker) {
        Vec3 chest = defender.position().add(0, defender.getBbHeight() * 0.65, 0);
        Vec3 to = attacker.position().add(0, attacker.getBbHeight() * 0.65, 0).subtract(chest);
        return chest.add(to.lengthSqr() < 1.0e-4 ? defender.getLookAngle() : to.normalize().scale(Math.min(0.8, to.length() / 2)));
    }

    /** A cast starting drops the guard: you can't hold it and cast at once. A press that casts nothing leaves it up. Attacking drops it too. */
    static void onCastStarted(ServerPlayer player) {
        if (player.hasData(MeleeLayer.ATTACHMENT)) release(player);
    }
}
