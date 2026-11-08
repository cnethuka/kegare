package com.cnethuka.kegare.impact.client;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.impact.ImpactKind;
import com.cnethuka.kegare.impact.ImpactPayload;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The impact flash (DESIGN §3.23): sharp and brief at the point of contact, for everyone near. A hard white core crossed like a glint,
 * and spikes that shoot out and break away, all solid and full-bright, gone in a few ticks. White for a body or a weapon, the Malice
 * color for a technique (its core stays white). Never on your own body in your own first person (it would be in your eyes), and
 * smaller the closer it is to your camera. Reduced: fewer spikes, sooner gone. Vanilla: vanilla's crit sparks, or dust in the color.
 */
final class ImpactFlash {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final RenderType TYPE = RenderType.entityTranslucentEmissive(WHITE);

    private record Flash(Vec3 at, float size, int spikeRgb, float born, int life, long seed, int spikes) {}

    private static final List<Flash> FLASHES = new ArrayList<>();

    private ImpactFlash() {}

    static void spawn(ClientLevel level, ImpactPayload p, float size, boolean ownBody) {
        if (size <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        if (ownBody && firstPerson) return;
        int tier = p.tier().ordinal();
        int rgb = p.kind() == ImpactKind.TECHNIQUE && p.color() != 0 ? MeleeTell.bright(p.color()) & 0xFFFFFF : 0xFFFFFF;
        VfxQuality quality = KegareClientConfig.VFX_QUALITY.get();
        if (quality == VfxQuality.VANILLA) {
            vanilla(level, p, rgb, tier);
            return;
        }
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(p.contact());
        if (firstPerson && d < 0.6) return;
        if (d < 2.5) size *= (float) Math.max(0.35, d / 2.5);
        boolean reduced = quality == VfxQuality.REDUCED;
        int spikes = Math.round((6 + 2 * tier) * (reduced ? 0.6f : 1f));
        int life = 3 + tier - (reduced ? 1 : 0);
        FLASHES.add(new Flash(p.contact(), size, rgb, level.getGameTime(), Math.max(2, life), level.getRandom().nextLong(), spikes));
    }

    private static void vanilla(ClientLevel level, ImpactPayload p, int rgb, int tier) {
        int count = new int[] {3, 6, 10, 16, 24}[Math.min(4, tier)];
        RandomSource r = level.getRandom();
        Vec3 at = p.contact();
        for (int i = 0; i < count; i++) {
            double vx = r.nextGaussian() * 0.25, vy = r.nextGaussian() * 0.25 + 0.05, vz = r.nextGaussian() * 0.25;
            if (rgb == 0xFFFFFF) level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, vx, vy, vz);
            else level.addParticle(new DustParticleOptions(new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1f),
                    at.x, at.y, at.z, vx, vy, vz);
        }
    }

    static void tick(ClientLevel level) {
        long now = level.getGameTime();
        FLASHES.removeIf(f -> now - f.born > f.life + 1);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FLASHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float now = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TYPE);
        Matrix4f m = event.getPoseStack().last().pose();
        Vec3 cam = camera.getPosition(), left = new Vec3(camera.getLeftVector()), up = new Vec3(camera.getUpVector());
        for (Flash f : FLASHES) {
            float t = (now - f.born) / f.life;
            if (t < 0 || t >= 1) continue;
            draw(out, m, f, t, f.at.subtract(cam), left, up);
        }
        buffers.endBatch(TYPE);
    }

    private static void draw(VertexConsumer out, Matrix4f m, Flash f, float t, Vec3 c, Vec3 left, Vec3 up) {
        float s = f.size;
        int alpha = Math.round(255 * (t < 0.5f ? 1f : 1f - (t - 0.5f) / 0.5f));
        // The core: a glint, a long thin diamond each way and a small solid one, shrinking fast.
        float core = s * (1.1f - 0.9f * t);
        diamond(out, m, c, left, up, core, core * 0.12f, 0xFFFFFF, alpha);
        diamond(out, m, c, up, left, core * 0.8f, core * 0.1f, 0xFFFFFF, alpha);
        diamond(out, m, c, left, up, s * 0.2f * (1 - t), s * 0.2f * (1 - t), 0xFFFFFF, alpha);
        // The spikes: shoot out in the first tick, then break away outward and thin.
        RandomSource r = RandomSource.create(f.seed);
        float reach = Math.min(1f, t * 3f + 0.4f);
        for (int i = 0; i < f.spikes; i++) {
            float angle = r.nextFloat() * Mth.TWO_PI;
            float length = s * (0.7f + 0.6f * r.nextFloat());
            float inner = s * 0.15f + length * 0.75f * t;
            float outer = s * 0.15f + length * reach;
            if (outer <= inner) continue;
            float width = s * (0.08f + 0.04f * r.nextFloat()) * (1f - 0.6f * t);
            Vec3 dir = left.scale(Mth.cos(angle)).add(up.scale(Mth.sin(angle)));
            Vec3 side = left.scale(-Mth.sin(angle)).add(up.scale(Mth.cos(angle)));
            Vec3 a = c.add(dir.scale(inner)), b = c.add(dir.scale(outer));
            Vec3 mid = c.add(dir.scale(inner + (outer - inner) * 0.3f));
            // a sliver: thick near the inner end, coming to a point
            quad(out, m, a, mid.add(side.scale(width / 2)), b, mid.subtract(side.scale(width / 2)), f.spikeRgb, alpha);
        }
    }

    private static void diamond(VertexConsumer out, Matrix4f m, Vec3 c, Vec3 along, Vec3 across, float length, float width, int rgb, int alpha) {
        if (length <= 0) return;
        quad(out, m, c.add(along.scale(length)), c.add(across.scale(width)), c.subtract(along.scale(length)), c.subtract(across.scale(width)), rgb, alpha);
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
        vertex(out, m, a, rgb, alpha);
        vertex(out, m, b, rgb, alpha);
        vertex(out, m, c, rgb, alpha);
        vertex(out, m, d, rgb, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, int rgb, int alpha) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha)
                .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    static void clear() {
        FLASHES.clear();
    }
}
