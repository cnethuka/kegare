package com.cnethuka.kegare.innate_technique.rime;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Sky High bookkeeping (runtime attachment {@code kegare:rime_launch} on the victim): who launched this player with
 * Glacial Spire. Cleared once they have left the ground and landed again, or after {@link #MAX_TICKS}.
 */
public final class RimeLaunch {
    private static final long MAX_TICKS = 200;

    /** The attachment's value. {@code caster == null}: no launch. */
    public static final class Mark {
        @Nullable UUID caster;
        long since;
        boolean leftGround;
    }

    private RimeLaunch() {}

    static void mark(ServerPlayer victim, ServerPlayer caster) {
        Mark mark = victim.getData(RimeRegistry.LAUNCH);
        mark.caster = caster.getUUID();
        mark.since = victim.level().getGameTime();
        mark.leftGround = false;
    }

    /** Every server tick for players: forget the launch once they're back on the ground. */
    static void tick(ServerPlayer player) {
        if (!player.hasData(RimeRegistry.LAUNCH)) return;
        Mark mark = player.getData(RimeRegistry.LAUNCH);
        if (mark.caster == null) return;
        if (!player.onGround()) {
            mark.leftGround = true;
        } else if (mark.leftGround || player.level().getGameTime() - mark.since > MAX_TICKS) {
            mark.caster = null;
        }
    }

    /** A Frost Shard (or Splinter shard) of {@code caster} hit {@code target}: Sky High if they're still in the air. */
    static void onShardHit(ServerPlayer caster, LivingEntity target) {
        if (!(target instanceof ServerPlayer victim) || victim == caster || !victim.hasData(RimeRegistry.LAUNCH)) return;
        Mark mark = victim.getData(RimeRegistry.LAUNCH);
        if (caster.getUUID().equals(mark.caster) && !victim.onGround()) RimeAdvancements.award(caster, RimeAdvancements.SKY_HIGH);
    }
}
