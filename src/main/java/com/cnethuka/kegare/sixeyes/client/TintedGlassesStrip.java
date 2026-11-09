package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * The tinted glasses on the face (DESIGN §3.32): drawn round the head at eye level at the skin's own
 * resolution (our own pixel art, {@code tools/gen_art.py}: a strip of the head's four sides, 8 pixels each, front, its left, back, its right).
 * Worn by anyone, it means nothing on someone without the eyes.
 */
final class TintedGlassesStrip implements ICurioRenderer {
    /** Eye level on the head, in pixels from its pivot (the neck): the strip's top and bottom. */
    private static final float TOP = -5.5f, BOTTOM = -2.5f, OUT = 4.3f;
    private final ResourceLocation texture;

    TintedGlassesStrip(String name) {
        texture = Kegare.id("textures/entity/" + name + ".png");
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack pose, RenderLayerParent<T, M> parent,
                                                                          MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                                                                          float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> model) || slotContext.entity().isInvisible()) return;
        pose.pushPose();
        model.head.translateAndRotate(pose);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        PoseStack.Pose p = pose.last();
        float y0 = TOP / 16f, y1 = BOTTOM / 16f, o = OUT / 16f;
        face(out, p, light, -o, y0, -o, o, y1, -o, 0f, 0f, 0f, -1f); // the front (the model faces -z)
        face(out, p, light, o, y0, -o, o, y1, o, 0.25f, 1f, 0f, 0f); // its left
        face(out, p, light, o, y0, o, -o, y1, o, 0.5f, 0f, 0f, 1f); // the back
        face(out, p, light, -o, y0, o, -o, y1, -o, 0.75f, -1f, 0f, 0f); // its right
        pose.popPose();
    }

    /** One side of the head: from (x0, y0, z0) across to (x1, y1, z1), showing a quarter of the strip from {@code u}. */
    private static void face(VertexConsumer out, PoseStack.Pose p, int light, float x0, float y0, float z0, float x1, float y1, float z1, float u, float nx, float ny, float nz) {
        vertex(out, p, light, x0, y1, z0, u, 1f, nx, ny, nz);
        vertex(out, p, light, x1, y1, z1, u + 0.25f, 1f, nx, ny, nz);
        vertex(out, p, light, x1, y0, z1, u + 0.25f, 0f, nx, ny, nz);
        vertex(out, p, light, x0, y0, z0, u, 0f, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        out.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
