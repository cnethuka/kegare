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
    /** A vanilla sprint as measured (5.5 blocks a second), in blocks a tick. */
    private static final double VANILLA_SPRINT = 0.28;
    /** How much faster than a sprint under Accelerate Blur always is: clearly faster, never the same. */
    static final double ABOVE_ACCELERATE = 1.25;
    /** Ticks of movement the server sums to check it (a late packet bunches two ticks into one). */
    static final int CHECK_TICKS = 5;
    /** Room over Blur's speed: the game's own steering adds a little, and a slope or a jump a little more. */
    private static final double SLACK = 1.6, SLACK_FLAT = 0.25;
    private static final int CRASH_COOLDOWN = 10;
    /** Blur's own check is putting someone back: that teleport is the check, not something to forget. */
    private static boolean puttingBack;

    private Blur() {}

    /**
     * Blur's top speed, blocks a tick: {@code [blur] speed}, but never below {@link #ABOVE_ACCELERATE} times a sprint under Accelerate's
     * rate, so Blur stays clearly the faster of the two whatever that rate is set to (at the defaults the two agree: 0.7).
     */
    public static double topSpeed() {
        return Math.max(SeveredConfig.BLUR_SPEED.get(),
                VANILLA_SPRINT * com.cnethuka.kegare.innate_technique.time_manipulation.TimeConfig.ACCELERATE_RATE.get() * ABOVE_ACCELERATE);
    }

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
        // A wall: they were going fast, most of the speed is gone, and there's something solid right ahead the way they were going.
        // (The client stops itself at the wall and only reports the shorter move, so the server can't wait for a collision of its own.)
        // Not in a Closing Line's recovery: the rush's own end takes its speed off, often right at the side of the ledge they leapt to.
        double before = Math.max(state.speed(0), Math.max(state.speed(1), state.speed(2)));
        if (now >= state.recoveryUntil && now - state.crashedAt > CRASH_COOLDOWN && before - speed >= SeveredConfig.CRASH_MIN_LOSS.get()
                && wallAhead(player, state)) {
            state.crashedAt = now;
            if (WallCrash.crash(player, before - speed)) {
                ClosingLine.cancel(player, state);
                stop(player, state);
                state.record(at, speed);
                return;
            }
        }
        ClosingLine.tick(player, state);
        // Never further than Blur (or the rush) allows, summed over a few ticks so a late packet can't trip it.
        Vec3 back = state.position(CHECK_TICKS - 1);
        double allowed = CHECK_TICKS * (Math.max(topSpeed(), ClosingLine.allowedSpeed(state, now)) * SLACK + SLACK_FLAT);
        if (back != null && was != null && Math.sqrt(sq(at.x - back.x) + sq(at.z - back.z)) > allowed && at.distanceToSqr(was) <= 64) {
            Vec3 to = state.position(0);
            puttingBack = true;
            try {
                player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
            } finally {
                puttingBack = false;
            }
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

    /**
     * Something solid just ahead of them the way they were last going, too high to step up onto. In a Closing Line, the way its line
     * heads, and anything solid there is a wall: the rush steps over a lip a step high itself ({@code client/RushDrive}), and a rising
     * one held at a wall slides on up its face (the game moves them up before across), so how near its top they got says nothing.
     * The ground under a line that dips, or a ceiling over one that rises, only holds them; only horizontal speed lost counts.
     */
    static boolean wallAhead(ServerPlayer player, SeveredState state) {
        boolean rush = state.rushDirection != null;
        Vec3 step;
        if (rush) {
            step = new Vec3(state.rushDirection.x, 0, state.rushDirection.z);
        } else {
            Vec3 a = state.position(0), b = state.position(1);
            if (a == null || b == null) return false;
            step = new Vec3(a.x - b.x, 0, a.z - b.z);
        }
        if (step.lengthSqr() < 1.0e-6) return false;
        net.minecraft.world.phys.AABB box = player.getBoundingBox().move(0, 0.05, 0).move(step.normalize().scale(0.25));
        return !player.level().noCollision(player, box) && (rush || !player.level().noCollision(player, box.move(0, 0.55, 0)));
    }

    /**
     * The server put them somewhere (Rewind, a swap, a command, an ender pearl): where they were before isn't where they ran from, so
     * the speed check starts over from here instead of putting them back. A Closing Line moved off its line still ends ({@link
     * ClosingLine#tick}).
     */
    static void movedByServer(SeveredState state) {
        if (!puttingBack) state.forgetMotion();
    }

    /** Tells the owner's client, if anything it shows or drives by changed. */
    static void sync(ServerPlayer player, SeveredState state) {
        BlurPayload payload = new BlurPayload(state.blur, (float) topSpeed(), SeveredConfig.BLUR_TURN.get().floatValue(),
                SeveredConfig.BLUR_ACCEL.get().floatValue(), SeveredConfig.BLUR_SKID.get().floatValue(), state.windedUntil,
                SeveredConfig.ticks(SeveredConfig.WINDED_SECONDS.get()), state.recoveryUntil, state.closingReadyAt,
                SeveredConfig.ticks(SeveredConfig.CLOSING_COOLDOWN.get()));
        if (payload.equals(state.sent)) return;
        state.sent = payload;
        if (player.connection != null) PacketDistributor.sendToPlayer(player, payload);
    }

    private static double sq(double v) {
        return v * v;
    }
}
