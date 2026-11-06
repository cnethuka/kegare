package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.Ability;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Shared targeting: the player (or, for the abilities that take them, the mob) under the crosshair within
 * {@code [swap.targeting] range}, with line of sight (blocks in the way stop it). Stunned or frozen targets are valid.
 * And the {@link SwapTargetEvent} gate every swap goes through.
 */
public final class SwapTargeting {
    private SwapTargeting() {}

    /** The nearest living, non-spectator player the caster is looking at, or empty (mobs don't count or block). */
    public static Optional<ServerPlayer> lookedAt(ServerPlayer caster) {
        return look(caster, ServerPlayer.class, p -> p != caster && p.isAlive() && !p.isSpectator());
    }

    /**
     * The nearest player OR swappable mob the caster is looking at (Positions, Switchback, Send Off, Exchange and
     * Momentum Exchange take mobs too; see {@link #swappableMob}).
     */
    public static Optional<LivingEntity> lookedAtLiving(ServerPlayer caster) {
        return look(caster, LivingEntity.class, e -> e != caster && e.isAlive() && !e.isSpectator()
                && (e instanceof ServerPlayer || swappableMob(e)));
    }

    /** Any living non-player except armor stands and bosses (the wither, the ender dragon, anything in {@code c:bosses}). */
    public static boolean swappableMob(LivingEntity entity) {
        return !(entity instanceof Player) && !(entity instanceof ArmorStand) && !CombatHelper.isBoss(entity);
    }

    private static <T extends LivingEntity> Optional<T> look(ServerPlayer caster, Class<T> type, Predicate<? super T> valid) {
        double range = SwapConfig.RANGE.get();
        double tolerance = SwapConfig.AIM_TOLERANCE.get();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1f);
        BlockHitResult block = caster.level().clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));

        T best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB search = caster.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0 + tolerance);
        for (T other : caster.serverLevel().getEntitiesOfClass(type, search, valid::test)) {
            AABB box = other.getBoundingBox().inflate(tolerance);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Fires {@link SwapTargetEvent}. Cancelled: tells the caster, awards Not Allowed and returns false (the caller
     * fizzles, keeping the Malice spent).
     *
     * @param message the action-bar line for a refusal, or null for the usual one
     */
    public static boolean allowed(ServerPlayer caster, Entity target, Ability ability, @Nullable Component message) {
        if (!NeoForge.EVENT_BUS.post(new SwapTargetEvent(caster, target, ability)).isCanceled()) {
            // Swap deals no damage, so this is where its abilities land.
            if (target instanceof LivingEntity living) com.cnethuka.kegare.progression.Landings.land(caster, living, ability);
            return true;
        }
        caster.displayClientMessage(message != null ? message : Component.translatable("message.kegare.swap.refused"), true);
        SwapAdvancements.award(caster, SwapAdvancements.NOT_ALLOWED);
        return false;
    }

    public static boolean allowed(ServerPlayer caster, Entity target, Ability ability) {
        return allowed(caster, target, ability, null);
    }
}
