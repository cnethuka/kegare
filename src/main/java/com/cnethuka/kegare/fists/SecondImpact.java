package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.melee.MeleeTell;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The second impact (DESIGN §3.28): a Reinforced Strike landed with Fists on hits again {@code secondImpactDelay} later, at the same spot
 * on the body, for {@code secondImpactDamage} of the Strike's damage before the target's defences, which meet it again as a hit of its own.
 * Its own damage type ({@code kegare:second_impact}): no knockback, so a boss is never moved, and it doesn't wait out the hurt cooldown
 * the Strike started, nor add to it. It goes through the Impact System like any hit. Plain Fists hits don't get one.
 */
public final class SecondImpact {
    private record Due(ServerPlayer attacker, LivingEntity target, float damage, long at) {}

    private static final List<Due> DUE = new ArrayList<>();

    private SecondImpact() {}

    /** A Strike landed: {@code damage} is what it dealt before the target's defences. */
    static void onStrike(ServerPlayer attacker, LivingEntity target, float damage) {
        if (!ReinforcedFists.isOn(attacker) || damage <= 0) return;
        float second = (float) (damage * FistsConfig.SECOND_IMPACT_DAMAGE.get());
        if (second <= 0) return;
        DUE.add(new Due(attacker, target, second, attacker.level().getGameTime() + Math.max(1, FistsConfig.ticks(FistsConfig.SECOND_IMPACT_DELAY))));
    }

    /** Every server tick (the overworld's clock). */
    static void tick(ServerLevel level) {
        if (DUE.isEmpty()) return;
        long now = level.getGameTime();
        List<Due> ready = new ArrayList<>();
        for (Iterator<Due> it = DUE.iterator(); it.hasNext(); ) {
            Due due = it.next();
            if (now >= due.at()) {
                it.remove();
                ready.add(due);
            }
        }
        ready.forEach(SecondImpact::land);
    }

    private static void land(Due due) {
        LivingEntity target = due.target();
        ServerPlayer attacker = due.attacker();
        if (!target.isAlive() || attacker.isRemoved() || target.level() != attacker.level()) return;
        ServerLevel level = attacker.serverLevel();
        target.hurt(source(level, attacker), due.damage());
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        MeleeTell.playStrikeImpact(attacker, at);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    public static DamageSource source(ServerLevel level, ServerPlayer attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FistsIds.SECOND_IMPACT), attacker, attacker);
    }

    static boolean isSecondImpact(DamageSource source) {
        return source.is(FistsIds.SECOND_IMPACT);
    }

    /** How many are waiting (tests). */
    static int pending() {
        return DUE.size();
    }

    public static void clear() {
        DUE.clear();
    }
}
