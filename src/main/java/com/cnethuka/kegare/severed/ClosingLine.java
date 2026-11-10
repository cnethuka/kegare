package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.impact.Impacts;
import com.cnethuka.kegare.melee.MeleeHits;
import com.cnethuka.kegare.progression.Attribution;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import com.cnethuka.kegare.innatetechnique.CastIntent;
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
 * covering the gap in a few ticks. The line runs from your feet to theirs in three dimensions, so it's a leap up to a target on a
 * pillar or a ledge, or down to one below, when nothing's in the way; gravity takes over once it's done. It looks like a teleport
 * but is real travel, driven on the owner's client like a dash ({@code client/RushDrive}), so the game's own collision stops it: a
 * wall stopping you at that speed is a wall crash ({@link Blur}).
 * It doesn't home: a target that moves away makes you miss. Anything living in the path stops you there; projectiles and techniques
 * hit you on the way, with no invulnerability. Arriving (in reach of the target, or of whatever stopped you) makes the first melee hit
 * within {@code strikeWindow} a momentum hit: the rush's speed carried into it as extra damage and knockback, and as the rush's charge for
 * the Impact System. No Reinforced Strike: that's Malice, and a Severed player has none. After every one, {@code recoverySeconds} with no
 * Blur speed, and a cooldown. A boss can be rushed at and struck, never moved.
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

    /** R from a Severed player who's Blurring casts Closing Line and does nothing else. */
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
        Vec3 line = target.position().subtract(from); // feet to feet: up, down or level
        double distance = line.length() - reachGap(player, target);
        if (distance < 0.5) {
            player.displayClientMessage(Component.translatable("message.kegare.severed.in_reach"), true);
            return;
        }
        Vec3 direction = line.normalize();
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
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, new RushPayload((float) direction.x, (float) direction.y, (float) direction.z, (float) state.rushSpeed,
                    (float) distance));
        }
        Blur.sync(player, state);
    }

    /** Where to stop short of something: its edge and yours apart by the stop gap (melee reach, in front of it). */
    static double reachGap(LivingEntity mover, LivingEntity other) {
        return mover.getBbWidth() / 2 + other.getBbWidth() / 2 + SeveredConfig.CLOSING_GAP.get();
    }

    /**
     * How far along the line before they'd be in reach of something living in the way (not the target), or {@code distance}: where
     * their box would first touch its box, less the stop gap.
     */
    static double firstInTheWay(ServerPlayer player, LivingEntity target, Vec3 direction, double distance) {
        AABB swept = player.getBoundingBox().expandTowards(direction.scale(distance + 1)).inflate(0.2);
        Vec3 start = player.position(), end = start.add(direction.scale(distance + 1));
        double w = player.getBbWidth() / 2, h = player.getBbHeight();
        double best = distance;
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, swept,
                e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            if (e.position().subtract(start).dot(direction) <= 0) continue; // behind them
            // Their feet anywhere in its box grown by theirs puts the two boxes together.
            AABB b = e.getBoundingBox();
            AABB grown = new AABB(b.minX - w, b.minY - h, b.minZ - w, b.maxX + w, b.maxY, b.maxZ + w);
            Optional<Vec3> touch = grown.contains(start) ? Optional.of(start) : grown.clip(start, end);
            if (touch.isEmpty()) continue;
            best = Math.min(best, touch.get().subtract(start).dot(direction) - SeveredConfig.CLOSING_GAP.get());
        }
        return Math.max(0, best);
    }

    /** Every tick while rushing, after their movement has come in (from {@link Blur#tick}). */
    static void tick(ServerPlayer player, SeveredState state) {
        if (!rushing(state)) return;
        Vec3 rel = player.position().subtract(state.rushFrom);
        double along = rel.dot(state.rushDirection);
        double off = Math.sqrt(Math.max(0, rel.lengthSqr() - along * along));
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

    /** They got there: the next melee hit carries the rush's speed, a skid, and the recovery. */
    static void arrive(ServerPlayer player, SeveredState state, double speed) {
        end(player, state, speed);
        state.momentumUntil = player.level().getGameTime() + SeveredConfig.ticks(SeveredConfig.CLOSING_STRIKE_WINDOW.get());
        state.momentumSpeed = speed;
        SeveredFx.arrive(player, state.rushDirection != null ? state.rushDirection : player.getLookAngle());
        Blur.sync(player, state);
    }

    /** Is a momentum hit waiting on them (a Closing Line arrived within {@code strikeWindow})? */
    public static boolean momentumReady(ServerPlayer player) {
        if (!player.hasData(Severed.STATE)) return false;
        return player.level().getGameTime() < Severed.state(player).momentumUntil;
    }

    /** A perfect parry caught it: spent, with nothing landed. Returns whether there was one. */
    public static boolean spendMomentum(ServerPlayer player) {
        if (!momentumReady(player)) return false;
        Severed.state(player).momentumUntil = 0;
        return true;
    }

    /**
     * The first melee hit after arriving, on something living that's a real target: the rush's speed carried into it, as
     * {@code momentumDamage} per block/tick of extra damage (added to the hit, before the target's defences), as {@code carriedKnockback}
     * per block/tick of knockback (applied with the hit; never to a boss), and as the rush's charge for the Impact System
     * (its speed against Closing Line's full speed). After the Strength bonus, so the momentum isn't scaled by it.
     */
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!momentumReady(attacker) || MeleeHits.of(event.getSource(), level) != MeleeHits.Kind.MELEE) return;
        if (!Attribution.isRealTarget(attacker, target) || !target.isAlive()) return;
        SeveredState state = Severed.state(attacker);
        double speed = state.momentumSpeed;
        state.momentumUntil = 0;
        event.setAmount(event.getAmount() + (float) (SeveredConfig.MOMENTUM_DAMAGE.get() * speed));
        double knockback = SeveredConfig.CLOSING_CARRY.get() * speed;
        if (!CombatHelper.isBoss(target) && knockback > 0) {
            target.knockback(knockback, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
            target.hurtMarked = true;
        }
        Impacts.charge(target, Math.min(1.0, speed / SeveredConfig.CLOSING_SPEED.get()));
    }

    /** Stopped short of arriving (a wall, a swap, a stun): no momentum hit, the recovery all the same. */
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
