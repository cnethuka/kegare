package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.client.ClientInput;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PossessionLinks;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetHud;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
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
 * Possession on every client: the possessing player is drawn as their puppet (no name tag, the puppet's glow), a crisp
 * thread runs from their shell to them for as long as they're in it (drawn by PuppetFx), and their own HUD shows "Possessing" and their body's health.
 */
public final class PossessionClient {
    /** Blocks between two beads of the thread, and the most beads drawn on one. */
    /** Vanilla's thread: specks along it this often, this far apart (Full and Reduced draw it, PuppetFx). */
    private static final int SPECKS_EVERY_TICKS = 3;
    private static final double SPECK_SPACING = 0.8;
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_EMPTY = ResourceLocation.withDefaultNamespace("hud/heart/container");

    private static final Map<UUID, Link> POSSESSING = new HashMap<>();

    /** One possession as this client knows it, and the game time it first heard of it (the thread shoots out from then). */
    record Link(PossessionLinks.Entry entry, long since) {}

    /** Whether this client has had the list since it logged in (the first one, on logging in, is everyone already at it). */
    private static boolean heard;
    private static PuppetHud.Payload hud = PuppetHud.Payload.NONE;

    private PossessionClient() {}

    static void init(IEventBus modBus) {
        PossessionLinks.StatePayload.setClientHandler(payload -> {
            ClientLevel level = Minecraft.getInstance().level;
            long now = level == null ? 0 : level.getGameTime();
            Map<UUID, Link> was = new HashMap<>(POSSESSING);
            POSSESSING.clear();
            for (PossessionLinks.Entry e : payload.entries()) {
                Link old = was.get(e.player());
                // Someone already possessing when you log in has their thread there already; a new one shoots out.
                long since = old != null && old.entry().shellId() == e.shellId() ? old.since() : heard ? now : now - 100;
                POSSESSING.put(e.player(), new Link(e, since));
            }
            heard = true;
        });
        PuppetHud.Payload.setClientHandler(payload -> hud = payload);
        ClientInput.addSpecialKeyNeeded(() -> hud.possessing()); // R is the way back, with Technique Mode on or off
        modBus.addListener(PossessionClient::addLayers);
        NeoForge.EVENT_BUS.addListener(PossessionClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(PossessionClient::onNameTag);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            POSSESSING.clear();
            heard = false;
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
        Link link = POSSESSING.get(player);
        return link == null ? null : link.entry();
    }

    /** Everyone possessing right now, for the thread (PuppetFx draws it). */
    static Map<UUID, Link> links() {
        return POSSESSING;
    }

    // ---------------------------------------------------------------- the thread

    /** Vanilla quality: lavender specks along the thread (Full and Reduced draw it as a crisp thread, PuppetFx). */
    private static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || POSSESSING.isEmpty() || PuppetFx.drawn() || level.getGameTime() % SPECKS_EVERY_TICKS != 0) return;
        for (Link link : POSSESSING.values()) {
            PossessionLinks.Entry e = link.entry();
            Player player = level.getPlayerByUUID(e.player());
            Entity shell = e.shellId() < 0 ? null : level.getEntity(e.shellId());
            if (player == null || shell == null) continue;
            Vec3 from = shell.position().add(0, shell.getBbHeight() * 0.6, 0);
            Vec3 to = player.position().add(0, player.getBbHeight() * 0.6, 0);
            java.util.List<Vec3> curve = PuppetFx.possessionCurve(from, to, 1f);
            Vec3 last = null;
            for (Vec3 at : curve) {
                if (last != null && at.distanceTo(last) < SPECK_SPACING) continue;
                level.addParticle(PuppetFx.SPECK, at.x, at.y, at.z, 0, 0, 0);
                last = at;
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
            PossessionLinks.Entry e = entry(player.getUUID());
            if (e == null || player.isInvisible()) return;
            if (PlayerAnimations.firstPersonPass()) return; // your own arms in first person: its own head would be round the camera
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
