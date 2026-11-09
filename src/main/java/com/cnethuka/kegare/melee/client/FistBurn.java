package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.fists.FistsPayload;
import com.cnethuka.kegare.melee.MeleeTell;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Reinforced Fists on a player (DESIGN §3.28): a low, steady burn on both fists in the Malice color the server sent this client
 * ({@link com.cnethuka.kegare.fists.FistsTell}), drawn at every effects setting. The ready Strike's fire, drawn on top of it on the
 * striking fist, is bigger, so the two can be told apart.
 */
public final class FistBurn {
    private static final Map<Integer, FistsPayload> SHOWN = new HashMap<>();
    /** Your own hand in first person: as for the ready fist, the fire has to stay small by the camera. */
    private static final float RADIUS = 3f / 16f;
    private static final float RADIUS_SLIM = 2.7f / 16f;

    private FistBurn() {}

    static void update(FistsPayload payload) {
        if (payload.flags() == 0) SHOWN.remove(payload.entityId());
        else SHOWN.put(payload.entityId(), payload);
    }

    static void clear() {
        SHOWN.clear();
    }

    /** What's on this player (null if nothing), as sent. */
    @Nullable
    static FistsPayload on(Player player) {
        FistsPayload shown = SHOWN.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        if (shown == null || mc.player == null || player.isInvisibleTo(mc.player)) return null;
        return shown;
    }

    /** The burn's color on this player, brightened for drawing, or null if Fists aren't on (or you can't see them). */
    @Nullable
    static Integer colorOn(Player player) {
        FistsPayload shown = on(player);
        return shown == null || !shown.has(FistsPayload.BURN) ? null : MeleeTell.bright(shown.color()) & 0xFFFFFF;
    }

    /** The burn on one arm, with the pose in the arm's space. {@code ownHand}: your own, by the camera. */
    static void drawArm(PoseStack pose, MultiBufferSource buffers, int rgb, float time, boolean slim, boolean leftArm, Vector3f toCamera, Vector3f up,
                        boolean ownHand) {
        float centre = FistShellMesh.armCentre(slim, leftArm);
        if (!ownHand) {
            FistFire.drawLow(pose.last(), buffers.getBuffer(FistFire.renderType()), rgb, time, FistShellMesh.handEnd(), centre, slim, toCamera, up);
        } else {
            FistFlames.drawOwnHand(pose.last(), buffers.getBuffer(FistFlames.renderType()), rgb, time * FistFire.LOW_PACE, FistShellMesh.handEnd(), centre,
                    slim ? RADIUS_SLIM : RADIUS, toCamera, up, leftArm, true);
        }
    }
}
