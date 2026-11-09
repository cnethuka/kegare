package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Where a ready fist's fire goes on a player's arm, in the arm's own space (the model's arm cube, hanging down, hand at its end). The arm
 * geometry the fire is drawn round; it used to carry a Severed player's strained forearm too, which went with their Strike (DESIGN §3.27).
 */
final class FistShellMesh {
    /** A player arm's cube ends here in its own space: 10 pixels below the pivot. */
    private static final float HAND_END = 10f / 16f;

    private FistShellMesh() {}

    /** Where the arm's middle is in its own space: a right arm's cube is off-centre toward the body's outside. */
    static float armCentre(boolean slimArm, boolean leftArm) {
        float centre = (slimArm ? 0.5f : 1f) / 16f;
        return leftArm ? centre : -centre;
    }

    /** Where the arm's cube ends, in the arm's space. */
    static float handEnd() {
        return HAND_END;
    }

    /** The middle of the fist, just past the knuckles, in the arm's space. */
    static float fistY() {
        return HAND_END + 0.5f / 16f;
    }
}
