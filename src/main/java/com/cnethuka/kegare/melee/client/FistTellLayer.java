package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.melee.MeleeTell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

/**
 * The tell on a player's attacking arm, drawn from the arm as the model has it, so it follows every animation. It draws nothing of an
 * item layer: it only extends that class because Player Animation Library's first-person pass, which shows your own arms during an
 * animation, draws no other layers on you but items and armor, and a ready fist has to show there too.
 */
final class FistTellLayer extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final boolean slim;

    FistTellLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHand, boolean slim) {
        super(parent, itemInHand);
        this.slim = slim;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        MeleeTell.Look look = FistTell.lookOn(player);
        Integer burn = FistBurn.colorOn(player);
        if (look == null && burn == null) return;
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float time = player.tickCount + partialTick;
        if (burn != null) { // Reinforced Fists: a low burn on the other fist, and on this one when no Strike is ready
            drawBurn(pose, buffers, player, burn, time, !left ? getParentModel().leftArm : getParentModel().rightArm, !left, partialTick);
            if (look == null) {
                drawBurn(pose, buffers, player, burn, time, left ? getParentModel().leftArm : getParentModel().rightArm, left, partialTick);
                return;
            }
        }
        ModelPart arm = left ? getParentModel().leftArm : getParentModel().rightArm;
        pose.pushPose();
        arm.translateAndRotate(pose);
        Vector3f fist = pose.last().pose().transformPosition(FistShellMesh.armCentre(slim, left), FistShellMesh.fistY(), 0f, new Vector3f());
        if (FistTell.inWorld(player, fist, partialTick)) {
            boolean self = FistTell.firstPersonSelf(player);
            FistTell.drawArm(pose, buffers, look, time, slim, left, light, fist.negate(new Vector3f()).normalize(), new Vector3f(0f, 1f, 0f), self);
            FistTell.reportFist(player, fist);
            if (!self) FistTell.drawWisps(player, buffers, partialTick);
        } else {
            FistTell.drawArm(pose, buffers, look, time, slim, left, light, new Vector3f(0f, 0f, 1f), new Vector3f(0f, -1f, 0f), false);
        }
        pose.popPose();
    }

    private void drawBurn(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, int color, float time, ModelPart arm, boolean leftArm,
                          float partialTick) {
        pose.pushPose();
        arm.translateAndRotate(pose);
        Vector3f fist = pose.last().pose().transformPosition(FistShellMesh.armCentre(slim, leftArm), FistShellMesh.fistY(), 0f, new Vector3f());
        if (FistTell.inWorld(player, fist, partialTick)) {
            FistBurn.drawArm(pose, buffers, color, time, slim, leftArm, fist.negate(new Vector3f()).normalize(), new Vector3f(0f, 1f, 0f),
                    FistTell.firstPersonSelf(player));
        } else {
            FistBurn.drawArm(pose, buffers, color, time, slim, leftArm, new Vector3f(0f, 0f, 1f), new Vector3f(0f, -1f, 0f), false);
        }
        pose.popPose();
    }
}
