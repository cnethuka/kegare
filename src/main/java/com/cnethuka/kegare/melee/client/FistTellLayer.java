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
 * The tell on a player's fists (a ready Strike on the attacking one, Reinforced Fists on both), drawn from the arm as the model has it,
 * so it follows every animation. It draws no items: it extends the item layer only because Player Animation Library's first-person pass
 * (your own arms during an animation) draws no layers on you but items and armor, and a ready fist has to show there too.
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
        boolean mainLeft = player.getMainArm() == HumanoidArm.LEFT;
        Integer zone = FistBurn.zoneOn(player);
        if (zone != null && !FistTell.firstPersonSelf(player)) { // the zone: a faint flicker of black sparks round both fists
            drawFlicker(pose, buffers, player, zone, getParentModel().leftArm, true, partialTick);
            drawFlicker(pose, buffers, player, zone, getParentModel().rightArm, false, partialTick);
        }
        // A ready Strike on the attacking hand; Reinforced Fists' fire, the very same, on both (§3.28).
        drawFist(pose, buffers, light, player, false, mainLeft, partialTick);
        drawFist(pose, buffers, light, player, true, !mainLeft, partialTick);
    }

    private void drawFist(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, boolean offHand, boolean left, float partialTick) {
        MeleeTell.Look look = FistTell.lookOn(player, offHand);
        if (look == null) return;
        ModelPart arm = left ? getParentModel().leftArm : getParentModel().rightArm;
        float time = player.tickCount + partialTick;
        pose.pushPose();
        arm.translateAndRotate(pose);
        Vector3f fist = pose.last().pose().transformPosition(FistShellMesh.armCentre(slim, left), FistShellMesh.fistY(), 0f, new Vector3f());
        if (FistTell.inWorld(player, fist, partialTick)) {
            boolean self = FistTell.firstPersonSelf(player);
            FistTell.drawArm(pose, buffers, look, time, slim, left, light, fist.negate(new Vector3f()).normalize(), new Vector3f(0f, 1f, 0f), self);
            FistTell.reportFist(player, offHand, fist);
            if (!self) FistTell.drawWisps(player, offHand, buffers, partialTick);
        } else {
            FistTell.drawArm(pose, buffers, look, time, slim, left, light, new Vector3f(0f, 0f, 1f), new Vector3f(0f, -1f, 0f), false);
        }
        pose.popPose();
    }

    private void drawFlicker(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, int color, ModelPart arm, boolean leftArm, float partialTick) {
        pose.pushPose();
        arm.translateAndRotate(pose);
        Vector3f fist = pose.last().pose().transformPosition(FistShellMesh.armCentre(slim, leftArm), FistShellMesh.fistY(), 0f, new Vector3f());
        if (FistTell.inWorld(player, fist, partialTick)) {
            FistBurn.drawFlicker(pose, buffers, color, player.tickCount, player.getId() * 2 + (leftArm ? 1 : 0), slim, leftArm, fist.negate(new Vector3f()).normalize());
        }
        pose.popPose();
    }
}
