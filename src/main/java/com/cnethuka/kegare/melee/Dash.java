package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.malice.MaliceService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Kinetic Manipulation: a short burst the way you're moving. The server decides if it happens, which way and how far (never past
 * a wall, measured here), then tells the owner's client once, and that drives it ({@code client/DashDrive}). The game's own movement
 * collision stops it, so it can't go through anything. Pushing velocity from here each tick was tried first and isn't good
 * enough for this: two pushes landing in one client tick lose one, and a dash came out anywhere from 3 to 5 blocks.
 * There's no invulnerability: you have to actually get out of the way.
 */
public final class Dash {
    /** Less room than this ahead isn't a dash, it's a bump: refused, and nothing is paid. */
    static final double MIN_CLEAR = 0.5;
    private static final double SWEEP_STEP = 0.2;
    private static final double STEP_HEIGHT = 0.6;

    private Dash() {}

    /** The world direction for movement keys ({@code forward}, {@code left} in -1..1) at {@code yaw}; none held is forward. */
    public static Vec3 direction(float yaw, int forward, int left) {
        int f = Mth.clamp(forward, -1, 1);
        int l = Mth.clamp(left, -1, 1);
        if (f == 0 && l == 0) f = 1;
        float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(l * cos - f * sin, 0, f * cos + l * sin).normalize();
    }

    /** How far the player's box can travel that way before something solid stops it, up to {@code max}. A step they could walk up doesn't. */
    public static double clearDistance(ServerPlayer player, Vec3 direction, double max) {
        AABB box = player.getBoundingBox();
        double clear = 0;
        double lift = 0;
        while (clear < max) {
            double next = Math.min(max, clear + SWEEP_STEP);
            AABB moved = box.move(direction.scale(next)).move(0, lift, 0);
            if (!player.level().noCollision(player, moved)) {
                if (lift > 0 || !player.level().noCollision(player, moved.move(0, STEP_HEIGHT, 0))) return clear;
                lift = STEP_HEIGHT;
            }
            clear = next;
        }
        return clear;
    }

    /** The request. Everything here is the server's own: the client's only say is which way, and that's free anyway. */
    static void request(ServerPlayer player, int forward, int left) {
        MeleeState state = MeleeLayer.state(player);
        long now = player.level().getGameTime();
        if (!MeleeRules.canAct(player) || state.dashDirection != null || now < state.dashReadyAt) return;
        if (player.isPassenger() || player.isFallFlying() || player.isSleeping()) return;
        MeleeNumbers numbers = MeleeNumbers.of(player);
        Vec3 direction = direction(player.getYRot(), forward, left);
        double clear = clearDistance(player, direction, numbers.dashDistance());
        if (clear < MIN_CLEAR) return;
        if (!MaliceService.trySpend(player, numbers.dashCost())) {
            player.displayClientMessage(Component.translatable("message.kegare.melee.no_malice", Component.translatable("key.kegare.dash")), true);
            return;
        }
        if (state.guarding) Parry.end(player, state); // a guard can't go with you, and comes down like any other
        Vec3 from = player.position();
        state.dashReadyAt = now + numbers.dashCooldownTicks();
        double speed = MeleeConfig.DASH_SPEED.get();
        state.dashDirection = direction;
        state.dashDistance = clear;
        state.dashTicksLeft = Mth.ceil(clear / speed) + 1;
        CleanDodge.start(player, state);
        MeleeImpact.DASH_BURST.fire(player.serverLevel(), from, player);
        VisualService.playOnce(player, Visual.of(animation(forward, left), MeleeIds.FX_DASH_TRAIL, Visual.Anchor.FEET));
        PacketDistributor.sendToPlayer(player, new MeleeDashPayload((float) direction.x, (float) direction.z, (float) speed, (float) clear));
        MeleeLayer.syncHud(player);
    }

    static ResourceLocation animation(int forward, int left) {
        if (forward < 0) return MeleeIds.ANIM_DASH_BACK;
        if (forward > 0 || left == 0) return MeleeIds.ANIM_DASH_FORWARD;
        return left > 0 ? MeleeIds.ANIM_DASH_LEFT : MeleeIds.ANIM_DASH_RIGHT;
    }

    static void tick(ServerPlayer player, MeleeState state) {
        if (state.dashDirection == null) return;
        if (!MeleeRules.canAct(player)) { // a stun or a freeze stops it where it is
            cancel(player, state);
            return;
        }
        if (--state.dashTicksLeft <= 0) state.dashDirection = null;
    }

    static void cancel(ServerPlayer player, MeleeState state) {
        if (state.dashDirection == null) return;
        state.dashDirection = null;
        PacketDistributor.sendToPlayer(player, MeleeDashPayload.STOP);
    }
}
