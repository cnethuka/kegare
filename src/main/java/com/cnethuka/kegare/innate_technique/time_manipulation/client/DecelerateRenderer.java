package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.innate_technique.time_manipulation.DecelerateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Decelerate's thrown clock hand (DESIGN §3.26): a smooth mesh, not pixels. A slim silver-white blade with a spade near its tip and a
 * fine point, a bronze pivot ring and a bronze counterweight on its tail, turning end over end in the plane it flies along, about
 * its balance point. Fully lit, so it reads by day and by night. It turns with its speed ({@link DecelerateEntity#spin}), so it crawls
 * through a Stasis Field and hangs in a Standstill.
 */
public final class DecelerateRenderer extends EntityRenderer<DecelerateEntity> {
    /** Half the blade's thickness: two faces this far apart, so edge-on it's still a line. */
    private static final float THICK = 0.006f;
    private static final int RING = 16;

    public DecelerateRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(DecelerateEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90f));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        // Local +X is now the way it flies; it turns about local Z, tip over forward.
        pose.mulPose(Axis.ZP.rotationDegrees(-Mth.lerp(partialTick, entity.spinO, entity.spin)));
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TimeFx.WHITE));
        Matrix4f m = pose.last().pose();
        for (float z : new float[] {-THICK, THICK}) draw(out, m, z);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void draw(VertexConsumer out, Matrix4f m, float z) {
        // The shaft, tapering toward the spade.
        quad(out, m, z, -0.2f, -0.016f, -0.2f, 0.016f, 0.32f, 0.01f, 0.32f, -0.01f, TimeFx.SILVER);
        // The spade and the point: a long diamond.
        quad(out, m, z, 0.29f, 0f, 0.37f, 0.052f, 0.54f, 0f, 0.37f, -0.052f, TimeFx.BRIGHT);
        // The pivot ring and the counterweight, bronze.
        annulus(out, m, z, 0f, 0.062f, 0.03f, TimeFx.BRONZE);
        annulus(out, m, z, -0.215f, 0.055f, 0f, TimeFx.BRONZE);
    }

    private static void annulus(VertexConsumer out, Matrix4f m, float z, float cx, float outer, float inner, int rgb) {
        for (int i = 0; i < RING; i++) {
            float a0 = Mth.TWO_PI * i / RING, a1 = Mth.TWO_PI * (i + 1) / RING;
            quad(out, m, z, cx + Mth.cos(a0) * inner, Mth.sin(a0) * inner, cx + Mth.cos(a0) * outer, Mth.sin(a0) * outer,
                    cx + Mth.cos(a1) * outer, Mth.sin(a1) * outer, cx + Mth.cos(a1) * inner, Mth.sin(a1) * inner, rgb);
        }
    }

    private static void quad(VertexConsumer out, Matrix4f m, float z, float x0, float y0, float x1, float y1, float x2, float y2, float x3,
                             float y3, int rgb) {
        vertex(out, m, x0, y0, z, rgb);
        vertex(out, m, x1, y1, z, rgb);
        vertex(out, m, x2, y2, z, rgb);
        vertex(out, m, x3, y3, z, rgb);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, float z, int rgb) {
        out.addVertex(m, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }

    @Override
    public ResourceLocation getTextureLocation(DecelerateEntity entity) {
        return TimeFx.WHITE;
    }
}
