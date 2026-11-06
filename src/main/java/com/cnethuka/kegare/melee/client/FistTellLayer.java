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
 * The shell on a player's attacking arm, drawn from the arm as the model has it, so it follows every animation. It draws nothing of
 * an item layer: it only extends that class because Player Animation Library's first-person pass, which shows your own arms during an
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
        if (look == null) return;
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        ModelPart arm = left ? getParentModel().leftArm : getParentModel().rightArm;
        pose.pushPose();
        arm.translateAndRotate(pose);
        FistTell.drawShell(pose, buffers, look, player.tickCount + partialTick, slim, left, light);
        FistTell.reportFist(player, pose.last().pose().transformPosition(FistShellMesh.armCentre(slim, left), FistShellMesh.fistY(), 0f, new Vector3f()));
        pose.popPose();
    }
}
