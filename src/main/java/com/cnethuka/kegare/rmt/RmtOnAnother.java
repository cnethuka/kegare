package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.SpecialAbility;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.InsightHooks;
import com.cnethuka.kegare.progression.UnlockRequirements;
import com.cnethuka.kegare.progression.Unlocks;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Reverse Malice Technique's special (R + its key, CHANNELED, DESIGN §3.17): healing another player. Few can, in canon. Eligible with
 * RMT unlocked and Output Insight at {@code healOthersInsight} or more (the Unlocks framework's {@code kegare:rmt_heal_others}, which
 * survives rerolls); then learned in a chance moment ({@link #onDroppedLow}): a player near you drops to 3 hearts, and with a little
 * luck they glow soft green to you alone for a few seconds; heal them then, and you know it for good. It heals the player you're looking
 * at within {@code range}, in line of sight, at {@code othersOutput} of YOUR output (your Grade, Peak and injuries, not theirs), for
 * {@code othersCost} times RMT's price. Any player, Severed included; never a mob. Ends when they leave range or sight, on your stun or
 * freeze, or out of Malice; keeps running at their full health; shares RMT's cooldown. The tell: a white-and-green link from your hand
 * to them, seen by everyone, and a soft chime as it starts.
 */
public final class RmtOnAnother extends SpecialAbility {
    /** How long a link a client has stopped hearing about stays drawn: a little over the second between resends. */
    static final int LINK_RESEND_TICKS = 20;

    RmtOnAnother() {
        super(RmtIds.ON_ANOTHER, CastType.CHANNELED, () -> Rmt.REVERSE_MALICE_TECHNIQUE);
    }

    /** The Unlocks framework's entry: eligible with RMT unlocked and enough Output Insight; given only by the moment (or an admin). */
    static void register() {
        Unlocks.register(RmtIds.ON_ANOTHER_UNLOCK, UnlockRequirements.builder()
                .condition(player -> SharedAbilityService.isUnlocked(player, RmtIds.ABILITY)
                        && Insight.get(player, InsightCategory.OUTPUT) >= RmtConfig.OTHERS_INSIGHT.get())
                .grantedOnly()
                .build());
    }

    @Override
    public com.cnethuka.kegare.loadout.AbilityCategory category() {
        return com.cnethuka.kegare.loadout.AbilityCategory.MALICE;
    }

    @Override public double cost(ServerPlayer p) { return RmtConfig.COST.get() * RmtConfig.OTHERS_COST.get(); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return RmtConfig.UPKEEP.get() * RmtConfig.OTHERS_COST.get(); }
    @Override public int maxChannelTicks(ServerPlayer p) { return RmtConfig.ticks(RmtConfig.MAX_DURATION); }
    @Override
    public Object[] descriptionArgs() {
        return new Object[] {com.cnethuka.kegare.config.ConfigText.number(RmtConfig.OTHERS_RANGE),
                com.cnethuka.kegare.config.ConfigText.percent(RmtConfig.OTHERS_OUTPUT), com.cnethuka.kegare.config.ConfigText.number(RmtConfig.OTHERS_COST)};
    }

    public static boolean knows(ServerPlayer player) {
        return Unlocks.has(player, RmtIds.ON_ANOTHER_UNLOCK);
    }

    /** Eligible to be taught it, and not yet taught. */
    static boolean couldLearn(ServerPlayer player) {
        return !knows(player) && Unlocks.eligible(player, RmtIds.ON_ANOTHER_UNLOCK);
    }

    /** The player under the crosshair within reach, in line of sight; never a mob, never themself. */
    static Optional<ServerPlayer> lookedAt(ServerPlayer caster) {
        double range = RmtConfig.OTHERS_RANGE.get(), tolerance = 0.3;
        Vec3 eye = caster.getEyePosition(), look = caster.getViewVector(1f);
        BlockHitResult block = caster.level().clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double reach = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer other : caster.serverLevel().players()) {
            if (other == caster || !other.isAlive() || other.isSpectator()) continue;
            AABB box = other.getBoundingBox().inflate(tolerance);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Still in reach and in sight: from the healer's eyes to the nearest point of them, within range, nothing solid between. */
    static boolean stillThere(ServerPlayer caster, Player target) {
        if (!target.isAlive() || target.isRemoved() || target.isSpectator() || target.level() != caster.level()) return false;
        AABB box = target.getBoundingBox();
        Vec3 eye = caster.getEyePosition();
        Vec3 nearest = new Vec3(Math.max(box.minX, Math.min(eye.x, box.maxX)), Math.max(box.minY, Math.min(eye.y, box.maxY)),
                Math.max(box.minZ, Math.min(eye.z, box.maxZ)));
        return eye.distanceTo(nearest) <= RmtConfig.OTHERS_RANGE.get() && caster.hasLineOfSight(target);
    }

    /** Whether {@code target} is glowing for {@code caster} right now. */
    static boolean inMoment(ServerPlayer caster, Player target) {
        RmtState state = GreenOrbService.state(caster);
        return target.getUUID().equals(state.momentTarget) && caster.level().getGameTime() < state.momentUntil;
    }

    @Override
    public boolean canCast(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Optional<ServerPlayer> target = lookedAt(p);
        return target.isPresent() && (knows(p) || (inMoment(p, target.get()) && couldLearn(p)));
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        Optional<ServerPlayer> target = lookedAt(player);
        if (!knows(player) && (target.isEmpty() || !inMoment(player, target.get()))) {
            return Component.translatable("message.kegare.rmt.dont_know");
        }
        return Component.translatable("message.kegare.rmt.no_one_to_heal", net.minecraft.network.chat.Component.literal(
                com.cnethuka.kegare.config.ConfigText.number(RmtConfig.OTHERS_RANGE)));
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        ServerPlayer target = lookedAt(p).orElse(null);
        if (target == null) {
            CastManager.fizzleKeepingCost(p);
            return;
        }
        RmtState state = GreenOrbService.state(p);
        if (!knows(p)) { // the moment: learned, for good
            Unlocks.grant(p, RmtIds.ON_ANOTHER_UNLOCK);
            p.sendSystemMessage(Component.translatable("message.kegare.rmt.learned_others"));
            endMoment(p);
        }
        state.healing = target.getUUID();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.8f);
        sendLink(p, target, true);
    }

    @Override
    public void tick(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        RmtState state = GreenOrbService.state(p);
        Player target = state.healing == null ? null : p.level().getPlayerByUUID(state.healing);
        if (target == null || !stillThere(p, target)) { // out of reach or sight: it's over
            CastManager.finishChannel(p);
            return;
        }
        float healed = ReverseMaliceTechnique.heal(p, target, RmtConfig.HEAL_PER_SECOND.get() * RmtOutput.of(p) * RmtConfig.OTHERS_OUTPUT.get() / 20.0);
        if (healed > 0f && target instanceof ServerPlayer healedPlayer) InsightHooks.healedByOther(healedPlayer);
        if (p.level().getGameTime() - state.linkSentAt >= LINK_RESEND_TICKS) sendLink(p, target, true);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        ServerPlayer p = ctx.player();
        RmtState state = GreenOrbService.state(p);
        Player target = state.healing == null ? null : p.level().getPlayerByUUID(state.healing);
        state.healing = null;
        sendLink(p, target, false);
    }

    private static void sendLink(ServerPlayer caster, @Nullable Player target, boolean on) {
        GreenOrbService.state(caster).linkSentAt = caster.level().getGameTime();
        RmtNet.Link link = new RmtNet.Link(caster.getId(), target == null ? -1 : target.getId(), on);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, link);
    }

    // ---------------------------------------------------------------- the moment

    /** {@code low} just dropped to momentHealth or less: everyone eligible near them may get their moment. */
    static void onDroppedLow(ServerPlayer low) {
        long now = low.level().getGameTime();
        double range = RmtConfig.MOMENT_RANGE.get();
        for (ServerPlayer near : low.serverLevel().players()) {
            if (near == low || near.distanceToSqr(low) > range * range || !couldLearn(near)) continue;
            RmtState state = GreenOrbService.state(near);
            if (now < state.momentUntil) continue; // one already showing
            if (now - state.lastMomentRoll < RmtConfig.ticks(RmtConfig.MOMENT_COOLDOWN)) continue;
            state.lastMomentRoll = now;
            if (near.getRandom().nextDouble() < RmtConfig.MOMENT_CHANCE.get()) startMoment(near, low);
        }
    }

    /** Their moment, now: {@code target} glows soft green to them alone. Also the debug command's. */
    public static void startMoment(ServerPlayer eligible, ServerPlayer target) {
        RmtState state = GreenOrbService.state(eligible);
        state.momentTarget = target.getUUID();
        state.momentUntil = eligible.level().getGameTime() + RmtConfig.ticks(RmtConfig.MOMENT_SECONDS);
        if (eligible.connection != null && !eligible.isFakePlayer()) {
            PacketDistributor.sendToPlayer(eligible, new RmtNet.Moment(target.getId(), state.momentUntil));
        }
    }

    static void endMoment(ServerPlayer player) {
        RmtState state = GreenOrbService.state(player);
        state.momentTarget = null;
        state.momentUntil = 0;
        if (player.connection != null && !player.isFakePlayer()) PacketDistributor.sendToPlayer(player, RmtNet.Moment.NONE);
    }
}
