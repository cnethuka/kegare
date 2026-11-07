package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.innate_technique.swap.SwapIds;
import com.cnethuka.kegare.innate_technique.swap.SwapLink;
import com.cnethuka.kegare.innate_technique.swap.SwapRegistry;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * Swap's client side: what it draws itself ({@link SwapFx}), effects drawn along a line (Exchange's travelling orbs) and the
 * disguises ({@link DisguiseClient}).
 */
public final class SwapClient {
    /** Blocks between two beads of a line. */
    private static final double SPACING = 0.9;
    private static final int MAX_BEADS = 40;

    /** A line still being drawn, bead by bead. */
    private static final class Travel {
        final Visual visual;
        final Vec3 from;
        final Vec3 to;
        final long startTick;
        final int travelTicks;
        final int beads;
        int drawn;

        Travel(Visual visual, Vec3 from, Vec3 to, long startTick, int travelTicks, int beads) {
            this.visual = visual;
            this.from = from;
            this.to = to;
            this.startTick = startTick;
            this.travelTicks = travelTicks;
            this.beads = beads;
        }
    }

    private static final List<Travel> TRAVELLING = new ArrayList<>();
    private static final DustParticleOptions TEAL_DUST = new DustParticleOptions(new Vector3f(0.08f, 0.72f, 0.65f), 1.0f);
    /** Small teal dust for a swap's two ends: vanilla dust lives longer the bigger it is, and these should be gone at once. */
    private static final DustParticleOptions TEAL_FLICK = new DustParticleOptions(new Vector3f(0.18f, 0.9f, 0.82f), 0.5f);

    private SwapClient() {}

    public static void init(IEventBus modBus) {
        SwapLink.Payload.setClientHandler(SwapClient::onLink);
        DisguiseClient.init();
        modBus.addListener(SwapClient::registerRenderers);
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(
                (ResourceManagerReloadListener) manager -> AnchorRenderer.TOKEN.forget()));
        modBus.addListener(MimicFlickerLayer::registerDefinitions);
        modBus.addListener(MimicFlickerLayer::addLayers);
        // Lookalikes used when an effect can't load, or with vfxQuality = VANILLA (teal dust; never the enderman's purple).
        VanillaFallback.register(SwapIds.FX_SWAP_DEPART, new VanillaFallback.Spec(TEAL_FLICK, 1, 12, 0.3, 0.02));
        VanillaFallback.register(SwapIds.FX_SWAP_ARRIVE, new VanillaFallback.Spec(TEAL_FLICK, 1, 8, 0.3, 0.02));
        VanillaFallback.register(SwapIds.FX_EXCHANGE_TRANSFER, new VanillaFallback.Spec(ParticleTypes.WITCH, 1, 3, 0.08, 0.0));
        VanillaFallback.register(SwapIds.FX_MOMENTUM_BURST, new VanillaFallback.Spec(TEAL_DUST, 1, 24, 0.4, 0.3));
        VanillaFallback.register(SwapIds.FX_TOTAL_SWAP_BURST, new VanillaFallback.Spec(ParticleTypes.REVERSE_PORTAL, 1, 40, 0.6, 0.3));
        NeoForge.EVENT_BUS.addListener(SwapClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SwapClient::onFrame);
        NeoForge.EVENT_BUS.addListener(SwapClient::onTickPre);
        NeoForge.EVENT_BUS.addListener(SwapClient::onLogout);
        NeoForge.EVENT_BUS.addListener(SwapClient::onRenderPlayer);
        NeoForge.EVENT_BUS.addListener(SwapClient::onRenderLevel);
        // Switchback's countdown ring is drawn here (SwapFx), not by Photon: it has to drain in step with the swap back.
        ClientVisuals.drawnByMove(SwapIds.FX_SWITCHBACK_TIMER);
        // So is the disguised player's own reminder (SwapHud's brackets), and the card flip as a disguise lands (SwapFx).
        ClientVisuals.drawnByMove(SwapIds.FX_AWARENESS_SHIMMER);
        ClientVisuals.drawnByMove(SwapIds.FX_MIMIC_FLICKER);
        SwapHud.init(modBus);
        ClientVisuals.onPlayAt(SwapIds.FX_AWARENESS_FLIP, (level, pos, entityId, visual) -> {
            SwapFx.flip(level, pos, entityId);
            return true;
        });
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void loopStarted(Entity entity, String key, Visual visual) {
                if (key.startsWith(SwapIds.SWITCHBACK_TIMER_KEY)) SwapFx.countdownStarted(entity, key);
            }

            @Override
            public void loopStopped(Entity entity, String key, float ticks, int growTicks) {
                if (key.startsWith(SwapIds.SWITCHBACK_TIMER_KEY)) SwapFx.countdownStopped(entity, key);
            }

            @Override
            public void playedOnce(Entity entity, Visual visual) {
                if (visual.effect().filter(SwapIds.FX_MIMIC_FLICKER::equals).isPresent()) SwapFx.mimicFlicker(entity);
            }
        });
        // Mimic claps by your own face: in your own first person that would only sweep your arms across the view, so it's vanilla's.
        PlayerAnimations.keepVanillaHands(List.of(SwapIds.ANIM_MIMIC_CAST));
        // Every other Swap animation is a clap in front of the chest: in your own first person, only while you look about level.
        PlayerAnimations.handsWhileLevel(List.of(SwapIds.ANIM_POSITIONS_CLAP, SwapIds.ANIM_SWAP_SNAP, SwapIds.ANIM_SWITCHBACK_CAST,
                SwapIds.ANIM_SWITCHBACK_SNAP,
                SwapIds.ANIM_AWARENESS_CAST, SwapIds.ANIM_ANCHOR_THROW, SwapIds.ANIM_ANCHOR_RECALL,
                SwapIds.ANIM_SEND_OFF_CAST, SwapIds.ANIM_EXCHANGE_CAST, SwapIds.ANIM_MOMENTUM_CAST));
        // Both ends of every swap: drawn here from the entity the server names, and Photon's glints on top (or the dust) a tick late.
        ClientVisuals.onPlayAt(SwapIds.FX_SWAP_DEPART, (level, pos, entityId, visual) -> {
            SwapFx.depart(level, pos, entityId);
            SwapFx.later(pos, visual);
            return true;
        });
        ClientVisuals.onPlayAt(SwapIds.FX_SWAP_ARRIVE, (level, pos, entityId, visual) -> {
            SwapFx.arrive(level, pos, entityId);
            SwapFx.later(pos, visual);
            return true;
        });
    }

    /**
     * Before the world ticks, so it never takes a step of the slide (which it reads as a stride, legs flung wide), and every frame,
     * since a move read on a frame between ticks would otherwise show for that frame.
     */
    private static void onTickPre(ClientTickEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) SwapFx.snap(level);
    }

    private static void onFrame(RenderFrameEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) SwapFx.snap(level);
    }

    private static void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) SwapFx.remember(player, event.getRenderer().getModel(), event.getPartialTick());
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        SwapFx.render(new PoseStack(), event.getCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The anchor's token: a small mesh (tools/blockbench/build_swap_anchor.py), its line and its ping.
        event.registerEntityRenderer(SwapRegistry.ANCHOR.get(), AnchorRenderer::new);
    }

    private static void onLink(SwapLink.Payload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (payload.effect().equals(SwapIds.FX_SWAP_LINK)) {
            SwapFx.link(level, payload.from(), payload.to()); // a crisp line, drawn here
            return;
        }
        Visual visual = Visual.effect(payload.effect(), Visual.Anchor.BODY);
        int beads = beads(payload.from(), payload.to());
        if (payload.travelTicks() <= 0) {
            for (int i = 0; i <= beads; i++) ClientVisuals.playAt(level, lerp(payload.from(), payload.to(), i / (double) beads), visual);
        } else {
            TRAVELLING.add(new Travel(visual, payload.from(), payload.to(), level.getGameTime(), payload.travelTicks(), beads));
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        SwapFx.tick(level);
        AnchorRenderer.tick(level);
        if (TRAVELLING.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Travel> it = TRAVELLING.iterator(); it.hasNext(); ) {
            Travel t = it.next();
            double progress = Math.min(1.0, (now - t.startTick + 1) / (double) t.travelTicks);
            int upTo = (int) Math.floor(progress * t.beads);
            while (t.drawn <= upTo) {
                ClientVisuals.playAt(level, lerp(t.from, t.to, t.drawn / (double) t.beads), t.visual);
                t.drawn++;
            }
            if (t.drawn > t.beads) it.remove();
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRAVELLING.clear();
        SwapFx.clear();
    }

    private static int beads(Vec3 from, Vec3 to) {
        return Math.max(1, Math.min(MAX_BEADS, (int) Math.ceil(from.distanceTo(to) / SPACING)));
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return from.add(to.subtract(from).scale(t));
    }
}
