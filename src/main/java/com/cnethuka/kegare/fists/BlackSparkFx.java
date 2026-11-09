package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.melee.MeleeTell;
import com.cnethuka.kegare.sound.KegareSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Black Spark's look and tell (DESIGN §3.28, §3.15): black sparks and crackling black arcs bursting from the point of contact, fringed in
 * the attacker's Malice color, for one sharp moment, and a sharp crack. Everyone within {@link #RANGE} sees it; the attacker sees their own
 * color, everyone else the color of the face a disguise shows. The client draws it ({@code fists/client/BlackSparkClient}).
 */
public final class BlackSparkFx {
    static final double RANGE = 48;

    private BlackSparkFx() {}

    static void play(ServerPlayer attacker, LivingEntity target) {
        ServerLevel level = attacker.serverLevel();
        Vec3 from = attacker.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 centre = box.getCenter();
        Vec3 at = box.contains(from) ? centre : box.clip(from, centre).orElse(centre);
        Vec3 direction = centre.subtract(from);
        direction = direction.lengthSqr() < 1.0e-6 ? attacker.getLookAngle() : direction.normalize();
        int own = color(MeleeTell.of(attacker)), others = color(MeleeTell.seenByOthers(attacker));
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(at) > RANGE * RANGE) continue;
            PacketDistributor.sendToPlayer(viewer, new BlackSparkPayload(at, direction, viewer == attacker ? own : others, attacker.getId(), target.getId()));
        }
        level.playSound(null, at.x, at.y, at.z, KegareSounds.BLACK_SPARK.get(), SoundSource.PLAYERS, 1.6f, 1.0f);
    }

    private static int color(MeleeTell.Look look) {
        return look.kind() == MeleeTell.Kind.GLOW ? look.color() : com.cnethuka.kegare.innatetechnique.InnateTechnique.DEFAULT_MALICE_COLOR;
    }
}
