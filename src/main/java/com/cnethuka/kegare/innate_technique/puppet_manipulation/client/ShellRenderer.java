package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetShellEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * A possessing player's empty body: their skin, armor and name, standing slack (head bowed, arms hanging) where they
 * left it. Full size.
 */
public class ShellRenderer extends EntityRenderer<PuppetShellEntity> {
    private final Body wide;
    private final Body slim;

    public ShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new Body(context, false);
        slim = new Body(context, true);
        shadowRadius = 0.5f;
    }

    @Override
    public void render(PuppetShellEntity shell, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        (PuppetSkins.skin(shell).model() == PlayerSkin.Model.SLIM ? slim : wide).render(shell, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PuppetShellEntity shell) {
        return PuppetSkins.skin(shell).texture();
    }

    /** The player model, slack: nobody is home. */
    static final class SlackModel extends PlayerModel<PuppetShellEntity> {
        SlackModel(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(PuppetShellEntity shell, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(shell, 0f, 0f, 0f, 0f, 0f);
            head.xRot = 0.55f; // bowed
            head.yRot = 0f;
            hat.copyFrom(head);
            rightArm.xRot = 0.05f;
            rightArm.zRot = 0.04f;
            leftArm.xRot = 0.05f;
            leftArm.zRot = -0.04f;
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }

    static final class Body extends LivingEntityRenderer<PuppetShellEntity, SlackModel> {
        Body(EntityRendererProvider.Context context, boolean slim) {
            super(context, new SlackModel(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5f);
            addLayer(new HumanoidArmorLayer<>(this,
                    new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidArmorModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
        }

        @Override
        public ResourceLocation getTextureLocation(PuppetShellEntity shell) {
            return PuppetSkins.skin(shell).texture();
        }
    }
}
