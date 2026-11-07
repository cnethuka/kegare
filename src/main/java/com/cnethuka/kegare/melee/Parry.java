package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastIntent;
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

    /** A melee hit met in the perfect window. Bosses never get here: their hits are only reduced. */
    static void parried(ServerPlayer player, MeleeState state, LivingEntity attacker) {
        state.parriedThisGuard = true;
        state.guardLoopAt = player.level().getGameTime() + MeleeIds.PARRY_PERFECT_TICKS;
        VisualService.playOnce(player, Visual.animation(MeleeIds.ANIM_PARRY_PERFECT));
        MeleeImpact.PARRY_PERFECT.fire(player.serverLevel(), contact(player, attacker), player);
        stagger(attacker);
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
        if (CombatHelper.isBoss(attacker)) return;
        int ticks = MeleeConfig.ticks(MeleeConfig.PARRY_STAGGER_SECONDS.get());
        if (ticks > 0) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, MeleeConfig.PARRY_STAGGER_SLOWNESS.get(), false, false));
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

    /** Casting or attacking drops the guard: you can't hold it and fight at once. */
    static boolean onCastIntent(ServerPlayer player, CastIntent intent) {
        boolean press = intent == CastIntent.CAST_PRESS || intent == CastIntent.SPECIAL_PRESS || intent == CastIntent.ULTIMATE_PRESS;
        if (press && player.hasData(MeleeLayer.ATTACHMENT)) release(player);
        return false; // it never swallows the cast
    }
}
