package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.rmt.Repair;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * A repair's tell (DESIGN §3.17): while someone repairs a burnt-out technique, their nose bleeds, dark red drops running down from
 * under the eyes and falling. Plain dust particles, so it shows at every effects setting. Not in your own first person (it's for others
 * to see); an invisible player shows nothing.
 */
public final class NosebleedClient {
    private static final Set<Integer> BLEEDING = new HashSet<>();
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.03f), 0.75f);
    private static final DustParticleOptions BLOOD_DARK = new DustParticleOptions(new Vector3f(0.32f, 0.0f, 0.02f), 0.6f);
    private static final RandomSource RANDOM = RandomSource.create();

    private NosebleedClient() {}

    public static void init() {
        Repair.Payload.setClientHandler(p -> {
            if (p.on()) BLEEDING.add(p.entityId());
            else BLEEDING.remove(p.entityId());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BLEEDING.clear());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || BLEEDING.isEmpty()) return;
        for (int id : BLEEDING) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Player player) || !player.isAlive() || player.isInvisibleTo(mc.player)) continue;
            if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            // Under the eyes, at the front of the face, turning with the head.
            Vec3 forward = Vec3.directionFromRotation(0, player.getYHeadRot());
            Vec3 nose = player.getEyePosition().add(forward.scale(0.27)).add(0, -0.16, 0);
            for (int i = 0; i < 2; i++) {
                double side = (RANDOM.nextDouble() - 0.5) * 0.08;
                Vec3 at = nose.add(forward.z * side, -RANDOM.nextDouble() * 0.1, -forward.x * side);
                level.addParticle(RANDOM.nextInt(3) == 0 ? BLOOD_DARK : BLOOD, at.x, at.y, at.z, forward.x * 0.05, -0.9, forward.z * 0.05);
            }
        }
    }
}
