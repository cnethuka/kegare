package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.client.ClientInput;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PossessionLinks;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Possession on every client: the possessing player is drawn as their puppet (no name tag, the puppet's glow), a thin
 * thread runs from their shell to them, and their own HUD shows "Possessing" and their body's health.
 */
public final class PossessionClient {
    /** Blocks between two beads of the thread, and the most beads drawn on one. */
    private static final double SPACING = 1.2;
    private static final int MAX_BEADS = 48;
    private static final int THREAD_EVERY_TICKS = 3;
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container");

    private static final Map<UUID, PossessionLinks.Entry> POSSESSING = new HashMap<>();
    private static PuppetHud.Payload hud = PuppetHud.Payload.NONE;

    private PossessionClient() {}

    static void init(IEventBus modBus) {
        PossessionLinks.StatePayload.setClientHandler(payload -> {
            POSSESSING.clear();
            payload.entries().forEach(e -> POSSESSING.put(e.player(), e));
        });
        PuppetHud.Payload.setClientHandler(payload -> hud = payload);
        ClientInput.addSpecialKeyNeeded(() -> hud.possessing()); // R is the way back, with Technique Mode on or off
        modBus.addListener(PossessionClient::addLayers);
        NeoForge.EVENT_BUS.addListener(PossessionClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PossessionClient::onNameTag);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            POSSESSING.clear();
            hud = PuppetHud.Payload.NONE;
        });
        StatusLayer.addExtra(PossessionClient::status);
        PuppetHudClient.init();
    }

    /** The owner's latest HUD numbers (puppet count, order, possession). */
    public static PuppetHud.Payload hud() {
        return hud;
    }

    @Nullable
    static PossessionLinks.Entry entry(UUID player) {
        return POSSESSING.get(player);
    }

    // ---------------------------------------------------------------- the thread

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || POSSESSING.isEmpty() || level.getGameTime() % THREAD_EVERY_TICKS != 0) return;
        Visual bead = Visual.effect(PuppetIds.FX_POSSESS_LINK, Visual.Anchor.BODY);
        for (PossessionLinks.Entry e : POSSESSING.values()) {
            Player player = level.getPlayerByUUID(e.player());
            Entity shell = e.shellId() < 0 ? null : level.getEntity(e.shellId());
            if (player == null || shell == null) continue;
            Vec3 from = shell.position().add(0, shell.getBbHeight() * 0.6, 0);
            Vec3 to = player.position().add(0, player.getBbHeight() * 0.6, 0);
            int beads = Math.max(1, Math.min(MAX_BEADS, (int) Math.ceil(from.distanceTo(to) / SPACING)));
            // A little sag in the middle, like a slack thread.
            double sag = Math.min(1.2, from.distanceTo(to) * 0.04);
            for (int i = 0; i <= beads; i++) {
                double t = i / (double) beads;
                Vec3 at = from.add(to.subtract(from).scale(t)).add(0, -sag * 4 * t * (1 - t), 0);
                ClientVisuals.playAt(level, at, bead);
            }
        }
    }

    // ---------------------------------------------------------------- looking like the puppet

    private static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player && POSSESSING.containsKey(player.getUUID())) event.setCanRender(TriState.FALSE);
    }

    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new GlowLayer(renderer, new PlayerModel<>(event.getEntityModels().bakeLayer(
                        model == PlayerSkin.Model.SLIM ? PuppetGlowLayer.SLIM : PuppetGlowLayer.WIDE), model == PlayerSkin.Model.SLIM)));
            }
        }
    }

    /** The puppet's glow on a player possessing it. */
    private static final class GlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final PlayerModel<AbstractClientPlayer> model;

        GlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, PlayerModel<AbstractClientPlayer> model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            PossessionLinks.Entry e = POSSESSING.get(player.getUUID());
            if (e == null || player.isInvisible()) return;
            float t = player.tickCount + partialTick;
            getParentModel().copyPropertiesTo(model);
            model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
            model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            var consumer = buffers.getBuffer(RenderType.energySwirl(PuppetGlowLayer.TEXTURE, (t * 0.004f) % 1f, (t * 0.007f) % 1f));
            model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, PuppetGlowLayer.color(e.color(), e.tier()));
        }
    }

    // ---------------------------------------------------------------- HUD

    /** "Possessing" and the body's health, under the crosshair, in or out of Technique Mode. */
    private static int status(GuiGraphics g, Minecraft mc, int cx, int y) {
        if (!hud.possessing()) return y;
        int lavender = 0xFF000000 | PuppetManipulation.MALICE_COLOR;
        GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.possessing"), cx, y, 1f, lavender);
        y += 11;
        String health = Mth.ceil(hud.shellHealth()) + " / " + Mth.ceil(hud.shellMaxHealth());
        Component label = Component.translatable("hud.kegare.shell_health", health);
        int w = 10 + mc.font.width(label);
        int x = cx - w / 2;
        g.blitSprite(HEART_EMPTY, x, y - 1, 9, 9);
        g.blitSprite(HEART, x, y - 1, 9, 9);
        g.drawString(mc.font, label, x + 11, y, 0xFFE8E2F0, true);
        return y + 12;
    }
}
