package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeStops;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Blur (G for Severed players, DESIGN §3.27): extremely fast, with momentum. The server allows it and tells the owner's client, which
 * drives the movement ({@code client/BlurDrive}) under the game's own collision, the way a dash is driven; the server checks every
 * tick how far they really went (never more than Blur allows, a few ticks at a time, so ping can't trip it), burns hunger for the
 * distance covered at Blur speed, catches a wall crash, and ends it on a stun, a freeze (Rime's or a Standstill), a mount, gliding,
 * or no longer being Severed. At {@code foodFloor} it crashes off: winded, and no Blur again until food is back above it. Swimming
 * and lava get no Blur speed; it stays on.
 */
public final class Blur {
    /** A sprint, in blocks a tick: faster than this is Blur's doing, and what its drain counts. */
    static final double SPRINT = 0.3;
    /** Ticks of movement the server sums to check it (a late packet bunches two ticks into one). */
    static final int CHECK_TICKS = 5;
    /** Room over Blur's speed: the game's own steering adds a little, and a slope or a jump a little more. */
    private static final double SLACK = 1.6, SLACK_FLAT = 0.25;
    private static final int CRASH_COOLDOWN = 10;

    private Blur() {}

    /** G, from a Severed player. */
    static void toggle(ServerPlayer player) {
        SeveredState state = Severed.state(player);
        if (state.blur) {
            stop(player, state);
            return;
        }
        long now = player.level().getGameTime();
        if (!canRun(player)) return;
        if (now < state.windedUntil || player.getFoodData().getFoodLevel() <= SeveredConfig.BLUR_FOOD_FLOOR.get()) {
            player.displayClientMessage(Component.translatable("message.kegare.severed.winded"), true);
            return;
        }
        state.blur = true;
        state.forgetMotion();
        VisualService.startLoop(player, SeveredIds.BLUR_LOOP, Visual.effect(SeveredIds.FX_BLUR, Visual.Anchor.FEET), 0);
        SeveredFx.blurOn(player);
        sync(player, state);
    }

    /** What has to hold for Blur to run at all. */
    static boolean canRun(ServerPlayer player) {
        return Severed.is(player) && player.isAlive() && !player.isSpectator() && !CombatHelper.isStunned(player) && !FreezeService.isFrozen(player)
                && !TimeStops.frozen(player) && !player.isPassenger() && !player.isFallFlying();
    }

    static void stop(ServerPlayer player, SeveredState state) {
        if (!state.blur) return;
        state.blur = false;
        VisualService.stopLoop(player, SeveredIds.BLUR_LOOP);
        sync(player, state);
    }

    /** Out of food: it crashes off, and they're winded. */
    static void windedOff(ServerPlayer player, SeveredState state) {
        int ticks = SeveredConfig.ticks(SeveredConfig.WINDED_SECONDS.get());
        state.windedUntil = player.level().getGameTime() + ticks;
        if (ticks > 0) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, SeveredConfig.WINDED_SLOWNESS.get(), false, false, true));
        stop(player, state);
        SeveredFx.winded(player);
        sync(player, state);
    }

    /** Every server tick, after their movement for the tick has come in. */
    static void tick(ServerPlayer player, SeveredState state) {
        Vec3 at = player.position();
        Vec3 was = state.position(0);
        double speed = was == null ? 0 : Math.sqrt(sq(at.x - was.x) + sq(at.z - was.z));
        if (was != null && at.distanceToSqr(was) > 64) speed = 0; // a teleport isn't speed
        if (!state.blur && !ClosingLine.rushing(state)) {
            state.record(at, speed);
            return;
        }
        if (!canRun(player)) {
            ClosingLine.cancel(player, state);
            stop(player, state);
            state.record(at, speed);
            return;
        }
        long now = player.level().getGameTime();
        // A wall: they were going fast, the last move ran into something, and most of the speed is gone.
        double before = Math.max(state.speed(0), Math.max(state.speed(1), state.speed(2)));
        if (player.horizontalCollision && now - state.crashedAt > CRASH_COOLDOWN && before - speed >= SeveredConfig.CRASH_MIN_LOSS.get()) {
            state.crashedAt = now;
            boolean rushing = ClosingLine.rushing(state);
            if (WallCrash.crash(player, before - speed)) {
                ClosingLine.cancel(player, state);
                stop(player, state);
                state.record(at, speed);
                return;
            }
            if (rushing) ClosingLine.stoppedByWall(player, state);
        }
        // Never further than Blur (or the rush) allows, summed over a few ticks so a late packet can't trip it.
        Vec3 back = state.position(CHECK_TICKS - 1);
        double allowed = CHECK_TICKS * (Math.max(SeveredConfig.BLUR_SPEED.get(), ClosingLine.allowedSpeed(state)) * SLACK + SLACK_FLAT);
        if (back != null && was != null && Math.sqrt(sq(at.x - back.x) + sq(at.z - back.z)) > allowed && at.distanceToSqr(was) <= 64) {
            Vec3 to = state.position(0);
            player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
            at = to;
            speed = 0;
        }
        state.record(at, speed);
        if (!state.blur) return;
        // Hunger, for the distance covered at Blur speed.
        if (speed > SPRINT && !player.isCreative()) {
            player.getFoodData().addExhaustion((float) (speed * SeveredConfig.BLUR_EXHAUSTION.get()));
        }
        if (player.getFoodData().getFoodLevel() <= SeveredConfig.BLUR_FOOD_FLOOR.get()) {
            windedOff(player, state);
            return;
        }
        if (speed > SPRINT && now - state.windTick >= 10) {
            state.windTick = now;
            SeveredFx.wind(player);
        }
    }

    /** Tells the owner's client, if anything it shows or drives by changed. */
    static void sync(ServerPlayer player, SeveredState state) {
        BlurPayload payload = new BlurPayload(state.blur, SeveredConfig.BLUR_SPEED.get().floatValue(), SeveredConfig.BLUR_TURN.get().floatValue(),
                SeveredConfig.BLUR_ACCEL.get().floatValue(), SeveredConfig.BLUR_SKID.get().floatValue(), state.windedUntil,
                SeveredConfig.ticks(SeveredConfig.WINDED_SECONDS.get()));
        if (payload.equals(state.sent)) return;
        state.sent = payload;
        if (player.connection != null) PacketDistributor.sendToPlayer(player, payload);
    }

    private static double sq(double v) {
        return v * v;
    }
}
