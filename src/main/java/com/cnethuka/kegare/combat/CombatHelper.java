package com.cnethuka.kegare.combat;

import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.innatetechnique.CastManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.Tags;

/**
 * Shared combat state for abilities, dominions and anything else that needs it.
 * Works on any {@link LivingEntity}; damage-based tagging is wired for players in {@code PlayerEvents}.
 * Reads never create the attachment, so querying mobs is free.
 */
public final class CombatHelper {
    private CombatHelper() {}

    private static long now(LivingEntity entity) {
        return entity.level().getGameTime();
    }

    /**
     * A boss ({@code c:bosses}: the wither, the ender dragon, modded bosses). Per DESIGN §4, bosses take damage from
     * innate techniques but are never moved, swapped or disabled (no freeze, launch, knockback or swap).
     */
    public static boolean isBoss(Entity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Marks the entity as in combat right now (also pauses Malice regen). */
    public static void markCombat(LivingEntity entity) {
        entity.getData(KegareAttachments.COMBAT).lastCombatTick = now(entity);
    }

    public static int ticksSinceCombat(LivingEntity entity) {
        if (!entity.hasData(KegareAttachments.COMBAT)) return Integer.MAX_VALUE;
        long last = entity.getData(KegareAttachments.COMBAT).lastCombatTick;
        if (last < 0) return Integer.MAX_VALUE;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, now(entity) - last));
    }

    public static boolean isInCombat(LivingEntity entity) {
        return ticksSinceCombat(entity) < KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS);
    }

    public static int combatTicksRemaining(LivingEntity entity) {
        return Math.max(0, KegareServerConfig.ticks(KegareServerConfig.COMBAT_TAG_SECONDS) - ticksSinceCombat(entity));
    }

    public static boolean isRegenPaused(LivingEntity entity) {
        return ticksSinceCombat(entity) < KegareServerConfig.ticks(KegareServerConfig.REGEN_PAUSE_SECONDS);
    }

    /**
     * Stuns for {@code ticks}. Never shortens an existing stun. Interrupts any charge or channel in progress.
     * While stunned, {@link CastManager} rejects every cast.
     */
    public static void stun(LivingEntity entity, int ticks) {
        if (ticks <= 0 || isBoss(entity)) return;
        CombatData data = entity.getData(KegareAttachments.COMBAT);
        data.stunnedUntil = Math.max(data.stunnedUntil, now(entity) + ticks);
        if (entity instanceof ServerPlayer player) CastManager.interrupt(player);
    }

    public static boolean isStunned(LivingEntity entity) {
        return stunTicksRemaining(entity) > 0;
    }

    public static int stunTicksRemaining(LivingEntity entity) {
        if (!entity.hasData(KegareAttachments.COMBAT)) return 0;
        long remaining = entity.getData(KegareAttachments.COMBAT).stunnedUntil - now(entity);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, remaining));
    }

    public static void clearStun(LivingEntity entity) {
        if (entity.hasData(KegareAttachments.COMBAT)) entity.getData(KegareAttachments.COMBAT).stunnedUntil = 0L;
    }
}
