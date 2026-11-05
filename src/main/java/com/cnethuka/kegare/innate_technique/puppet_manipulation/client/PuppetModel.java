package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * The owner's player model, posed like a player, plus the idle spin's arm and head keyframes while spinning
 * ({@link PuppetPoses}; the whole-body turn is applied by the renderer).
 */
public class PuppetModel extends PlayerModel<PuppetEntity> {
    public PuppetModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(PuppetEntity puppet, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Like a player: crouching, and arms held out for whatever it holds (a Stand-In borrows its owner's items).
        crouching = puppet.isCrouching();
        rightArmPose = puppet.getMainHandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        leftArmPose = puppet.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
        super.setupAnim(puppet, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!puppet.isSpinning()) return;
        PuppetPoses.idleSpin().ifPresent(clip -> {
            float t = puppet.spinSeconds(ageInTicks - puppet.tickCount);
            apply(clip.rotation("right_arm", t), rightArm);
            apply(clip.rotation("left_arm", t), leftArm);
            apply(clip.rotation("head", t), head);
            apply(clip.rotation("head", t), hat);
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        });
    }

    /** Degrees on the model's own axes (see tools/gen_puppet_anims.py): x negative = arm forward, z positive = right arm out. */
    private static void apply(Vector3f degrees, ModelPart part) {
        if (degrees == null) return;
        part.xRot = (float) Math.toRadians(degrees.x);
        part.yRot = (float) Math.toRadians(degrees.y);
        part.zRot = (float) Math.toRadians(degrees.z);
    }
}
