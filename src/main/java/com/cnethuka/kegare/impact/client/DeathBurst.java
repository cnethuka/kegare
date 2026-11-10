package com.cnethuka.kegare.impact.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.impact.ImpactKind;
import com.cnethuka.kegare.impact.ImpactPayload;
import com.cnethuka.kegare.melee.MeleeTell;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Replaces vanilla's death smoke (the white "poof" a second after dying) for a mob a hit killed (DESIGN §3.23): solid slivers flung
 * from the body and a thin ring at its feet in the hit's colour (white for a body or weapon, the Malice colour for a technique),
 * full-bright and gone in under half a second. {@link #hit} remembers the Impact System's victims for a few seconds; any other death
 * (a fall, fire, drowning), and every death at vfxQuality VANILLA, keeps vanilla's smoke. Reduced draws half the slivers. Called from
 * {@code DeathPoofMixin}.
 */
public final class DeathBurst {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(WHITE);
    /** How long after a hit its victim's death still counts as the hit's (vanilla's smoke comes 20 ticks after death). */
    private static final int REMEMBER_TICKS = 60;
    private static final int LIFE = 9;

    private record Struck(long at, int rgb) {}

    private record Burst(Vec3 centre, Vec3 feet, float height, float width, int rgb, long born, long seed, int slivers) {}

    private static final Map<Integer, Struck> STRUCK = new HashMap<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private DeathBurst() {}

    /** A hit landed on this entity: if it dies of it, it goes out with a burst in the hit's colour. */
    static void hit(ClientLevel level, ImpactPayload p) {
        if (p.victim() < 0) return;
        int rgb = p.kind() == ImpactKind.TECHNIQUE && p.color() != 0 ? MeleeTell.bright(p.color()) & 0xFFFFFF : 0xFFFFFF;
        STRUCK.put(p.victim(), new Struck(level.getGameTime(), rgb));
    }

    /** Vanilla is about to puff its death smoke for {@code entity}: true if a burst goes instead. */
    public static boolean replace(LivingEntity entity) {
        if (KegareClientConfig.VFX_QUALITY.get() == VfxQuality.VANILLA || !(entity.level() instanceof ClientLevel level)) return false;
        Struck struck = STRUCK.remove(entity.getId());
        if (struck == null || level.getGameTime() - struck.at > REMEMBER_TICKS) return false;
        int slivers = KegareClientConfig.VFX_QUALITY.get() == VfxQuality.REDUCED ? 5 : 10;
        BURSTS.add(new Burst(entity.getBoundingBox().getCenter(), entity.position(), entity.getBbHeight(), entity.getBbWidth(), struck.rgb,
                level.getGameTime(), level.getRandom().nextLong(), slivers));
        return true;
    }

    static void tick(ClientLevel level) {
        long now = level.getGameTime();
        BURSTS.removeIf(b -> now - b.born > LIFE + 1);
        STRUCK.values().removeIf(s -> now - s.at > REMEMBER_TICKS);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BURSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition(), left = new Vec3(camera.getLeftVector()), up = new Vec3(camera.getUpVector());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TYPE);
        Matrix4f m = event.getPoseStack().last().pose();
        for (Burst b : BURSTS) {
            float t = (now - b.born) / LIFE;
            if (t < 0 || t >= 1) continue;
            draw(out, m, b, t, cam, left, up);
        }
        buffers.endBatch(TYPE);
    }

    private static void draw(VertexConsumer out, Matrix4f m, Burst b, float t, Vec3 cam, Vec3 left, Vec3 up) {
        int alpha = Math.round(255 * (t < 0.4f ? 1f : 1f - (t - 0.4f) / 0.6f));
        RandomSource r = RandomSource.create(b.seed);
        float size = Math.max(0.5f, b.height);
        // The slivers: flung out of the body every way, thin and solid, breaking off and shrinking as they go.
        for (int i = 0; i < b.slivers; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6 + 0.35, r.nextGaussian()).normalize();
            float reach = size * (0.45f + 0.35f * r.nextFloat());
            Vec3 tail = b.centre.add(dir.scale(reach * Math.min(1f, t * 2.2f) * 0.55f));
            Vec3 head = b.centre.add(dir.scale(reach * (0.35f + Math.min(1f, t * 2.2f))));
            float width = size * 0.045f * (1f - 0.7f * t);
            sliver(out, m, tail.subtract(cam), head.subtract(cam), width, b.rgb, alpha, left, up);
        }
        // The ring at its feet: thin, flat, running out a little past the body.
        float radius = (0.3f + 0.75f * Mth.sqrt(t)) * Math.max(0.6f, b.width + 0.4f);
        ring(out, m, b.feet.add(0, 0.03, 0).subtract(cam), radius, 0.035f, b.rgb, Math.round(alpha * 0.85f));
    }

    /** A thin quad from {@code a} to {@code b}, facing the camera, coming to a point at {@code b}. */
    private static void sliver(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, float width, int rgb, int alpha, Vec3 left, Vec3 up) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0e-6) return;
        Vec3 view = a.add(b).scale(0.5).normalize();
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0e-8) side = left;
        side = side.normalize().scale(width);
        vertex(out, m, a.add(side), rgb, alpha);
        vertex(out, m, a.subtract(side), rgb, alpha);
        vertex(out, m, b.subtract(side.scale(0.15)), rgb, alpha);
        vertex(out, m, b.add(side.scale(0.15)), rgb, alpha);
    }

    /** A flat ring on the ground: 24 quads between two circles. */
    private static void ring(VertexConsumer out, Matrix4f m, Vec3 c, float radius, float thickness, int rgb, int alpha) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 o0 = c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), o1 = c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            Vec3 i0 = c.add(Math.cos(a0) * (radius - thickness), 0, Math.sin(a0) * (radius - thickness));
            Vec3 i1 = c.add(Math.cos(a1) * (radius - thickness), 0, Math.sin(a1) * (radius - thickness));
            vertex(out, m, i0, rgb, alpha);
            vertex(out, m, o0, rgb, alpha);
            vertex(out, m, o1, rgb, alpha);
            vertex(out, m, i1, rgb, alpha);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, int rgb, int alpha) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha)
                .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    static void clear() {
        STRUCK.clear();
        BURSTS.clear();
    }
}
