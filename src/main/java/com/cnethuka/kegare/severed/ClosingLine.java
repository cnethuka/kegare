package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.innatetechnique.CastIntent;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Closing Line (R while Blurring, DESIGN §3.27): a straight rush at where the player or mob you're looking at stood when you cast it,
 * covering the gap in a few ticks. It looks like a teleport but is real travel, driven on the owner's client like a dash
 * ({@code client/RushDrive}), so the game's own collision stops it: a wall stopping you at that speed is a wall crash ({@link Blur}).
 * It doesn't home: a target that moves away makes you miss. Anything living in the path stops you there; projectiles and techniques
 * hit you on the way, with no invulnerability. Arriving (in reach of the target, or of whatever stopped you) earns a Reinforced Strike
 * for {@code strikeWindow} that carries the rush's speed as knockback. After every one, {@code recoverySeconds} with no Blur speed,
 * and a cooldown. A boss can be rushed at and struck, never moved (the Strike's own rule).
 */
public final class ClosingLine {
    /** Off the line by more than this, they were moved by something else (a swap): it's over. */
    private static final double OFF_LINE = 2.5;
    /** Ticks past what the rush should take, in case something holds it up without stopping it. */
    private static final int SPARE_TICKS = 4;

    private ClosingLine() {}

    static boolean rushing(SeveredState state) {
        return state.rushDirection != null;
    }

    /** How fast a rush may have moved them over the last few ticks (it ran on into the server's check), blocks a tick. */
    static double allowedSpeed(SeveredState state, long now) {
        return rushing(state) || now < state.rushAllowedUntil ? state.rushSpeed : 0;
    }

    /** R from a Severed player who's Blurring: everything else about R is theirs no more. */
    public static boolean intercept(ServerPlayer player, CastIntent intent) {
        if (!Severed.is(player) || !Severed.blurring(player)) return false;
        if (intent == CastIntent.SPECIAL_PRESS) request(player);
        return intent == CastIntent.SPECIAL_PRESS || intent == CastIntent.SPECIAL_RELEASE;
    }

    static void request(ServerPlayer player) {
        SeveredState state = Severed.state(player);
        long now = player.level().getGameTime();
        if (!state.blur || rushing(state) || !Blur.canRun(player) || now < state.recoveryUntil) return;
        if (now < state.closingReadyAt) {
            String seconds = String.format(java.util.Locale.ROOT, "%.1f", (state.closingReadyAt - now) / 20.0);
            player.displayClientMessage(Component.translatable("message.kegare.severed.closing_line_cooldown", seconds), true);
            return;
        }
        Optional<LivingEntity> seen = SeveredTargeting.lookedAt(player);
        if (seen.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.kegare.severed.no_target"), true);
            return;
        }
        LivingEntity target = seen.get();
        Vec3 from = player.position();
        Vec3 flat = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
        double distance = flat.horizontalDistance() - reachGap(player, target);
        if (distance < 0.5) {
            player.displayClientMessage(Component.translatable("message.kegare.severed.in_reach"), true);
            return;
        }
        Vec3 direction = flat.normalize();
        distance = Math.min(distance, firstInTheWay(player, target, direction, distance));
        state.closingReadyAt = now + SeveredConfig.ticks(SeveredConfig.CLOSING_COOLDOWN.get());
        SeveredFx.launch(player, direction);
        if (distance < 0.5) { // something's already in the way, right there
            arrive(player, state, SeveredConfig.CLOSING_SPEED.get());
            return;
        }
        state.rushFrom = from;
        state.rushDirection = direction;
        state.rushDistance = distance;
        state.rushSpeed = SeveredConfig.CLOSING_SPEED.get();
        state.rushTicksLeft = (int) Math.ceil(distance / state.rushSpeed) + SPARE_TICKS;
        state.rushTarget = target.getId();
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new RushPayload((float) direction.x, (float) direction.z, (float) state.rushSpeed, (float) distance));
        Blur.sync(player, state);
    }

    /** Where to stop short of something: its edge and yours apart by the stop gap (melee reach, in front of it). */
    static double reachGap(LivingEntity mover, LivingEntity other) {
        return mover.getBbWidth() / 2 + other.getBbWidth() / 2 + SeveredConfig.CLOSING_GAP.get();
    }

    /** How far along the line before they'd be in reach of something living in the way (not the target), or {@code distance}. */
    static double firstInTheWay(ServerPlayer player, LivingEntity target, Vec3 direction, double distance) {
        AABB swept = player.getBoundingBox().expandTowards(direction.scale(distance + 1)).inflate(0.2);
        Vec3 start = player.position();
        double best = distance;
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, swept,
                e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 rel = e.position().subtract(start);
            double along = rel.x * direction.x + rel.z * direction.z;
            double off = Math.sqrt(Math.max(0, rel.x * rel.x + rel.z * rel.z - along * along));
            if (along <= 0 || off > (player.getBbWidth() + e.getBbWidth()) / 2) continue;
            best = Math.min(best, along - reachGap(player, e));
        }
        return Math.max(0, best);
    }

    /** Every tick while rushing, after their movement has come in (from {@link Blur#tick}). */
    static void tick(ServerPlayer player, SeveredState state) {
        if (!rushing(state)) return;
        Vec3 rel = player.position().subtract(state.rushFrom);
        Vec3 dir = state.rushDirection;
        double along = rel.x * dir.x + rel.z * dir.z;
        double off = Math.sqrt(Math.max(0, rel.x * rel.x + rel.z * rel.z - along * along));
        if (off > OFF_LINE || along < -1.0) { // moved off it by something else
            cancel(player, state);
            return;
        }
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.35),
                e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            arrive(player, state, state.rushSpeed); // something living in the way: stopped there
            return;
        }
        if (along >= state.rushDistance - 0.35) {
            arrive(player, state, state.rushSpeed);
            return;
        }
        if (--state.rushTicksLeft <= 0) {
            if (along >= state.rushDistance * 0.75) arrive(player, state, state.rushSpeed);
            else cancel(player, state);
        }
    }

    /** They got there: a Strike carrying the rush's speed, a skid, and the recovery. */
    static void arrive(ServerPlayer player, SeveredState state, double speed) {
        end(player, state, speed);
        ReinforcedStrike.earnFromRush(player, SeveredConfig.ticks(SeveredConfig.CLOSING_STRIKE_WINDOW.get()), speed * SeveredConfig.CLOSING_CARRY.get());
        SeveredFx.arrive(player, state.rushDirection != null ? state.rushDirection : player.getLookAngle());
        Blur.sync(player, state);
    }

    /** Stopped short of arriving (a wall, a swap, a stun): no Strike, the recovery all the same. */
    static void cancel(ServerPlayer player, SeveredState state) {
        if (!rushing(state)) return;
        end(player, state, state.rushSpeed);
        Blur.sync(player, state);
    }

    private static void end(ServerPlayer player, SeveredState state, double speed) {
        long now = player.level().getGameTime();
        boolean was = rushing(state);
        state.rushDirection = null;
        state.rushTarget = -1;
        state.rushSpeed = speed;
        state.rushAllowedUntil = now + Blur.CHECK_TICKS + 2;
        state.recoveryUntil = now + SeveredConfig.ticks(SeveredConfig.CLOSING_RECOVERY.get());
        if (was && player.connection != null) PacketDistributor.sendToPlayer(player, RushPayload.STOP);
    }
}
