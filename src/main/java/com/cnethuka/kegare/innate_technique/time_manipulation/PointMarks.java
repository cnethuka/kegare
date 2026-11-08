package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Point Maker's marks (DESIGN §3.26): one per player, runtime only (gone on death, logout or a technique change, with no cooldown). A
 * mark holds where to come back to (the top of the marked block), which way they faced, the effects they had and their health when
 * they marked it, the hands of the clock at that moment, and where they've been since (every few ticks, for the afterimages of the
 * return). When it runs out, Point Maker's cooldown starts.
 */
final class PointMarks {
    /** How often the path back is sampled, and how many samples are kept at most. */
    static final int PATH_EVERY = 4, PATH_MAX = 64;

    static final class Mark {
        final ResourceKey<Level> level;
        final BlockPos block;
        final Vec3 standAt;
        final float yaw, pitch;
        final Set<Holder<MobEffect>> effects;
        final float health;
        final long until;
        final float hourHand, minuteHand;
        final List<Vec3> path = new ArrayList<>();

        Mark(ResourceKey<Level> level, BlockPos block, Vec3 standAt, float yaw, float pitch, Set<Holder<MobEffect>> effects, float health,
             long until, float hourHand, float minuteHand) {
            this.level = level;
            this.block = block;
            this.standAt = standAt;
            this.yaw = yaw;
            this.pitch = pitch;
            this.effects = effects;
            this.health = health;
            this.until = until;
            this.hourHand = hourHand;
            this.minuteHand = minuteHand;
        }
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    private PointMarks() {}

    static Optional<Mark> get(ServerPlayer player) {
        return Optional.ofNullable(MARKS.get(player.getUUID()));
    }

    static boolean has(ServerPlayer player) {
        return MARKS.containsKey(player.getUUID());
    }

    /** Marks the top of {@code block} for the player, as they are right now. */
    static Mark mark(ServerPlayer player, BlockPos block, Vec3 standAt) {
        Set<Holder<MobEffect>> effects = new HashSet<>();
        for (MobEffectInstance e : player.getActiveEffects()) effects.add(e.getEffect());
        long dayTime = player.level().getDayTime() % 24000L;
        // The world's clock: dayTime 0 is six in the morning. Hands in radians from twelve, clockwise.
        double hours = (dayTime / 1000.0 + 6.0) % 12.0;
        float hour = (float) (hours / 12.0 * Mth.TWO_PI), minute = (float) ((dayTime % 1000L) / 1000.0 * Mth.TWO_PI);
        Mark mark = new Mark(player.level().dimension(), block.immutable(), standAt, player.getYRot(), player.getXRot(), effects,
                player.getHealth(), player.level().getGameTime() + TimeConfig.ticks(TimeConfig.MARK_SECONDS), hour, minute);
        mark.path.add(player.position());
        MARKS.put(player.getUUID(), mark);
        send(player, mark);
        return mark;
    }

    /** The mark is used (the return) or gone: forgotten, and the caster told. */
    static void clear(ServerPlayer player) {
        if (MARKS.remove(player.getUUID()) != null) send(player, null);
    }

    /** Forgotten without a word (logout). */
    static void forget(UUID player) {
        MARKS.remove(player);
    }

    /** Every tick for every player with a mark: sample their path; when it runs out, it's gone and the cooldown starts. */
    static void tick(ServerPlayer player) {
        Mark mark = MARKS.get(player.getUUID());
        if (mark == null) return;
        long now = player.level().getGameTime();
        if (now >= mark.until) {
            clear(player);
            InnateTechniqueService.startCooldown(player, TimeManipulation.POINT_MAKER);
            return;
        }
        if (now % PATH_EVERY == 0 && player.level().dimension().equals(mark.level)) {
            mark.path.add(player.position());
            if (mark.path.size() > PATH_MAX) mark.path.remove(1); // the first (where they marked it from) stays
        }
    }

    /**
     * How much the return heals: {@code healFraction} of max health, but never more than was lost since marking, so it never goes above
     * the health they had then. 0 if they've lost nothing.
     */
    static float heal(float marked, float now, float maxHealth, double fraction) {
        float lost = marked - now;
        if (lost <= 0f) return 0f;
        return Math.min((float) (fraction * maxHealth), lost);
    }

    private static void send(ServerPlayer player, @Nullable Mark mark) {
        if (player instanceof FakePlayer || player.connection == null) return;
        PacketDistributor.sendToPlayer(player, mark == null ? TimeNet.Mark.none()
                : new TimeNet.Mark(true, mark.block, mark.hourHand, mark.minuteHand, mark.until));
    }

    static void clearAll() {
        MARKS.clear();
    }
}
