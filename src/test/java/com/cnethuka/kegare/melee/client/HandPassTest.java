package com.cnethuka.kegare.melee.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Where your own fist is reported from vanilla's first-person hand pass. A Severed player's steam goes there, so it has to be by the hand on
 * the screen whichever way they face, and not only facing north, where the camera's rotation happens to be none at all.
 */
class HandPassTest {
    /** Vanilla's camera, turned the way it turns itself. */
    private static final class Aimed extends Camera {
        Aimed(float yaw, float pitch) {
            setRotation(yaw, pitch, 0f);
        }
    }

    /** The pose vanilla's hand pass starts from: {@code GameRenderer.renderItemInHand} undoes the level's view rotation, which is the camera's own. */
    private static PoseStack handPass(Camera camera) {
        Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new Quaternionf()));
        PoseStack pose = new PoseStack();
        pose.mulPose(view.invert(new Matrix4f()));
        return pose;
    }

    @Test
    void aFistInTheHandPassIsWhereTheHandIsOnTheScreenWhicheverWayYouFace() {
        for (float yaw : new float[] {180f, 90f, 0f, -90f, 37f}) {
            for (float pitch : new float[] {-15f, 0f, 30f}) {
                Camera camera = new Aimed(yaw, pitch);
                PoseStack pose = handPass(camera);
                // Where vanilla puts a right hand's item, in the view: right of the middle, low, and in front.
                pose.translate(0.56f, -0.52f, -0.72f);
                Vector3f fist = FistTell.handPassPoint(pose.last(), 0f, 0f, 0f);
                Vector3f right = camera.getLeftVector().negate(new Vector3f());
                String at = "yaw " + yaw + ", pitch " + pitch + ": " + fist;
                assertTrue(fist.dot(camera.getLookVector()) > 0.6f, "in front of the camera, " + at);
                assertTrue(fist.dot(right) > 0.4f, "off to the right, " + at);
                assertTrue(fist.dot(camera.getUpVector()) < -0.4f, "and low, " + at);
                assertTrue(Math.abs(fist.length() - new Vector3f(0.56f, -0.52f, -0.72f).length()) < 1.0e-4f, "as far off as it is in the view, " + at);
            }
        }
    }
}
