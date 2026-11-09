package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innate_technique.swap.Disguises;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import com.cnethuka.kegare.innate_technique.rime.client.LanceMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Eyewear on the face, for everyone to see (DESIGN §3.32): a real 3D model (Python-built, {@code tools/blockbench/build_blindfold.py}), drawn
 * under the head's transform so it follows every pose, animations included. Worn by anyone, it means nothing on someone without the eyes.
 */
public final class EyewearRenderer implements ICurioRenderer {
    private final LanceMesh.Model model;
    private final String piece;
    private final RenderType renderType;

    EyewearRenderer(String name, Function<ResourceLocation, RenderType> renderType) {
        ResourceLocation texture = Kegare.id("textures/entity/" + name + ".png");
        this.model = new LanceMesh.Model(Kegare.id("models/fx/" + name + ".obj"), texture, texture);
        this.piece = name;
        this.renderType = renderType.apply(texture);
    }

    /** Resources reloaded: read the mesh again when it's next drawn. */
    void forget() {
        model.forget();
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack pose, RenderLayerParent<T, M> parent,
                                                                          MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                                                                          float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        LivingEntity wearer = slotContext.entity();
        // On a disguise everyone else sees the face's eyewear, never the wearer's own: DisguisedEyewearLayer draws that instead.
        if (wearer instanceof Player && Disguises.eyewearShown(Disguises.clientEntry(wearer.getUUID()), wearer.level().getGameTime(), ownView(wearer)) != null) return;
        if (parent.getModel() instanceof HumanoidModel<?> humanoid) draw(wearer, humanoid, pose, buffers, light);
    }

    /** Whether this client is the wearer, who always sees their own. */
    static boolean ownView(LivingEntity wearer) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(wearer.getUUID());
    }

    /** Draws this piece on a humanoid's head, following every pose. */
    void draw(LivingEntity wearer, HumanoidModel<?> humanoid, PoseStack pose, MultiBufferSource buffers, int light) {
        if (wearer.isInvisible() || wearer.isSpectator()) return;
        LanceMesh.Piece mesh = model.piece(piece);
        if (mesh == null) return;
        pose.pushPose();
        humanoid.head.translateAndRotate(pose);
        pose.mulPose(Axis.ZP.rotationDegrees(180f)); // into Blockbench's axes: up is up, the player's right +X
        VertexConsumer out = buffers.getBuffer(renderType);
        LanceMesh.emit(pose.last(), out, mesh, 1f, LanceMesh.light(light), false);
        pose.popPose();
    }
}
