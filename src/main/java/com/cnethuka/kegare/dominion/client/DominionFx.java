package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.dominion.DominionEntity;
import com.cnethuka.kegare.dominion.DominionNet;
import com.cnethuka.kegare.sound.KegareSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * Dominions' moments and air, on a client (DESIGN §3.35): its own sounds (rising and closing, breaking, clashing), the cracks a hit
 * leaves ({@link BarrierCracks}) and a spray of white sparks, dust drifting inside a barrier while you're in it, sparks off a clash's
 * seam, and a broken barrier's white shatter: its cracks race across the whole shell, then it bursts into white shards with a white
 * flash. All our own drawing and vanilla particles (no Photon), so the same at every effects setting; Reduced and Vanilla make about
 * half the shards and particles.
 */
public final class DominionFx {
    /** A broken barrier: where, its colors, when, its cracks as they were, and its shards. */
    private static final class Shatter {
        final Vec3 centre;
        final float radius;
        final int shell, sky, fog;
        final boolean seenInside;
        final long at;
        final List<BarrierCracks.Star> stars;
        final List<Shard> shards;

        Shatter(Vec3 centre, float radius, int shell, int sky, int fog, boolean seenInside, long at, List<BarrierCracks.Star> stars, List<Shard> shards) {
            this.centre = centre;
            this.radius = radius;
            this.shell = shell;
            this.sky = sky;
            this.fog = fog;
            this.seenInside = seenInside;
            this.at = at;
            this.stars = stars;
            this.shards = shards;
        }
    }

    /** A piece of the shell (relative to its centre): its corners, the middle it turns round, how it flies and spins. */
    record Shard(Vec3 a, Vec3 b, Vec3 c, Vec3 centroid, Vec3 velocity, Vec3 axis, float spin) {}

    /** The cracks race across the whole shell for this long before it bursts. */
    static final int CRACK_TICKS = 3;
    /** The shards fly this long. */
    static final int SHARD_TICKS = 30;
    /** The white flash, from the burst. */
    static final int FLASH_TICKS = 7;
    private static final List<Shatter> SHATTERS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Nothing spawned closer than this to a first-person camera (DESIGN §3.13). */
    private static final double NEAR = 2.0;
    private static final ParticleOptions WHITE_DUST = new DustParticleOptions(new Vector3f(1f, 1f, 1f), 1.0f);

    private DominionFx() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener(DominionFx::render);
    }

    static boolean full() {
        return KegareClientConfig.VFX_QUALITY.get() == KegareClientConfig.VfxQuality.FULL;
    }

    private static float share() {
        return full() ? 1f : 0.5f;
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
                if (level.getEntity(m.dominion()) instanceof DominionEntity d) BarrierCracks.hit(d, at, m.strength());
                sparks(level, at, Math.max(3, Math.round(8 * share())), 0.18);
            }
            case DominionNet.Moment.BROKE -> {
                level.playLocalSound(at.x, at.y, at.z, KegareSounds.DOMINION_BREAK.get(), SoundSource.PLAYERS, 3f, 1f, false);
                shatter(level, m, at);
            }
            case DominionNet.Moment.ENDED -> level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 0.6f, false);
            case DominionNet.Moment.CLASH -> {
                level.playLocalSound(at.x, at.y, at.z, KegareSounds.DOMINION_CLASH.get(), SoundSource.PLAYERS, 3f, 1f, false);
                sparks(level, at, Math.round(30 * share()), 0.25);
            }
            default -> {}
        }
    }

    /** White sparks off a point on a shell. */
    private static void sparks(ClientLevel level, Vec3 at, int count, double speed) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        for (int i = 0; i < count; i++) {
            Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() + 0.5, RANDOM.nextGaussian()).normalize().scale(speed);
            spawn(level, camera, i % 3 == 0 ? ParticleTypes.END_ROD : WHITE_DUST, at, v);
        }
    }

    private static void spawn(ClientLevel level, Camera camera, ParticleOptions type, Vec3 at, Vec3 v) {
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson() && at.distanceTo(camera.getPosition()) < NEAR) return;
        level.addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    // ---------------------------------------------------------------------------------------------- the white shatter

    private static void shatter(ClientLevel level, DominionNet.Moment m, Vec3 centre) {
        float r = m.radius();
        int sky = 0x8E8983, fog = 0x8E8983;
        List<BarrierCracks.Star> stars = new ArrayList<>();
        if (level.getEntity(m.dominion()) instanceof DominionEntity d) {
            sky = d.look().skyColor();
            fog = d.look().fogColor();
            stars.addAll(BarrierCracks.stars(d));
        }
        // Enough stars to crack it all over as it goes.
        RandomSource rng = RandomSource.create(Mth.floor(centre.x) * 341873128712L ^ Mth.floor(centre.z) * 132897987541L);
        while (stars.size() < 9) {
            double yaw = rng.nextDouble() * Mth.TWO_PI, up = 0.1 + rng.nextDouble() * 1.2;
            BarrierCracks.add(stars, new Vec3(Math.cos(yaw) * Math.cos(up), Math.sin(up), Math.sin(yaw) * Math.cos(up)), 0.2f);
        }
        boolean inside = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(centre) < r;
        SHATTERS.add(new Shatter(centre, r, m.shellColor(), sky, fog, inside, level.getGameTime(), stars, shards(r, rng, full())));
    }

    /**
     * The shell above the ground in pieces: a grid of it, its corners nudged so no two pieces are alike, each cell two triangles. Each
     * flies out from the middle and a little up, turning as it goes.
     */
    static List<Shard> shards(float r, RandomSource rng, boolean full) {
        int slices = full ? 36 : 24, stacks = full ? 9 : 6;
        Vec3[][] grid = new Vec3[stacks + 1][slices];
        for (int i = 0; i <= stacks; i++) {
            for (int j = 0; j < slices; j++) {
                double lat = (Math.PI / 2) * 0.94 * (i + (i > 0 && i < stacks ? (rng.nextDouble() - 0.5) * 0.6 : 0)) / stacks;
                double lon = Mth.TWO_PI * (j + (rng.nextDouble() - 0.5) * 0.6) / slices;
                grid[i][j] = new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon)).scale(r);
            }
        }
        List<Shard> out = new ArrayList<>();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                Vec3 p00 = grid[i][j], p01 = grid[i][(j + 1) % slices], p10 = grid[i + 1][j], p11 = grid[i + 1][(j + 1) % slices];
                out.add(shard(p00, p01, p11, rng));
                out.add(shard(p00, p11, p10, rng));
            }
        }
        return out;
    }

    private static Shard shard(Vec3 a, Vec3 b, Vec3 c, RandomSource rng) {
        Vec3 mid = a.add(b).add(c).scale(1 / 3.0);
        Vec3 out = mid.normalize().scale(0.12 + rng.nextDouble() * 0.22).add(0, 0.04 + rng.nextDouble() * 0.1, 0)
                .add(rng.nextGaussian() * 0.03, rng.nextGaussian() * 0.03, rng.nextGaussian() * 0.03);
        Vec3 axis = new Vec3(rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian());
        axis = axis.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : axis.normalize();
        return new Shard(a, b, c, mid, out, axis, (float) (0.08 + rng.nextDouble() * 0.3));
    }

    /** Where a shard's corner is {@code t} ticks after the burst: flown out, fallen a little, turned and shrunk round its middle. */
    static Vec3 corner(Shard s, Vec3 corner, float t) {
        Vec3 offset = s.velocity().scale(t).add(0, -0.016 * t * t, 0);
        Vec3 rel = corner.subtract(s.centroid()).scale(1 - 0.5 * Mth.clamp(t / SHARD_TICKS, 0f, 1f));
        double angle = s.spin() * t, cos = Math.cos(angle), sin = Math.sin(angle);
        Vec3 k = s.axis();
        Vec3 turned = rel.scale(cos).add(k.cross(rel).scale(sin)).add(k.scale(k.dot(rel) * (1 - cos)));
        return s.centroid().add(offset).add(turned);
    }

    // ---------------------------------------------------------------------------------------------- every tick

    /** Every client tick: cracks grow, dust drifts inside the barrier you're in, sparks fly off any clash's seam near you. */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        float share = share();
        for (DominionEntity d : DominionEntity.in(level)) {
            BarrierCracks.tick(d, damage(d));
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
                    spawn(level, camera, i % 2 == 0 ? ParticleTypes.END_ROD : WHITE_DUST, p, v);
                }
            }
        }
        long now = level.getGameTime();
        for (Shatter s : SHATTERS) {
            if (now - s.at == CRACK_TICKS) burst(level, camera, s);
        }
        SHATTERS.removeIf(s -> now - s.at > CRACK_TICKS + SHARD_TICKS);
    }

    /** How cracked a barrier is: the strength it's lost, and a quarter more for one that closes already cracked. */
    static float damage(DominionEntity d) {
        return Math.min(1f, 1f - d.barrier() + (d.look().cracked() ? 0.25f : 0f));
    }

    /** The burst: white motes and sparks thrown off the whole shell. */
    private static void burst(ClientLevel level, Camera camera, Shatter s) {
        int count = Math.round(180 * share());
        for (int i = 0; i < count; i++) {
            double yaw = RANDOM.nextDouble() * Mth.TWO_PI, up = Math.asin(RANDOM.nextDouble());
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(up), Math.sin(up), Math.sin(yaw) * Math.cos(up));
            Vec3 at = s.centre.add(dir.scale(s.radius));
            spawn(level, camera, i % 3 == 0 ? ParticleTypes.END_ROD : WHITE_DUST, at, dir.scale(0.1 + RANDOM.nextDouble() * 0.15));
        }
    }

    private static Vector3f rgb(int color) {
        return new Vector3f((color >> 16 & 0xFF) / 255f, (color >> 8 & 0xFF) / 255f, (color & 0xFF) / 255f);
    }

    // ---------------------------------------------------------------------------------------------- drawing

    /** Shattering barriers: their cracks racing over them, then the flash and the shards. */
    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SHATTERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        for (Shatter s : SHATTERS) {
            float t = mc.level.getGameTime() - s.at + partial;
            pose.pushPose();
            pose.translate(s.centre.x - cam.x, s.centre.y - cam.y, s.centre.z - cam.z);
            PoseStack.Pose p = pose.last();
            boolean inside = cam.distanceTo(s.centre) < s.radius;
            if (t < CRACK_TICKS) {
                // Still whole, its cracks racing across all of it.
                RenderType type = RenderType.entityTranslucent(DominionRenderer.WHITE);
                if (inside) DominionRenderer.shell(buffers.getBuffer(type), p, s.radius, s.radius, s.fog, s.sky, 0.97f);
                else DominionRenderer.shell(buffers.getBuffer(type), p, s.radius, s.radius, s.shell, s.shell, 1f);
                buffers.endBatch(type);
                float k = t / CRACK_TICKS;
                for (BarrierCracks.Star star : s.stars) star.shown = Mth.lerp(k * k, star.shown, BarrierCracks.MAX_REACH);
                RenderType glow = RenderType.entityTranslucentEmissive(DominionRenderer.WHITE);
                BarrierCracks.draw(buffers.getBuffer(glow), p, s.stars, s.radius, s.radius, inside, 1f);
                buffers.endBatch(glow);
            } else {
                float b = t - CRACK_TICKS;
                RenderType type = RenderType.entityTranslucentEmissive(DominionRenderer.WHITE);
                VertexConsumer out = buffers.getBuffer(type);
                if (b < FLASH_TICKS) {
                    float flash = 0.9f * (1f - b / FLASH_TICKS);
                    DominionRenderer.shell(out, p, s.radius * (1f + 0.035f * b), s.radius * 2, 0xFFFFFF, 0xFFFFFF, flash * flash);
                }
                int alpha = Math.round(255 * (1f - Mth.clamp((b - 10f) / (SHARD_TICKS - 10f), 0f, 1f)));
                Vec3 eye = cam.subtract(s.centre);
                boolean firstPerson = mc.options.getCameraType().isFirstPerson();
                if (alpha > 0) {
                    for (Shard shard : s.shards) {
                        Vec3 a = corner(shard, shard.a(), b), c1 = corner(shard, shard.b(), b), c2 = corner(shard, shard.c(), b);
                        if (firstPerson && a.add(c1).add(c2).scale(1 / 3.0).distanceTo(eye) < NEAR) continue; // never in front of your eyes
                        triangle(out, p, a, c1, c2, alpha);
                    }
                }
                buffers.endBatch(type);
            }
            pose.popPose();
        }
    }

    /** A white shard, both faces, lit in full (facing up, where the entity shader's lights are) so it stays white as it turns. */
    private static void triangle(VertexConsumer out, PoseStack.Pose p, Vec3 a, Vec3 b, Vec3 c, int alpha) {
        for (Vec3 v : new Vec3[] {a, b, c, c}) { // a quad with its last corner twice: a triangle
            out.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(255, 255, 255, alpha).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0f, 1f, 0f);
        }
    }

    /** Logout: every shatter forgotten. */
    static void clear() {
        SHATTERS.clear();
    }
}
