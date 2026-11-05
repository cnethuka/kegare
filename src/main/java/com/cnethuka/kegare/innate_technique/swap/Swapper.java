package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The moving part of Swap. Every position swap goes through here: both players move at once, both fall distances are
 * reset (only Momentum Exchange keeps fall distance, on purpose, and it never moves anyone), and both ends get their
 * effects and the sound. Callers fire {@link SwapTargetEvent} first.
 */
public final class Swapper {
    private Swapper() {}

    /** Swaps two players' positions, and their facing too if asked. */
    public static void swapPlayers(ServerPlayer a, ServerPlayer b, boolean facing) {
        Vec3 posA = a.position();
        Vec3 posB = b.position();
        float yawA = a.getYRot(), pitchA = a.getXRot();
        float yawB = b.getYRot(), pitchB = b.getXRot();
        depart(a);
        depart(b);
        SwapLink.play(a.serverLevel(), bodyPos(a), bodyPos(b));
        moveTo(a, posB, facing ? yawB : yawA, facing ? pitchB : pitchA);
        moveTo(b, posA, facing ? yawA : yawB, facing ? pitchA : pitchB);
        arrive(a);
        arrive(b);
    }

    /** Moves one player (keeping its own facing), resetting its fall distance. */
    public static void moveTo(ServerPlayer player, Vec3 pos) {
        moveTo(player, pos, player.getYRot(), player.getXRot());
    }

    public static void moveTo(ServerPlayer player, Vec3 pos, float yaw, float pitch) {
        if (player.isSleeping()) player.stopSleepInBed(true, true);
        if (player instanceof FakePlayer) { // no client to tell, and its connection ignores teleports
            player.stopRiding();
            player.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        } else {
            player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, pitch); // also dismounts
        }
        player.setYHeadRot(yaw);
        player.resetFallDistance();
    }

    /** Where someone leaves: a teal implosion and the sound. */
    public static void depart(Entity entity) {
        effect(entity, SwapIds.DEPART, 0.9f);
    }

    /** Where someone arrives. */
    public static void arrive(Entity entity) {
        effect(entity, SwapIds.ARRIVE, 1.15f);
    }

    private static void effect(Entity entity, Visual visual, float pitch) {
        ServerLevel level = (ServerLevel) entity.level();
        Vec3 at = bodyPos(entity);
        VisualService.playAt(level, at, visual);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, pitch);
    }

    public static Vec3 bodyPos(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() / 2.0, 0);
    }
}
