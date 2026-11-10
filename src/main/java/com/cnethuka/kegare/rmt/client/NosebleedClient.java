package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.rmt.Repair;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A repair's tell (DESIGN §3.17): while someone repairs a burnt-out technique, their nose bleeds. It starts at the nostrils and runs down
 * past the mouth to the chin ({@link NosebleedLayer}, a pixel at a time), and drops fall from the chin. Every effects setting.
 * Not in your own first person (it's for others to see); an invisible player shows nothing. It stays a moment after the repair stops, then
 * goes from the nose down.
 */
public final class NosebleedClient {
    /** Ticks for the streak to run one pixel further. */
    static final int TICKS_A_PIXEL = 5;
    /** After the repair stops: how long the streak stays, then how fast it goes. */
    private static final int STAYS = 40, TICKS_A_PIXEL_GONE = 3;
    private static final int DROP_EVERY = 4, DROP_LIFE = 14;
    private static final float DROP_SCALE = 0.4f;
    /** How far the head's pivot sits below the eyes, and how far out the chin is from it. */
    private static final double NECK_BELOW_EYES = 0.12, CHIN_OUT = 0.27;
    /** A hard little square of solid red that falls. Dust would be spiky and hang where it's made, a dark splat over the mouth. */
    private static final BlockParticleOption DROP = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());

    /** Who's bleeding: game time it started, and when it stopped (-1 still going). */
    private static final class Bleed {
        final long started;
        long stopped = -1;

        Bleed(long started) {
            this.started = started;
        }
    }

    private static final Map<Integer, Bleed> BLEEDING = new HashMap<>();

    private NosebleedClient() {}

    public static void init(IEventBus modBus) {
        Repair.Payload.setClientHandler(p -> {
            ClientLevel level = Minecraft.getInstance().level;
            long now = level == null ? 0 : level.getGameTime();
            if (p.on()) {
                Bleed bleed = BLEEDING.get(p.entityId());
                if (bleed == null || bleed.stopped >= 0) BLEEDING.put(p.entityId(), new Bleed(now));
            } else {
                Bleed bleed = BLEEDING.get(p.entityId());
                if (bleed != null) bleed.stopped = now;
            }
        });
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> NosebleedLayer.addLayers(e));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BLEEDING.clear());
    }

    /** How far along the streak this player's blood has run, and how much has gone from its top: {from, to} of its pixels, or null for none. */
    static int[] streak(int entityId, long now) {
        Bleed bleed = BLEEDING.get(entityId);
        if (bleed == null) return null;
        int to = (int) ((now - bleed.started) / TICKS_A_PIXEL) + 1;
        int from = 0;
        if (bleed.stopped >= 0) {
            to = (int) ((bleed.stopped - bleed.started) / TICKS_A_PIXEL) + 1;
            from = (int) Math.max(0, (now - bleed.stopped - STAYS) / TICKS_A_PIXEL_GONE);
        }
        return new int[] {from, Math.min(to, NosebleedLayer.LENGTH)};
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || BLEEDING.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Map.Entry<Integer, Bleed>> it = BLEEDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Bleed> entry = it.next();
            Bleed bleed = entry.getValue();
            Entity e = level.getEntity(entry.getKey());
            int[] streak = streak(entry.getKey(), now);
            if (!(e instanceof Player player) || !player.isAlive() || streak[0] >= streak[1]) {
                it.remove();
                continue;
            }
            if (bleed.stopped >= 0 || player.isInvisibleTo(mc.player) || streak[1] < NosebleedLayer.LENGTH || now % DROP_EVERY != 0) continue;
            if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) continue;
            // Off the chin, wherever the head is turned.
            Vec3 chin = player.getEyePosition().add(0, -NECK_BELOW_EYES, 0).add(Vec3.directionFromRotation(player.getXRot(), player.getYHeadRot()).scale(CHIN_OUT));
            Particle drop = mc.particleEngine.createParticle(DROP, chin.x, chin.y, chin.z, 0, 0, 0);
            if (drop != null) {
                drop.setParticleSpeed(0, 0, 0); // straight down, whatever the constructor made of it
                drop.setLifetime(DROP_LIFE);
                drop.setColor(1f, 1f, 1f); // (a block's crumb is shaded down by default)
                drop.scale(DROP_SCALE);
            }
        }
    }
}
