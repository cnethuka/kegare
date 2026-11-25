package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.dominion.DominionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A Dominion's barrier (DESIGN §3.35): a sphere round its centre, rising from the ground as it opens. From outside it's its shell color;
 * from inside, its sky. Contested in a clash, it's half there.
 */
public class DominionRenderer extends EntityRenderer<DominionEntity> {
    static final ResourceLocation WHITE = Kegare.id("textures/marks/white.png");
    private static final int SLICES = 48, STACKS = 24;

    public DominionRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(DominionEntity entity) {
        return WHITE;
    }

    @Override
    public void render(DominionEntity d, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float risen = d.risen(partialTick);
        if (risen <= 0f) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean inside = d.contains(cam);
        int rgb = inside ? d.look().skyColor() : d.look().shellColor();
        float alpha = inside ? 0.97f : 0.86f;
        if (d.state() == DominionEntity.State.CLASH) alpha *= 0.5f;
        float r = (float) d.radius();
        // Rising from the ground: only what's below the top of the risen part is there yet.
        float top = -r + r * (1f + risen);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
        PoseStack.Pose p = pose.last();
        int cr = rgb >> 16 & 0xFF, cg = rgb >> 8 & 0xFF, cb = rgb & 0xFF, ca = Mth.clamp((int) (alpha * 255), 0, 255);
        for (int i = 0; i < STACKS; i++) {
            float v0 = (float) i / STACKS, v1 = (float) (i + 1) / STACKS;
            float a0 = Mth.PI * (v0 - 0.5f), a1 = Mth.PI * (v1 - 0.5f);
            float y0 = Mth.sin(a0) * r, y1 = Mth.sin(a1) * r;
            if (y0 > top) break;
            y1 = Math.min(y1, top);
            float r0 = Mth.cos(a0) * r, r1 = (float) Math.sqrt(Math.max(0, r * r - y1 * y1));
            for (int j = 0; j < SLICES; j++) {
                float t0 = Mth.TWO_PI * j / SLICES, t1 = Mth.TWO_PI * (j + 1) / SLICES;
                float x00 = Mth.cos(t0) * r0, z00 = Mth.sin(t0) * r0, x01 = Mth.cos(t1) * r0, z01 = Mth.sin(t1) * r0;
                float x10 = Mth.cos(t0) * r1, z10 = Mth.sin(t0) * r1, x11 = Mth.cos(t1) * r1, z11 = Mth.sin(t1) * r1;
                vertex(out, p, x00, y0, z00, cr, cg, cb, ca);
                vertex(out, p, x01, y0, z01, cr, cg, cb, ca);
                vertex(out, p, x11, y1, z11, cr, cg, cb, ca);
                vertex(out, p, x10, y1, z10, cr, cg, cb, ca);
            }
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, float x, float y, float z, int r, int g, int b, int a) {
        float len = Mth.sqrt(x * x + y * y + z * z);
        float nx = len < 1.0e-4f ? 0 : x / len, ny = len < 1.0e-4f ? 1 : y / len, nz = len < 1.0e-4f ? 0 : z / len;
        out.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }
}
