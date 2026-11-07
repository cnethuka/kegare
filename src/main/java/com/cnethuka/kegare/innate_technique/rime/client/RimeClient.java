package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.client.ClientData;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.innate_technique.rime.GlacialSpireAbility;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innate_technique.rime.RimeRegistry;
import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * Rime's client side: projectile renderers, the vanilla-particle stand-ins for its Photon effects, and Glacial Spire's
 * aiming ring (only the caster sees it, while charging).
 */
public final class RimeClient {
    private static final DustParticleOptions AIM_DUST = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1f), 0.9f);
    private static final int AIM_POINTS = 18;

    private RimeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RimeClient::registerRenderers);
        modBus.addListener(RimeClient::registerReloadListeners);
        modBus.addListener(LanceHold::addLayers);
        NeoForge.EVENT_BUS.addListener(RimeClient::onClientTick);
        // Glacial Lance draws its own lance: in the hand, in flight, stuck where it lands, in pieces.
        LanceHold.init();
        LanceAftermath.init();
        for (var effect : List.of(RimeIds.FX_GLACIAL_LANCE_CHARGE, RimeIds.FX_GLACIAL_LANCE_CHARGE_SELF, RimeIds.FX_GLACIAL_LANCE_SNAP,
                RimeIds.FX_GLACIAL_LANCE_STICK, RimeIds.FX_GLACIAL_LANCE_SHATTER)) {
            VisualHooks.registerEffect(effect);
        }
        NeoForge.EVENT_BUS.addListener(LanceHold::onRenderHand);
        NeoForge.EVENT_BUS.addListener(LanceAftermath::render);
        NeoForge.EVENT_BUS.addListener(RimeClient::onLanceTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            LanceHold.clear();
            LanceAftermath.clear();
        });
        // The throw is the whole body: in your own first person, that would swing your own arm and whatever it holds across the view.
        PlayerAnimations.keepVanillaHands(List.of(RimeIds.ANIM_GLACIAL_LANCE_CHARGE, RimeIds.ANIM_GLACIAL_LANCE_RELEASE));
        // Lookalikes used when an effect can't load, or with vfxQuality = VANILLA.
        VanillaFallback.register(RimeIds.FX_FROST_SHARD_TRAIL, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 2, 6, 0.06, 0.01));
        VanillaFallback.register(RimeIds.FX_FROST_SHARD_IMPACT, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 14, 0.25, 0.12));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_CHARGE, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 2, 6, 0.25, 0.02));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_TRAIL, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 4, 8, 0.15, 0.02));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_IMPACT, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 28, 0.45, 0.2));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_STICK, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 12, 0.2, 0.08));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_SHATTER, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 16, 0.4, 0.1));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_SNAP, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 3, 0.08, 0.02));
        VanillaFallback.register(RimeIds.FX_FROST_FIELD_CONE, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 6, 10, 0.2, 0.45, true));
        VanillaFallback.register(RimeIds.FX_RIME_SHELL_AURA, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 6, 0.55, 0.0));
        // Small flakes, not ice-block item particles: those look huge and blocky right next to your own camera.
        VanillaFallback.register(RimeIds.FX_FREEZE_SHATTER, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 24, 0.45, 0.18));
        // Specials
        VanillaFallback.register(RimeIds.FX_SPLINTER_CAST, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 10, 0.2, 0.3, true));
        VanillaFallback.register(RimeIds.FX_SPLINTER_TRAIL, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 4, 0.04, 0.01));
        VanillaFallback.register(RimeIds.FX_GLACIAL_SPIRE_CHARGE, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 2, 6, 0.3, 0.02));
        VanillaFallback.register(RimeIds.FX_GLACIAL_SPIRE_WARNING, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 3, 6, 0.9, 0.0));
        VanillaFallback.register(RimeIds.FX_GLACIAL_SPIRE_SPIKE, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 3, 6, 0.4, 0.05));
        VanillaFallback.register(RimeIds.FX_GLACIAL_SPIRE_ERUPT, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 36, 0.8, 0.35));
        VanillaFallback.register(RimeIds.FX_ICE_PATH_TRAIL, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 3, 6, 0.35, 0.01));
        VanillaFallback.register(RimeIds.FX_SHATTER_SHELL_BURST, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 40, 0.6, 0.5));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The item sprite, full-bright so the ice reads at night.
        event.registerEntityRenderer(RimeRegistry.FROST_SHARD.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        event.registerEntityRenderer(RimeRegistry.GLACIAL_LANCE.get(), GlacialLanceRenderer::new);
        event.registerEntityRenderer(RimeRegistry.SPLINTER_SHARD.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.7f, true));
        // Glacial Spire is drawn entirely by its Photon loops.
        event.registerEntityRenderer(RimeRegistry.GLACIAL_SPIRE.get(), NoopRenderer::new);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> LanceMesh.forget());
    }

    private static void onLanceTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        LanceHold.tick(mc.level);
        LanceAftermath.tick(mc.level);
    }

    /** While you charge Glacial Spire, a ring of frost dust shows where it will rise (nobody else sees this). */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        SyncPayload data = ClientData.get();
        if (player == null || mc.level == null || data == null || mc.isPaused() || player.tickCount % 2 != 0) return;
        boolean aiming = data.casting().map(c -> c.ability().equals(Rime.GLACIAL_SPIRE.id())).orElse(false);
        if (!aiming) return;
        double radius;
        try {
            radius = RimeConfig.SPIRE_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return;
        }
        GlacialSpireAbility.target(player).ifPresent(at -> {
            double turn = player.tickCount * 0.08;
            for (int i = 0; i < AIM_POINTS; i++) {
                double angle = turn + Math.PI * 2 * i / AIM_POINTS;
                Vec3 p = at.add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
                mc.level.addParticle(AIM_DUST, p.x, p.y, p.z, 0, 0, 0);
            }
        });
    }
}
