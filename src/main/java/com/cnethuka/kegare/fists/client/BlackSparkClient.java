package com.cnethuka.kegare.fists.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.fists.BlackSparkPayload;
import com.cnethuka.kegare.melee.MeleeTell;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Black Spark's look (DESIGN §3.28): for one sharp moment, crackling black arcs and black sparks burst from the point of contact, every
 * one fringed in the attacker's Malice color (as this viewer is meant to see it), around a black star at the heart of it. The arcs are
 * redrawn crooked every tick, so they crackle; they shoot out on the first tick and break up as they go. Solid and full-bright, so the
 * black holds against the sky and the night alike. Original: no lightning of anyone else's.
 * <p>
 * Never on your own body in your own first person (it would fill your eyes: the Impact System gives you the hit). Smaller the closer it is
 * to the camera. Reduced: fewer arcs and sparks, sooner gone. Vanilla: black dust, dust in the color, and crit sparks.
 */
public final class BlackSparkClient {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(WHITE);
    private static final int BLACK = 0x050307;
    /** How big it is, in blocks: the arcs' reach. */
    private static final float SIZE = 1.0f;

    private record Burst(Vec3 at, Vec3 dir, int rgb, long born, int life, long seed, int arcs, int sparks, float size) {}

    private static final List<Burst> BURSTS = new ArrayList<>();

    private BlackSparkClient() {}

    public static void init() {
        BlackSparkPayload.setClientHandler(BlackSparkClient::spawn);
        NeoForge.EVENT_BUS.addListener(BlackSparkClient::render);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BURSTS.clear());
    }

    private static void spawn(BlackSparkPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        if (firstPerson && p.victimId() == mc.player.getId()) return;
        int rgb = MeleeTell.bright(p.color()) & 0xFFFFFF;
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        if (quality == VfxQuality.VANILLA) {
            vanilla(level, p.at(), p.direction(), rgb);
            return;
        }
        float size = SIZE;
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(p.at());
        if (firstPerson && d < 0.6) return;
        if (d < 2.5) size *= (float) Math.max(0.4, d / 2.5);
        boolean reduced = quality == VfxQuality.REDUCED;
        BURSTS.add(new Burst(p.at(), p.direction(), rgb, level.getGameTime(), reduced ? 4 : 6, level.getRandom().nextLong(), reduced ? 4 : 7,
                reduced ? 6 : 12, size));
    }

    private static void vanilla(ClientLevel level, Vec3 at, Vec3 dir, int rgb) {
        RandomSource r = level.getRandom();
        DustParticleOptions black = new DustParticleOptions(new Vector3f(0.02f, 0.02f, 0.03f), 1.2f);
        DustParticleOptions color = new DustParticleOptions(new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 0.9f);
        for (int i = 0; i < 16; i++) {
            Vec3 v = out(r, dir).scale(0.15 + 0.25 * r.nextDouble());
            level.addParticle(i % 3 == 2 ? color : black, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 6; i++) {
            Vec3 v = out(r, dir).scale(0.3);
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /** A way out from the hit: mostly onward and to the sides, never back at the one who struck. */
    private static Vec3 out(RandomSource r, Vec3 dir) {
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
        if (v.dot(dir) < -0.2) v = v.subtract(dir.scale(2 * v.dot(dir)));
        return v.add(dir.scale(0.35)).normalize();
    }

    private static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            BURSTS.clear();
            return;
        }
        long now = level.getGameTime();
        BURSTS.removeIf(b -> now - b.born > b.life + 1);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BURSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TYPE);
        Matrix4f m = event.getPoseStack().last().pose();
        Vec3 cam = camera.getPosition();
        for (Burst b : BURSTS) {
            float t = (now - b.born) / b.life;
            if (t < 0 || t >= 1) continue;
            draw(out, m, b, t, (long) Math.floor(now), cam);
        }
        buffers.endBatch(TYPE);
    }

    private static void draw(VertexConsumer out, Matrix4f m, Burst b, float t, long tick, Vec3 cam) {
        Vec3 c = b.at.subtract(cam);
        float s = b.size;
        // The heart: a black star, gone in the first third.
        if (t < 0.35f) {
            float star = s * 0.32f * (1f - t / 0.35f);
            RandomSource sr = RandomSource.create(b.seed ^ 0x5EED);
            for (int i = 0; i < 6; i++) {
                Vec3 d = out(sr, b.dir).scale(star * (0.7 + 0.6 * sr.nextDouble()));
                ribbon(out, m, c, c.add(d), s * 0.11f, s * 0.05f, b.rgb, cam, c);
            }
        }
        // The arcs: crooked lines out from the point of contact, redrawn every tick so they crackle, breaking up as they go.
        RandomSource shape = RandomSource.create(b.seed);
        float reach = Math.min(1f, t * 4f + 0.35f);
        for (int a = 0; a < b.arcs; a++) {
            Vec3 dir = out(shape, b.dir);
            float length = s * (0.55f + 0.6f * shape.nextFloat());
            RandomSource crackle = RandomSource.create(b.seed * 31 + a * 7919 + tick * 104729);
            int segments = 5;
            Vec3 side = dir.cross(new Vec3(0.3, 1, 0.2)).normalize(), side2 = dir.cross(side).normalize();
            Vec3 from = c;
            for (int k = 1; k <= segments; k++) {
                float along = length * reach * k / segments;
                float kink = length * 0.16f * (k == segments ? 0.4f : 1f);
                Vec3 to = c.add(dir.scale(along)).add(side.scale((crackle.nextFloat() - 0.5f) * 2 * kink)).add(side2.scale((crackle.nextFloat() - 0.5f) * 2 * kink));
                // Breaking up: the outer pieces go first as it fades.
                boolean shown = t < 0.45f || crackle.nextFloat() > (t - 0.45f) * 1.6f * (0.6f + 0.4f * k / segments);
                float thin = 1f - 0.55f * t;
                if (shown) ribbon(out, m, from, to, s * 0.075f * thin, s * 0.032f * thin, b.rgb, cam, c);
                from = to;
                // A fork now and then.
                if (k == 2 && crackle.nextFloat() < 0.5f) {
                    Vec3 fork = to.add(out(crackle, dir).scale(length * 0.3f * reach));
                    if (shown) ribbon(out, m, to, fork, s * 0.055f * thin, s * 0.024f * thin, b.rgb, cam, c);
                }
            }
        }
        // The sparks: black chips with a rim of color, thrown out and falling.
        RandomSource sparks = RandomSource.create(b.seed ^ 0xC0FFEE);
        float age = t * b.life;
        for (int i = 0; i < b.sparks; i++) {
            Vec3 v = out(sparks, b.dir).scale(s * (0.12 + 0.2 * sparks.nextDouble()));
            Vec3 p = c.add(v.scale(age)).add(0, -0.02 * age * age, 0);
            float size = s * (0.035f + 0.025f * sparks.nextFloat()) * (1f - 0.5f * t);
            chip(out, m, p, size, b.rgb, cam);
        }
    }

    /** A line segment as a flat strip facing the camera: a band of the color, and the black core on top of it, a hair nearer. */
    private static void ribbon(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, float fringe, float core, int rgb, Vec3 cam, Vec3 centre) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0e-8) return;
        Vec3 toCam = a.add(b).scale(-0.5);
        double dist = toCam.length();
        if (dist < 1.0e-4) return;
        Vec3 side = along.cross(toCam).normalize();
        Vec3 near = toCam.scale(0.012 / dist);
        strip(out, m, a, b, side.scale(fringe / 2), Vec3.ZERO, rgb);
        strip(out, m, a, b, side.scale(core / 2), near, BLACK);
    }

    private static void strip(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 half, Vec3 shift, int rgb) {
        vertex(out, m, a.add(half).add(shift), rgb);
        vertex(out, m, b.add(half).add(shift), rgb);
        vertex(out, m, b.subtract(half).add(shift), rgb);
        vertex(out, m, a.subtract(half).add(shift), rgb);
    }

    /** A spark: a black diamond on a slightly bigger one in the color, facing the camera. */
    private static void chip(VertexConsumer out, Matrix4f m, Vec3 p, float size, int rgb, Vec3 cam) {
        Vec3 toCam = p.scale(-1);
        double dist = toCam.length();
        if (dist < 1.0e-4) return;
        Vec3 n = toCam.scale(1 / dist);
        Vec3 right = n.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(n).normalize();
        diamond(out, m, p, right, up, size * 1.6f, rgb);
        diamond(out, m, p.add(n.scale(0.01)), right, up, size, BLACK);
    }

    private static void diamond(VertexConsumer out, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float r, int rgb) {
        vertex(out, m, c.add(up.scale(r)), rgb);
        vertex(out, m, c.add(right.scale(r)), rgb);
        vertex(out, m, c.subtract(up.scale(r)), rgb);
        vertex(out, m, c.subtract(right.scale(r)), rgb);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, int rgb) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255)
                .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}
