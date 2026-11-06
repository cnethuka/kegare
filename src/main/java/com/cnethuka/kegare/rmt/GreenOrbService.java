package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.loadout.SharedAbilityData;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The green orb: how Reverse Malice Technique is found (server side; DESIGN §3.18).
 * <ul>
 *   <li>A hit from a player that leaves an eligible player (born, not Severed, RMT still locked, no orb waiting) at
 *       the health threshold or below rolls the chance, at most once per roll cooldown.</li>
 *   <li>On success the server rolls the orb's route a few blocks in front of them and sends it to that player
 *       only ({@link OrbPayload}); nobody else is told. It fades after its lifetime.</li>
 *   <li>Looking at it sends it off along a random route the server rolled. Each tick the server checks the player's look
 *       against where it is now: while they keep it in sight it moves on, and if they lose it, it runs back. Reaching the
 *       end takes it in: RMT unlocked, real healing, a private line in chat.</li>
 * </ul>
 */
public final class GreenOrbService {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);
    public static final Supplier<AttachmentType<RmtState>> STATE = ATTACHMENTS.register("rmt_state",
            () -> AttachmentType.builder(() -> new RmtState()).build());

    /** Closer than this and the look check would be meaningless, as when pressed against a wall. */
    private static final double MIN_DISTANCE = 0.75;

    /** Result of a forced spawn (the debug command). */
    public enum ForceResult { SPAWNED, ALREADY_UNLOCKED, SEVERED, NOT_BORN }

    private GreenOrbService() {}

    static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static RmtState state(ServerPlayer player) {
        return player.getData(STATE);
    }

    /** Never for Severed players, the unborn, or anyone who already has Reverse Malice Technique. */
    public static boolean eligible(ServerPlayer player) {
        return MaliceService.get(player).isBorn() && !MaliceService.isSevered(player)
                && !SharedAbilityService.isUnlocked(player, RmtIds.ABILITY);
    }

    /** A player just hurt {@code victim}; called after the damage applied. */
    static void onHitByPlayer(ServerPlayer victim) {
        long now = victim.level().getGameTime();
        state(victim).lastHitByPlayer = now;
        if (!victim.isAlive() || victim.getHealth() > RmtConfig.ORB_HEALTH_THRESHOLD.get()) return;
        if (!eligible(victim) || state(victim).hasOrb()) return;
        SharedAbilityData data = SharedAbilityService.get(victim);
        long cooldown = RmtConfig.ticks(RmtConfig.ORB_ROLL_COOLDOWN);
        if (data.lastOrbRoll() != Long.MIN_VALUE && now - data.lastOrbRoll() < cooldown) return;
        data.setLastOrbRoll(now);
        if (victim.getRandom().nextDouble() < RmtConfig.ORB_CHANCE.get()) spawn(victim);
    }

    /** Debug: an orb now, regardless of chance and roll cooldown (still never for the ineligible). */
    public static ForceResult force(ServerPlayer player) {
        if (!MaliceService.get(player).isBorn()) return ForceResult.NOT_BORN;
        if (MaliceService.isSevered(player)) return ForceResult.SEVERED;
        if (SharedAbilityService.isUnlocked(player, RmtIds.ABILITY)) return ForceResult.ALREADY_UNLOCKED;
        spawn(player);
        return ForceResult.SPAWNED;
    }

    /** Ticks until this player's next roll is allowed (0 = now). */
    public static int rollCooldownRemaining(ServerPlayer player) {
        long last = SharedAbilityService.get(player).lastOrbRoll();
        if (last == Long.MIN_VALUE) return 0;
        return (int) Math.max(0, RmtConfig.ticks(RmtConfig.ORB_ROLL_COOLDOWN) - (player.level().getGameTime() - last));
    }

    private static void spawn(ServerPlayer player) {
        // It starts a few blocks ahead, off to one side of the crosshair, near eye level (half the look's pitch).
        float side = RmtConfig.ORB_SIDE_ANGLE.get().floatValue() * (player.getRandom().nextBoolean() ? 1 : -1);
        OrbPath.Shape shape = new OrbPath.Shape(RmtConfig.ORB_PATH_LEGS.get(), RmtConfig.ORB_PATH_STEP.get().floatValue(),
                RmtConfig.ORB_PATH_YAW_RANGE.get().floatValue(), RmtConfig.ORB_PATH_PITCH_RANGE.get().floatValue(), RmtConfig.ORB_PATH_MAX_TURN.get().floatValue());
        List<Vec3> points = new ArrayList<>();
        for (Vec2 offset : OrbPath.route(player.getRandom(), new Vec2(side, 0), shape)) {
            points.add(pointAt(player, player.getXRot() * 0.5f + offset.y, player.getYRot() + offset.x));
        }
        RmtState state = state(player);
        state.path = new OrbPath(points);
        state.orbExpires = player.level().getGameTime() + RmtConfig.ticks(RmtConfig.ORB_LIFETIME);
        state.followTicks = 0;
        state.followTotal = Math.max(1, RmtConfig.ticks(RmtConfig.ORB_FOLLOW_SECONDS));
        PacketDistributor.sendToPlayer(player, OrbPayload.show(state.path, state.followTotal));
    }

    /** {@code distance} blocks that way, or short of any wall in the way. */
    private static Vec3 pointAt(ServerPlayer player, float pitch, float yaw) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = eye.add(dir.scale(RmtConfig.ORB_DISTANCE.get()));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return end;
        return eye.add(dir.scale(Math.max(MIN_DISTANCE, hit.getLocation().distanceTo(eye) - 0.4)));
    }

    /** Every server tick for players: fade the orb on time, and move it on or back by where they're looking. */
    static void tick(ServerPlayer player) {
        RmtState state = state(player);
        if (!state.hasOrb()) return;
        if (state.followTicks > 0) state.orbExpires++; // a follow under way can't fade
        if (!player.isAlive() || !eligible(player) || player.level().getGameTime() >= state.orbExpires) {
            clear(player);
            return;
        }
        int before = state.followTicks;
        if (isLookingAt(player, state.orb())) {
            if (++state.followTicks >= state.followTotal) {
                takeIn(player);
                return;
            }
        } else {
            state.followTicks = Math.max(0, state.followTicks - RmtConfig.ORB_FOLLOW_REWIND.get());
        }
        if (state.followTicks != before) PacketDistributor.sendToPlayer(player, OrbPayload.progress(state.followTicks));
    }

    private static boolean isLookingAt(ServerPlayer player, Vec3 point) {
        Vec3 to = point.subtract(player.getEyePosition());
        double cos = to.lengthSqr() < 1.0e-6 ? 1 : player.getLookAngle().dot(to.normalize());
        return cos >= Math.cos(Math.toRadians(RmtConfig.ORB_LOOK_ANGLE.get()));
    }

    /** Removes a waiting orb (death, Severed...). */
    static void clear(ServerPlayer player) {
        RmtState state = state(player);
        if (!state.hasOrb()) return;
        state.clearOrb();
        PacketDistributor.sendToPlayer(player, OrbPayload.hide());
    }

    private static void takeIn(ServerPlayer player) {
        RmtState state = state(player);
        Vec3 pos = state.path.at(1);
        state.clearOrb();
        if (!SharedAbilityService.unlock(player, Rmt.REVERSE_MALICE_TECHNIQUE)) return;
        player.heal((float) Math.min(RmtConfig.ORB_UNLOCK_HEAL.get(), Mth.clamp(player.getMaxHealth() - player.getHealth(), 0, Float.MAX_VALUE)));
        PacketDistributor.sendToPlayer(player, OrbPayload.taken(pos));
        player.sendSystemMessage(Component.translatable("message.kegare.rmt.unlocked").withStyle(ChatFormatting.GREEN));
        RmtAdvancements.onUnlock(player);
    }
}
