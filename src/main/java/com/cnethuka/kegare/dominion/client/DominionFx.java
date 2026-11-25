package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.dominion.DominionEntity;
import com.cnethuka.kegare.dominion.DominionNet;
import com.cnethuka.kegare.sound.KegareSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * Dominions' moments and air, on a client (DESIGN §3.35): its own sounds (rising and closing, breaking, clashing), dust drifting inside
 * a barrier while you're in it, sparks and grit flying off a clash's seam, and a broken barrier's shell flying apart: it swells, fades
 * and sheds grey shards. All vanilla particles and our own drawing (no Photon), so the same at every effects setting; Reduced and
 * Vanilla spawn half as many.
 */
public final class DominionFx {
    /** A broken barrier's shell, swelling and fading for {@link #FADE_TICKS}. */
    private record Fade(Vec3 centre, float radius, int color, boolean cracked, long at) {}

    private static final int FADE_TICKS = 14;
    private static final List<Fade> FADES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Nothing spawned closer than this to a first-person camera (DESIGN §3.13). */
    private static final double NEAR = 2.0;

    private DominionFx() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener(DominionFx::render);
    }

    private static float share() {
        return KegareClientConfig.VFX_QUALITY.get() == KegareClientConfig.VfxQuality.FULL ? 1f : 0.5f;
    }

    /** A moment's sound and look. */
    static void moment(DominionNet.Moment m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 at = new Vec3(m.x(), m.y(), m.z());
        switch (m.kind()) {
            // The barrier rising, and the seal as it closes a second later: one sound, heard by everyone near.
            case DominionNet.Moment.OPENING -> level.playLocalSound(at.x, at.y, at.z, KegareSounds.DOMINION_OPEN.get(), SoundSource.PLAYERS, 2.5f, 1f, false);
            case DominionNet.Moment.HIT -> {
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.2f, 0.6f, false);
                shards(level, at, Math.max(3, Math.round(6 * share())), 0.15);
            }
            case DominionNet.Moment.BROKE -> {
                level.playLocalSound(at.x, at.y, at.z, KegareSounds.DOMINION_BREAK.get(), SoundSource.PLAYERS, 3f, 1f, false);
                FADES.add(new Fade(at, m.radius(), m.shellColor(), m.cracked(), level.getGameTime()));
                int count = Math.round(160 * share());
                for (int i = 0; i < count; i++) {
                    Vec3 dir = upper(RANDOM);
                    Vec3 p = at.add(dir.scale(m.radius()));
                    spawn(level, mc.gameRenderer.getMainCamera(), shard(m.shellColor()), p, dir.scale(0.12).add(0, -0.05, 0));
                }
            }
            case DominionNet.Moment.ENDED -> level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 0.6f, false);
            case DominionNet.Moment.CLASH -> {
                level.playLocalSound(at.x, at.y, at.z, KegareSounds.DOMINION_CLASH.get(), SoundSource.PLAYERS, 3f, 1f, false);
                shards(level, at, Math.round(30 * share()), 0.25);
            }
            default -> {}
        }
    }

    /** A spray of grey shards and sparks off a point on a shell. */
    private static void shards(ClientLevel level, Vec3 at, int count, double speed) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() + 0.5, RANDOM.nextGaussian()).normalize().scale(speed);
            spawn(level, camera, i % 3 == 0 ? ParticleTypes.CRIT : shard(0x8E8983), at, v);
        }
    }

    private static ParticleOptions shard(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float lift = 0.35f; // a little lighter than the shell, so it reads against it
        return new DustParticleOptions(new Vector3f((r + (255 - r) * lift) / 255f, (g + (255 - g) * lift) / 255f, (b + (255 - b) * lift) / 255f), 1.4f);
    }

    private static Vec3 upper(RandomSource rng) {
        Vec3 v;
        do v = new Vec3(rng.nextGaussian(), Math.abs(rng.nextGaussian()), rng.nextGaussian());
        while (v.lengthSqr() < 1.0e-6);
        return v.normalize();
    }

    private static void spawn(ClientLevel level, Camera camera, ParticleOptions type, Vec3 at, Vec3 v) {
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && at.distanceTo(camera.getPosition()) < NEAR) return;
        level.addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    /** Every client tick: dust inside the barrier you're in, sparks off any clash's seam near you. */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        float share = share();
        for (DominionEntity d : DominionEntity.in(level)) {
            int dust = d.look().dustColor();
            if (dust != -1 && d.risen(0f) >= 1f && d.contains(cam)) {
                int count = Math.max(1, Math.round(5 * share));
                for (int i = 0; i < count; i++) {
                    Vec3 p = cam.add((RANDOM.nextDouble() - 0.5) * 24, (RANDOM.nextDouble() - 0.3) * 10, (RANDOM.nextDouble() - 0.5) * 24);
                    if (!d.contains(p)) continue;
                    if (i % 2 == 0) spawn(level, camera, ParticleTypes.WHITE_ASH, p, Vec3.ZERO);
                    else spawn(level, camera, new DustParticleOptions(rgb(dust), 1.1f), p, new Vec3(0.02, -0.005, 0.01));
                }
            }
            if (d.state() == DominionEntity.State.CLASH && level.getEntity(d.clashWith()) instanceof DominionEntity other && d.getId() < other.getId()
                    && d.centre().distanceTo(cam) < d.radius() + 96) {
                Optional<double[]> meet = DominionRenderer.meeting(d.centre(), d.radius(), other.centre(), other.radius());
                if (meet.isEmpty()) continue;
                double[] m = meet.get();
                Vec3 mid = new Vec3(m[0], m[1], m[2]), n = new Vec3(m[3], m[4], m[5]);
                Vec3 e1 = Math.abs(n.y) < 0.9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
                Vec3 e2 = n.cross(e1).normalize();
                int count = Math.max(2, Math.round(8 * share));
                for (int i = 0; i < count; i++) {
                    double t = RANDOM.nextDouble() * Mth.TWO_PI;
                    Vec3 p = mid.add(e1.scale(Math.cos(t) * m[6])).add(e2.scale(Math.sin(t) * m[6]));
                    if (p.y < Math.min(d.centre().y, other.centre().y) - 0.5) continue; // underground
                    Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.12, 0.05 + RANDOM.nextDouble() * 0.15, RANDOM.nextGaussian() * 0.12);
                    spawn(level, camera, i % 2 == 0 ? ParticleTypes.CRIT : shard(0x8E8983), p, v);
                }
            }
        }
        long now = level.getGameTime();
        FADES.removeIf(f -> now - f.at() > FADE_TICKS);
    }

    private static Vector3f rgb(int color) {
        return new Vector3f((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f);
    }

    /** Broken shells, swelling and fading. */
    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || FADES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        for (Iterator<Fade> it = FADES.iterator(); it.hasNext(); ) {
            Fade f = it.next();
            float t = (mc.level.getGameTime() - f.at() + partial) / FADE_TICKS;
            if (t > 1f) continue;
            float alpha = 0.8f * (float) Math.pow(1f - t, 1.6);
            float r = f.radius() * (1f + 0.06f * t);
            pose.pushPose();
            pose.translate(f.centre().x - cam.x, f.centre().y - cam.y, f.centre().z - cam.z);
            RenderType type = RenderType.entityTranslucent(DominionRenderer.CRACKED);
            int lighter = DominionRenderer.brighter(f.color(), 0.25f);
            DominionRenderer.shell(buffers.getBuffer(type), pose.last(), r, r, lighter, lighter, alpha, 1f);
            buffers.endBatch(type);
            pose.popPose();
        }
    }

    /** Tests and logout: every fading shell forgotten. */
    static void clear() {
        FADES.clear();
    }
}
