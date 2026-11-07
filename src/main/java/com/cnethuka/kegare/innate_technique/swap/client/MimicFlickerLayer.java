package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Mimic's flicker (DESIGN §3.19): the caster's body drawn again as a flat teal figure, its boxes a pixel bigger all round than
 * the skin's outer layer, so it shows over everything they wear. As a layer of the player's renderer, set up as their body is,
 * it's posed exactly as they are drawn, animation and all. How strongly, and when, comes from {@link SwapFx#flickerAlpha}.
 */
final class MimicFlickerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final ModelLayerLocation WIDE = new ModelLayerLocation(Kegare.id("mimic_flicker"), "main");
    static final ModelLayerLocation SLIM = new ModelLayerLocation(Kegare.id("mimic_flicker_slim"), "main");
    /** Out past the hat (0.5) and the jacket and sleeves (0.25): the teal sits clear of the skin rather than fighting it. */
    private static final float INFLATE = 0.85f;

    private final PlayerModel<AbstractClientPlayer> model;

    private MimicFlickerLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, PlayerModel<AbstractClientPlayer> model) {
        super(parent);
        this.model = model;
        // Its parts sit at their own origin: they're drawn under the body's parts' transforms.
        for (ModelPart part : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            part.setPos(0f, 0f, 0f);
            part.setRotation(0f, 0f, 0f);
        }
    }

    static void registerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WIDE, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(INFLATE), false), 64, 64));
        event.registerLayerDefinition(SLIM, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(INFLATE), true), 64, 64));
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                boolean slim = skin == PlayerSkin.Model.SLIM;
                renderer.addLayer(new MimicFlickerLayer(renderer, new PlayerModel<>(event.getEntityModels().bakeLayer(slim ? SLIM : WIDE), slim)));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float alpha = SwapFx.flickerAlpha(player.getId());
        if (alpha <= 0f || player.isInvisible()) return;
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        // Each teal part is drawn where the body's part is, under its transform as the player's animation left it (as the fist
        // flames are), not posed again: only the body's own model is posed by the animation.
        PlayerModel<AbstractClientPlayer> body = getParentModel();
        ModelPart[] from = {body.head, body.body, body.rightArm, body.leftArm, body.rightLeg, body.leftLeg};
        ModelPart[] to = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        VertexConsumer out = buffers.getBuffer(RenderType.itemEntityTranslucentCull(SwapFx.WHITE));
        int color = Math.round(alpha * 255) << 24 | SwapFx.GHOST_RGB;
        for (int i = 0; i < from.length; i++) {
            if (!from[i].visible) continue;
            pose.pushPose();
            from[i].translateAndRotate(pose);
            to[i].render(pose, out, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
            pose.popPose();
        }
    }
}
