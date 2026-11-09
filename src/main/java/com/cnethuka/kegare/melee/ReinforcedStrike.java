package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Landings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Reinforcement, spent: no key and no slot. A perfect parry or a clean dodge readies it, and the next melee hit in time lands
 * harder and pays by itself. It doesn't stack: earning it again only restarts the clock.
 */
public final class ReinforcedStrike {
    /** Told when a Strike goes off. */
    @FunctionalInterface
    public interface LandedListener {
        /** @param damage what the Strike deals, before the target's defences */
        void landed(ServerPlayer attacker, LivingEntity target, float damage);
    }

    private static final java.util.List<LandedListener> LANDED = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ReinforcedStrike() {}

    public static void addLandedListener(LandedListener listener) {
        LANDED.add(listener);
    }

    /** Readies a Strike on them, as a perfect parry or a clean dodge does (debug commands and tests). */
    public static void earn(ServerPlayer player) {
        earn(player, MeleeLayer.state(player));
    }

    static void earn(ServerPlayer player, MeleeState state) {
        MeleeNumbers numbers = MeleeNumbers.of(player);
        state.strikeUntil = player.level().getGameTime() + numbers.strikeTicks();
        state.strikeTicks = numbers.strikeTicks();
        state.strikeExtraKnockback = 0;
        state.strikeFromRush = false;
        MeleeTell.sync(player, state, true);
        MeleeLayer.syncHud(player);
    }

    /**
     * A Closing Line's arrival (DESIGN §3.27): a Strike for {@code ticks}, carrying {@code extraKnockback} of the rush's speed on top
     * of its own. The ready fist shows as for any Strike.
     */
    public static void earnFromRush(ServerPlayer player, int ticks, double extraKnockback) {
        MeleeState state = MeleeLayer.state(player);
        state.strikeUntil = player.level().getGameTime() + ticks;
        state.strikeTicks = ticks;
        state.strikeExtraKnockback = Math.max(0, extraKnockback);
        state.strikeFromRush = true;
        MeleeTell.sync(player, state, true);
        MeleeLayer.syncHud(player);
    }

    /** Ticks left on a ready Strike, or 0. */
    public static int ticksLeft(ServerPlayer player) {
        if (!player.hasData(MeleeLayer.ATTACHMENT)) return 0;
        return (int) Math.max(0, MeleeLayer.state(player).strikeUntil - player.level().getGameTime());
    }

    /** A Closing Line's Strike is ready on them. */
    public static boolean fromRush(ServerPlayer player) {
        return isReady(player) && MeleeLayer.state(player).strikeFromRush;
    }

    public static boolean isReady(ServerPlayer player) {
        return player.hasData(MeleeLayer.ATTACHMENT) && isReady(player, MeleeLayer.state(player));
    }

    private static boolean isReady(ServerPlayer player, MeleeState state) {
        return player.level().getGameTime() < state.strikeUntil;
    }

    static void tick(ServerPlayer player, MeleeState state) {
        if (state.strikeUntil == 0) return;
        if (isReady(player, state)) MeleeTell.sync(player, state, true); // a disguise can begin or end while it's ready
        else clear(player, state);
    }

    static void clear(ServerPlayer player, MeleeState state) {
        if (state.strikeUntil == 0) return;
        state.strikeUntil = 0;
        state.strikeExtraKnockback = 0;
        state.strikeFromRush = false;
        MeleeTell.sync(player, state, false);
        MeleeLayer.syncHud(player);
    }

    /**
     * A melee hit is about to land. Returns the damage it should do: more if the Strike goes off. Without the Malice it stays
     * normal and the Strike waits, in case they can pay before it runs out. {@code beforeDefences}: the hit's damage before the
     * target's armour, enchantments and effects took their share.
     */
    static float onHit(ServerPlayer attacker, LivingEntity target, float damage, float beforeDefences) {
        MeleeState state = MeleeLayer.state(attacker);
        if (!isReady(attacker, state)) return damage;
        MeleeNumbers numbers = MeleeNumbers.of(attacker);
        if (!MaliceService.trySpend(attacker, numbers.strikeCost())) return damage;
        double extraKnockback = state.strikeExtraKnockback;
        clear(attacker, state);
        Landings.markStrike(attacker, target);
        double knockback = numbers.strikeKnockback() + extraKnockback; // a Closing Line's carries its rush (§3.27)
        if (!CombatHelper.isBoss(target) && knockback > 0) { // bosses: damaged, never moved
            target.knockback(knockback, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        VisualService.playOnce(attacker, Visual.animation(MeleeIds.ANIM_REINFORCED_STRIKE));
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        MeleeTell.playStrikeImpact(attacker, at);
        MeleeImpact.STRIKE_HIT.fire(attacker.serverLevel(), at, target, attacker);
        float strike = beforeDefences * (float) (1.0 + numbers.strikeBonus());
        LANDED.forEach(listener -> listener.landed(attacker, target, strike));
        return damage * (float) (1.0 + numbers.strikeBonus());
    }
}
