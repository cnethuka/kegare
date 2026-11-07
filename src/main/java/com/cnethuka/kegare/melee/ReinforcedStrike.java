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
    private ReinforcedStrike() {}

    static void earn(ServerPlayer player, MeleeState state) {
        MeleeNumbers numbers = MeleeNumbers.of(player);
        state.strikeUntil = player.level().getGameTime() + numbers.strikeTicks();
        state.strikeTicks = numbers.strikeTicks();
        MeleeTell.sync(player, state, true);
        MeleeLayer.syncHud(player);
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
        MeleeTell.sync(player, state, false);
        MeleeLayer.syncHud(player);
    }

    /**
     * A melee hit is about to land. Returns the damage it should do: more if the Strike goes off. Without the Malice it stays
     * normal and the Strike waits, in case they can pay before it runs out.
     */
    static float onHit(ServerPlayer attacker, LivingEntity target, float damage) {
        MeleeState state = MeleeLayer.state(attacker);
        if (!isReady(attacker, state)) return damage;
        MeleeNumbers numbers = MeleeNumbers.of(attacker);
        if (!MaliceService.trySpend(attacker, numbers.strikeCost())) return damage;
        clear(attacker, state);
        Landings.markStrike(attacker, target);
        if (!CombatHelper.isBoss(target) && numbers.strikeKnockback() > 0) { // bosses: damaged, never moved
            target.knockback(numbers.strikeKnockback(), attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        VisualService.playOnce(attacker, Visual.animation(MeleeIds.ANIM_REINFORCED_STRIKE));
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        MeleeTell.playStrikeImpact(attacker, at);
        MeleeImpact.STRIKE_HIT.fire(attacker.serverLevel(), at, target);
        return damage * (float) (1.0 + numbers.strikeBonus());
    }
}
