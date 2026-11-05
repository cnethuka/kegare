package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * Draws a puppet as its owner: their skin and arm model (wide or slim), held items and armor (Stand-In borrows them),
 * the Malice glow ({@link PuppetGlowLayer}) and, while it spins, the idle spin's body turn. The 1.21 scale attribute
 * makes it half size. No name tag (a Stand-In shows its owner's).
 */
public class PuppetRenderer extends EntityRenderer<PuppetEntity> {
    private final Body wide;
    private final Body slim;

    public PuppetRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new Body(context, false);
        slim = new Body(context, true);
        shadowRadius = 0.5f;
    }

    private Body body(PuppetEntity puppet) {
        return PuppetSkins.skin(puppet).model() == PlayerSkin.Model.SLIM ? slim : wide;
    }

    @Override
    public void render(PuppetEntity puppet, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (puppet.isPossessed()) return; // it's inside its possessor, who is drawn as it
        body(puppet).render(puppet, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PuppetEntity puppet) {
        return PuppetSkins.skin(puppet).texture();
    }

    /** One arm model's renderer (vanilla picks wide or slim per player the same way). */
    static final class Body extends LivingEntityRenderer<PuppetEntity, PuppetModel> {
        Body(EntityRendererProvider.Context context, boolean slim) {
            super(context, new PuppetModel(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5f);
            addLayer(new HumanoidArmorLayer<>(this,
                    new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
            addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
            addLayer(new PuppetGlowLayer(this, context, slim));
        }

        @Override
        public ResourceLocation getTextureLocation(PuppetEntity puppet) {
            return PuppetSkins.skin(puppet).texture();
        }

        @Override
        protected void setupRotations(PuppetEntity puppet, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
            super.setupRotations(puppet, pose, bob, yBodyRot, partialTick, scale);
            if (!puppet.isSpinning()) return;
            PuppetPoses.idleSpin().ifPresent(clip -> {
                Vector3f body = clip.rotation("body", puppet.spinSeconds(partialTick));
                if (body != null) pose.mulPose(Axis.YP.rotationDegrees(-body.y));
            });
        }

        @Override
        protected boolean shouldShowName(PuppetEntity puppet) {
            return puppet.hasCustomName() && puppet.isCustomNameVisible() && super.shouldShowName(puppet);
        }
    }
}
