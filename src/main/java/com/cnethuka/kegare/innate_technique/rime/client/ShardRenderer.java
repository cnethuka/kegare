package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Frost Shard's and Splinter's thrown ice: a small solid crystal of the lance's ice (LanceMesh.SHARD, {@code models/fx/frost_shard.obj})
 * pointing the way it flies and turning about its length, with a short thin pale line behind it. Your own isn't drawn until it's clear
 * of your first-person camera (it leaves from between your eyes), as vanilla does with thrown items.
 * <p>
 * The line is drawn here, not by Photon: Photon's trail is a ribbon turned to face the camera, and seen straight down its length (by
 * whoever threw it, or whoever it's coming at) it becomes a sheet across the whole view. This one is a strip turned the same way
 * that narrows to nothing as it's seen end-on, as a thin line would. With vfxQuality VANILLA it's left to the snowflakes.
 */
public class ShardRenderer<T extends RimeProjectile> extends EntityRenderer<T> {
    /** Your own isn't drawn this close to your first-person camera. */
    static final double OWN_HIDDEN_WITHIN = 3.0;
    /** The line's texture: even along its length, soft across it (the lance's Photon trail uses it too). */
    static final ResourceLocation LINE = Kegare.id("textures/fx/glacial_lance_trail.png");
    /** The line's own colour, pale frost; it fades out toward its far end. */
    private static final int LINE_RGB = 0xD6EEFF;
    private static final int SEGMENTS = 6;
    /** How far short of the thrower's eyes the line stops: clear of their head. */
    private static final float CLEAR_OF_THROWER = 0.45f;
    private final String piece;
    private final float spin;
    /** How far behind its back the line reaches, and how wide it is at the shard. */
    final float lineLength, lineWidth;
    /** Where its back is, behind its middle. */
    private final float back;

    private ShardRenderer(EntityRendererProvider.Context context, String piece, float spin, float lineLength, float lineWidth, float back) {
        super(context);
        this.piece = piece;
        this.spin = spin;
        this.lineLength = lineLength;
        this.lineWidth = lineWidth;
        this.back = back;
        this.shadowRadius = 0f;
    }

    public static <T extends RimeProjectile> ShardRenderer<T> frostShard(EntityRendererProvider.Context context) {
        return new ShardRenderer<>(context, "frost_shard", 24f, 1.7f, 0.05f, 0.24f);
    }

    public static <T extends RimeProjectile> ShardRenderer<T> splinter(EntityRendererProvider.Context context) {
        return new ShardRenderer<>(context, "splinter", 30f, 1.0f, 0.032f, 0.16f);
    }

    @Override
    public boolean shouldRender(T shard, Frustum frustum, double camX, double camY, double camZ) {
        if (!shard.shouldRender(camX, camY, camZ)) return false;
        return frustum.isVisible(shard.getBoundingBox().inflate(0.5));
    }

    @Override
    public void render(T shard, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (hiddenFromOwnCamera(shard, partialTick)) return;
        pose.pushPose();
        pose.translate(0f, shard.getBbHeight() * 0.5f, 0f);
        GlacialLanceRenderer.orient(pose, Mth.lerp(partialTick, shard.yRotO, shard.getYRot()), Mth.lerp(partialTick, shard.xRotO, shard.getXRot()));
        pose.mulPose(Axis.ZP.rotationDegrees((shard.tickCount + partialTick) * spin + shard.getId() * 47f));
        LanceMesh.SHARD.draw(pose.last(), buffers, List.of(piece), new float[] {1f}, light);
        pose.popPose();
        if (KegareClientConfig.VFX_QUALITY.get() != KegareClientConfig.VfxQuality.VANILLA) line(shard, partialTick, pose, buffers);
        super.render(shard, yaw, partialTick, pose, buffers, light);
    }

    /** How long the line is: never back past where it was thrown from (it's drawn from the start, a whole move a tick). */
    static float lineLength(float full, float travelled) {
        return Mth.clamp(travelled - 0.3f, 0f, full);
    }

    /**
     * The same, also stopping short of the thrower's head ({@code fromThrower} blocks from the shard's middle to their eyes): the
     * tick count alone would let it reach back through the thrower's head.
     */
    static float lineLength(float full, float travelled, double fromThrower, float back) {
        return Mth.clamp(Math.min(travelled - 0.3f, (float) fromThrower - back - CLEAR_OF_THROWER), 0f, full);
    }

    /** How wide the line is drawn, seen at {@code sin} of the angle between it and the way you look along: nothing end-on. */
    static float seenWidth(float width, float sin) {
        return width * Mth.clamp(sin / 0.35f, 0f, 1f);
    }

    private void line(T shard, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Vec3 v = shard.getDeltaMovement();
        if (v.lengthSqr() < 1.0e-4) return;
        Vec3 dir = v.normalize();
        Vec3 at = shard.getPosition(partialTick).add(0, shard.getBbHeight() * 0.5, 0);
        float travelled = (float) ((shard.tickCount + partialTick) * v.length());
        Entity owner = shard.getOwner();
        float length = owner == null ? lineLength(lineLength, travelled)
                : lineLength(lineLength, travelled, at.distanceTo(owner.getEyePosition(partialTick)), back);
        if (length <= 0.05f) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (shard.getOwner() == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            // Your own: never in your face, it stops short of your camera.
            length = Math.min(length, (float) at.distanceTo(cam) - (float) OWN_HIDDEN_WITHIN);
            if (length <= 0.05f) return;
        }
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(LINE));
        Matrix4f m = pose.last().pose();
        float h = shard.getBbHeight() * 0.5f;
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = (float) i / SEGMENTS, t1 = (float) (i + 1) / SEGMENTS;
            float d0 = back + t0 * length, d1 = back + t1 * length;
            Vec3 p0 = dir.scale(-d0), p1 = dir.scale(-d1);
            // Turned to face the camera, narrowing as it's seen end-on.
            Vec3 toCam = cam.subtract(at.add(p0)).normalize();
            Vec3 across = dir.cross(toCam);
            float sin = (float) across.length();
            if (sin < 1.0e-4f) continue;
            float w0 = seenWidth(lineWidth, sin) * (1f - t0), w1 = seenWidth(lineWidth, sin) * (1f - t1);
            Vec3 side = across.scale(1.0 / sin);
            int a0 = Math.round(200 * (1f - t0) * (1f - t0)), a1 = Math.round(200 * (1f - t1) * (1f - t1));
            vertex(out, m, p0.add(side.scale(w0)), h, t0, 0f, a0);
            vertex(out, m, p0.subtract(side.scale(w0)), h, t0, 1f, a0);
            vertex(out, m, p1.subtract(side.scale(w1)), h, t1, 1f, a1);
            vertex(out, m, p1.add(side.scale(w1)), h, t1, 0f, a1);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, float h, float u, float v, int alpha) {
        Vector3f q = m.transformPosition(new Vector3f((float) p.x, (float) p.y + h, (float) p.z));
        out.addVertex(q.x, q.y, q.z).setColor((LINE_RGB >> 16) & 0xFF, (LINE_RGB >> 8) & 0xFF, LINE_RGB & 0xFF, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    private static boolean hiddenFromOwnCamera(RimeProjectile shard, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || shard.getOwner() != mc.player || !mc.options.getCameraType().isFirstPerson()) return false;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        return shard.getPosition(partialTick).distanceToSqr(camera) < OWN_HIDDEN_WITHIN * OWN_HIDDEN_WITHIN;
    }

    @Override
    public ResourceLocation getTextureLocation(T shard) {
        return LanceMesh.SHARD.texture;
    }
}
