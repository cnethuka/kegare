package com.cnethuka.kegare.innatetechnique.visual;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.network.VisualPayload;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of cosmetics. Every call sends one packet to the entity's own player (if any) and everyone tracking
 * it, and returns immediately: VISUAL ONLY, server timing never waits on animations or effects.
 * <p>
 * Loops are remembered per entity (runtime only) and re-sent to players who start tracking it later, so a visible
 * loop always means the thing it shows is active.
 */
public final class VisualService {
    /** Range for effects that play at a position (impacts). */
    private static final double POSITION_RANGE = 64.0;

    private VisualService() {}

    public static String key(Ability ability, VisualPhase phase) {
        return ability.id() + "/" + phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    // ---------------------------------------------------------------- abilities

    /** Plays an ability's phase on its caster: once, or as a loop keyed by ability + phase. */
    public static void ability(ServerPlayer caster, Ability ability, VisualPhase phase) {
        ability(caster, ability, phase, 0);
    }

    /** @param growTicks loops only: grow from small to full size over this many ticks */
    public static void ability(ServerPlayer caster, Ability ability, VisualPhase phase, int growTicks) {
        Visual visual = ability.visuals().get(phase);
        if (visual.isEmpty()) return;
        if (phase.loops()) {
            startLoop(caster, key(ability, phase), visual, growTicks);
        } else {
            playOnce(caster, visual);
        }
    }

    /** Stops an ability's loop phase on its caster. */
    public static void stopAbility(ServerPlayer caster, Ability ability, VisualPhase phase) {
        stopLoop(caster, key(ability, phase));
    }

    /** An ability's impact effect at a position (players within range of it). */
    public static void impact(ServerLevel level, Ability ability, Vec3 pos) {
        ability.visuals().get(VisualPhase.IMPACT).effect().ifPresent(effect ->
                playAt(level, pos, Visual.effect(effect, Visual.Anchor.BODY)));
    }

    // ---------------------------------------------------------------- generic

    public static void playOnce(Entity entity, Visual visual) {
        if (visual.isEmpty()) return;
        send(entity, new VisualPayload(VisualPayload.Action.PLAY_ONCE, entity.getId(), "", visual, Vec3.ZERO, 0));
    }

    /** One moment that looks different to the player it's on than to everyone else (see {@code MeleeTell}). */
    public static void playOnce(ServerPlayer owner, Visual own, Visual others) {
        PacketDistributor.sendToPlayer(owner, new VisualPayload(VisualPayload.Action.PLAY_ONCE, owner.getId(), "", own, Vec3.ZERO, 0));
        PacketDistributor.sendToPlayersTrackingEntity(owner, new VisualPayload(VisualPayload.Action.PLAY_ONCE, owner.getId(), "", others, Vec3.ZERO, 0));
    }

    public static void startLoop(Entity entity, String key, Visual visual, int growTicks) {
        startLoop(entity, key, visual, growTicks, 0);
    }

    /** @param lifetimeTicks lifetime of the effect's persistent ({@code persist*}) particles, e.g. a trail's config duration */
    public static void startLoop(Entity entity, String key, Visual visual, int growTicks, int lifetimeTicks) {
        if (visual.isEmpty()) return;
        entity.getData(KegareAttachments.VISUAL_STATE).loops.put(key, new VisualState.Loop(visual, growTicks, entity.level().getGameTime(), lifetimeTicks));
        send(entity, new VisualPayload(VisualPayload.Action.START_LOOP, entity.getId(), key, visual, Vec3.ZERO, growTicks, lifetimeTicks));
    }

    public static void stopLoop(Entity entity, String key) {
        if (!entity.hasData(KegareAttachments.VISUAL_STATE)) return;
        if (entity.getData(KegareAttachments.VISUAL_STATE).loops.remove(key) == null) return;
        send(entity, new VisualPayload(VisualPayload.Action.STOP, entity.getId(), key, Visual.NONE, Vec3.ZERO, 0));
    }

    /** Stops everything on the entity (death, logout, dimension change). Sent even if nothing is known to play. */
    public static void stopAll(Entity entity) {
        if (entity.hasData(KegareAttachments.VISUAL_STATE)) entity.getData(KegareAttachments.VISUAL_STATE).loops.clear();
        send(entity, new VisualPayload(VisualPayload.Action.STOP_ALL, entity.getId(), "", Visual.NONE, Vec3.ZERO, 0));
    }

    public static void playAt(ServerLevel level, Vec3 pos, Visual visual) {
        playAt(level, pos, visual, -1);
    }

    /**
     * An effect at a position that names the entity it came from ({@code entityId}), for a client that draws part of it itself from
     * that entity (Glacial Lance stuck where it landed). Sent before the entity is removed, so it's still there to read.
     */
    public static void playAt(ServerLevel level, Vec3 pos, Visual visual, int entityId) {
        if (visual.isEmpty()) return;
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, POSITION_RANGE,
                new VisualPayload(VisualPayload.Action.PLAY_AT, entityId, "", visual, pos, 0));
    }

    /** An effect at a position that looks different to {@code owner} than to everyone else in range. */
    public static void playAt(ServerLevel level, Vec3 pos, ServerPlayer owner, Visual own, Visual others) {
        if (!others.isEmpty()) PacketDistributor.sendToPlayersNear(level, owner, pos.x, pos.y, pos.z, POSITION_RANGE, new VisualPayload(VisualPayload.Action.PLAY_AT, -1, "", others, pos, 0));
        if (!own.isEmpty() && owner.level() == level && owner.position().closerThan(pos, POSITION_RANGE)) {
            PacketDistributor.sendToPlayer(owner, new VisualPayload(VisualPayload.Action.PLAY_AT, -1, "", own, pos, 0));
        }
    }

    /** A player started tracking {@code target}: send them its running loops (they missed the starts). */
    public static void onStartTracking(ServerPlayer tracker, Entity target) {
        if (!target.hasData(KegareAttachments.VISUAL_STATE)) return;
        long now = target.level().getGameTime();
        for (Map.Entry<String, VisualState.Loop> e : target.getData(KegareAttachments.VISUAL_STATE).loops.entrySet()) {
            VisualState.Loop loop = e.getValue();
            // A growing loop resumes part-way: grow over what's left of its time.
            int grow = loop.growTicks() <= 0 ? 0 : (int) Math.max(1, loop.growTicks() - (now - loop.startTick()));
            Visual visual = loop.visual();
            // A late viewer's looping animation simply starts from its first frame.
            PacketDistributor.sendToPlayer(tracker, new VisualPayload(VisualPayload.Action.START_LOOP, target.getId(),
                    e.getKey(), visual, Vec3.ZERO, grow, loop.lifetimeTicks()));
        }
    }

    private static void send(Entity entity, VisualPayload payload) {
        if (entity.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }
}
