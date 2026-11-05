package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.loadout.SharedAbilityData;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 *   <li>On success the server picks the orb's position a few blocks in front of them and sends it to that player
 *       only ({@link OrbPayload}); nobody else is told. It fades after its lifetime.</li>
 *   <li>Each tick the server checks the player's look vector against the position it picked; looking at it for the
 *       configured time (without looking away) takes it in: RMT unlocked, real healing, a private line in chat.</li>
 * </ul>
 */
public final class GreenOrbService {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Kegare.MODID);
    public static final Supplier<AttachmentType<RmtState>> STATE = ATTACHMENTS.register("rmt_state",
            () -> AttachmentType.builder(() -> new RmtState()).build());

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
        // A few blocks ahead, kept near eye level (half the look's pitch), and short of any wall in the way.
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, look.y * 0.5, look.z).normalize();
        Vec3 end = eye.add(dir.scale(RmtConfig.ORB_DISTANCE.get()));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 pos = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(dir.scale(0.4));
        RmtState state = state(player);
        int lifetime = RmtConfig.ticks(RmtConfig.ORB_LIFETIME);
        state.orb = pos;
        state.orbExpires = player.level().getGameTime() + lifetime;
        state.lookTicks = 0;
        PacketDistributor.sendToPlayer(player, new OrbPayload(OrbPayload.Action.SHOW, pos, lifetime));
    }

    /** Every server tick for players: fade the orb on time, or count how long they look at it. */
    static void tick(ServerPlayer player) {
        RmtState state = state(player);
        if (state.orb == null) return;
        long now = player.level().getGameTime();
        if (now >= state.orbExpires) { // the client fades it on the same timer
            state.orb = null;
            return;
        }
        if (!player.isAlive() || !eligible(player)) {
            clear(player);
            return;
        }
        Vec3 to = state.orb.subtract(player.getEyePosition());
        double cos = to.lengthSqr() < 1.0e-6 ? 1 : player.getLookAngle().dot(to.normalize());
        if (cos >= Math.cos(Math.toRadians(RmtConfig.ORB_LOOK_ANGLE.get()))) {
            if (++state.lookTicks >= Math.max(1, RmtConfig.ticks(RmtConfig.ORB_LOOK_SECONDS))) takeIn(player);
        } else {
            state.lookTicks = 0;
        }
    }

    /** Removes a waiting orb (death, Severed...). */
    static void clear(ServerPlayer player) {
        RmtState state = state(player);
        if (state.orb == null) return;
        state.orb = null;
        PacketDistributor.sendToPlayer(player, new OrbPayload(OrbPayload.Action.HIDE, Vec3.ZERO, 0));
    }

    private static void takeIn(ServerPlayer player) {
        RmtState state = state(player);
        Vec3 pos = state.orb;
        state.orb = null;
        if (!SharedAbilityService.unlock(player, Rmt.REVERSE_MALICE_TECHNIQUE)) return;
        player.heal((float) Math.min(RmtConfig.ORB_UNLOCK_HEAL.get(), Mth.clamp(player.getMaxHealth() - player.getHealth(), 0, Float.MAX_VALUE)));
        PacketDistributor.sendToPlayer(player, new OrbPayload(OrbPayload.Action.TAKEN, pos, 0));
        player.sendSystemMessage(Component.translatable("message.kegare.rmt.unlocked").withStyle(ChatFormatting.GREEN));
        RmtAdvancements.onUnlock(player);
    }
}
