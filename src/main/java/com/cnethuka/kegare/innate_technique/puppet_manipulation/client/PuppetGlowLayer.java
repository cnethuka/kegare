package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The Malice a puppet was charged with: a slowly drifting shell of threads just outside its body (additive, like a
 * charged creeper's), in its owner's Malice color. Faint for a tap, normal for half, bright for full (a full puppet
 * also gets an outline, see {@code PuppetClient}).
 */
public class PuppetGlowLayer extends RenderLayer<PuppetEntity, PuppetModel> {
    public static final ModelLayerLocation WIDE = new ModelLayerLocation(Kegare.id("puppet_glow"), "main");
    public static final ModelLayerLocation SLIM = new ModelLayerLocation(Kegare.id("puppet_glow_slim"), "main");
    static final ResourceLocation TEXTURE = Kegare.id("textures/entity/puppet_glow.png");
    /** Glow strength per tier (tap, half, full). */
    private static final float[] INTENSITY = {0.22f, 0.45f, 0.85f};

    private final PuppetModel model;

    public PuppetGlowLayer(RenderLayerParent<PuppetEntity, PuppetModel> parent, EntityRendererProvider.Context context, boolean slim) {
        super(parent);
        model = new PuppetModel(context.bakeLayer(slim ? SLIM : WIDE), slim);
    }

    public static LayerDefinition definition(boolean slim) {
        return LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.3f), slim), 64, 64);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, PuppetEntity puppet, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (puppet.isInvisible() || puppet.isStandIn()) return; // a Stand-In looks exactly like its owner
        float t = puppet.tickCount + partialTick;
        model.prepareMobModel(puppet, limbSwing, limbSwingAmount, partialTick);
        getParentModel().copyPropertiesTo(model);
        model.setupAnim(puppet, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer consumer = buffers.getBuffer(RenderType.energySwirl(TEXTURE, (t * 0.004f) % 1f, (t * 0.007f) % 1f));
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, color(puppet.maliceColor(), puppet.tier().ordinal()));
    }

    /** The glow's ARGB for a Malice color at a tier (additive, so brightness is strength). */
    static int color(int rgb, int tier) {
        float k = INTENSITY[Math.max(0, Math.min(INTENSITY.length - 1, tier))];
        int r = Math.round(((rgb >> 16) & 0xFF) * k), g = Math.round(((rgb >> 8) & 0xFF) * k), b = Math.round((rgb & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
