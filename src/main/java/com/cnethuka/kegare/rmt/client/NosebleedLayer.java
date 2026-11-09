package com.cnethuka.kegare.rmt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The nosebleed's streak (DESIGN §3.17), drawn on the player's own model at the skin's resolution with hard pixels, so it turns with the head
 * and follows any animation: both nostrils, then one thin line running down past the mouth to the chin and on down the chest. Thin on purpose:
 * a wide patch at the mouth reads as a blob, not as blood from the nose.
 */
final class NosebleedLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** On the head and chest, in skin pixels from each part's pivot: out past the hat (4.5) and the jacket (2.25), so any skin shows it. */
    private static final float FACE_OUT = 4.55f, CHEST_OUT = 2.3f;
    /** The nostrils' row on the head, counted down from its top (8 pixels tall, its pivot at the bottom). */
    private static final int NOSE_ROW = 5;
    private static final int MAIN_COLUMN = 0, SECOND_COLUMN = -1;
    private static final int FACE_ROWS = 3, CHEST_ROWS = 5;
    /** The streak's pixels, in the order they appear. */
    static final int LENGTH = FACE_ROWS + CHEST_ROWS;
    /** The pixel that reaches the chin: drops start falling. */
    static final int REACHES_CHIN = FACE_ROWS;
    private static final int BLOOD = 0xAE0A14, FRESH = 0xE0141E;
    private static final int MIN_BLOCK_LIGHT = 7;

    private NosebleedLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new NosebleedLayer(renderer));
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        int[] streak = NosebleedClient.streak(player.getId(), player.level().getGameTime());
        if (streak == null || streak[0] >= streak[1]) return;
        int from = streak[0], to = streak[1];
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        // A tell has to show: it never goes as dark as the night round it.
        light = LightTexture.pack(Math.max(LightTexture.block(light), MIN_BLOCK_LIGHT), LightTexture.sky(light));
        PlayerModel<AbstractClientPlayer> model = getParentModel();

        pose.pushPose();
        model.head.translateAndRotate(pose);
        PoseStack.Pose face = pose.last();
        for (int i = from; i < Math.min(to, FACE_ROWS); i++) {
            float y = NOSE_ROW + i - 8; // the head's top is at -8, its pivot at 0
            pixel(out, face, light, MAIN_COLUMN, y, -FACE_OUT, i == to - 1 ? FRESH : BLOOD);
            if (i == 0) pixel(out, face, light, SECOND_COLUMN, y, -FACE_OUT, i == to - 1 ? FRESH : BLOOD); // the other nostril
        }
        pose.popPose();

        if (to > FACE_ROWS) {
            pose.pushPose();
            model.body.translateAndRotate(pose);
            PoseStack.Pose chest = pose.last();
            for (int i = Math.max(from, FACE_ROWS); i < to; i++) pixel(out, chest, light, MAIN_COLUMN, i - FACE_ROWS, -CHEST_OUT, i == to - 1 ? FRESH : BLOOD);
            pose.popPose();
        }
    }

    /** One skin pixel, facing forward (the model faces -z): {@code column} and {@code y} its left and top in pixels from the part's pivot. */
    private static void pixel(VertexConsumer out, PoseStack.Pose p, int light, int column, float y, float z, int rgb) {
        float x0 = column / 16f, x1 = (column + 1) / 16f, y0 = y / 16f, y1 = (y + 1) / 16f, zz = z / 16f;
        vertex(out, p, light, x0, y1, zz, rgb);
        vertex(out, p, light, x1, y1, zz, rgb);
        vertex(out, p, light, x1, y0, zz, rgb);
        vertex(out, p, light, x0, y0, zz, rgb);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose p, int light, float x, float y, float z, int rgb) {
        out.addVertex(p, x, y, z).setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, 255).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 0f, -1f);
    }
}
