package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.melee.MeleeIds;
import com.cnethuka.kegare.mixin.client.ItemInHandRendererInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * The guard in first person, where the melee animations leave the hands to vanilla: the same hand and item, brought in and turned
 * across the body, with a short shove when a parry lands. It stays out of the middle of the view, and vanilla's own lighting and
 * proportions apply to whatever's held.
 */
final class GuardHand {
    /** Where the hand turns about: roughly where vanilla draws it. */
    private static final float PIVOT_X = 0.56f, PIVOT_Y = -0.52f, PIVOT_Z = -0.72f;
    /** Picked by eye in game with a sword and an axe: the flat of the item towards you, because seen edge on it's all dark outline. */
    private static final float IN = -0.06f, UP = 0.05f, ROLL = 28f, YAW = 26f;
    private static final float EASE = 0.5f;
    private static final int SHOVE_TICKS = 5;

    private static float raised;
    private static float raisedBefore;
    private static float shove;
    private static float shoveBefore;
    private static int shoveLeft;
    private static boolean wasPerfect;

    private GuardHand() {}

    static void tick(LocalPlayer player) {
        boolean perfect = PlayerAnimations.isCurrent(player, MeleeIds.ANIM_PARRY_PERFECT);
        if (perfect && !wasPerfect) shoveLeft = SHOVE_TICKS;
        wasPerfect = perfect;
        boolean guarding = PlayerAnimations.isCurrent(player, MeleeIds.ANIM_PARRY_GUARD);
        raisedBefore = raised;
        raised += ((guarding || shoveLeft > 0 ? 1f : 0f) - raised) * EASE;
        if (raised < 0.002f) raised = 0f;
        shoveBefore = shove;
        shove = shoveLeft > 0 ? shoveLeft / (float) SHOVE_TICKS : 0f;
        if (shoveLeft > 0) shoveLeft--;
    }

    static void reset() {
        raised = raisedBefore = shove = shoveBefore = 0f;
        shoveLeft = 0;
        wasPerfect = false;
    }

    /** Moves whatever's drawn next to where the main hand is now. Nothing while it's down. */
    static void apply(PoseStack pose, float side, float partialTick) {
        if (!KegareClientConfig.FIRST_PERSON_ANIMATIONS.get()) return;
        float up = Mth.lerp(partialTick, raisedBefore, raised);
        if (up <= 0f) return;
        float kick = Mth.lerp(partialTick, shoveBefore, shove);
        pose.translate(side * IN * up, UP * up, -0.1f * kick);
        pose.translate(side * PIVOT_X, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.ZP.rotationDegrees(side * (ROLL * up + 10f * kick)));
        pose.mulPose(Axis.YP.rotationDegrees(side * YAW * up));
        pose.translate(side * -PIVOT_X, -PIVOT_Y, -PIVOT_Z);
    }

    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || Mth.lerp(event.getPartialTick(), raisedBefore, raised) <= 0f
                || !KegareClientConfig.FIRST_PERSON_ANIMATIONS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        apply(pose, player.getMainArm() == HumanoidArm.LEFT ? -1f : 1f, event.getPartialTick());
        ((ItemInHandRendererInvoker) mc.getEntityRenderDispatcher().getItemInHandRenderer()).kegare$renderArmWithItem(player, event.getPartialTick(),
                event.getInterpolatedPitch(), InteractionHand.MAIN_HAND, event.getSwingProgress(), event.getItemStack(), event.getEquipProgress(),
                pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
        event.setCanceled(true);
    }
}
