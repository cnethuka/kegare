package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetRegistry;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/** Puppet Manipulation's client side: the puppet renderer, its spin keyframes, outlines and vanilla stand-ins for its effects. */
public final class PuppetClient {
    public static final DustParticleOptions LAVENDER_DUST = new DustParticleOptions(new Vector3f(0.77f, 0.71f, 0.99f), 1.0f);

    /** Line-of-sight answers for full puppets' outlines, refreshed every few ticks: entity ID -> (game tick, visible). */
    private static final Map<Integer, long[]> SIGHT = new HashMap<>();

    private PuppetClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(PuppetClient::registerRenderers);
        modBus.addListener(PuppetClient::registerLayers);
        modBus.addListener(PuppetClient::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> SIGHT.clear());
        PuppetEntity.setOutlineHook(PuppetClient::outlined);
        PossessionClient.init(modBus);
        VanillaFallback.register(PuppetIds.FX_PUPPET_FORM, new VanillaFallback.Spec(LAVENDER_DUST, 1, 24, 0.35, 0.05));
        VanillaFallback.register(PuppetIds.FX_PUPPET_DISSOLVE, new VanillaFallback.Spec(LAVENDER_DUST, 1, 30, 0.35, 0.12));
        VanillaFallback.register(PuppetIds.FX_POSSESS_LINK, new VanillaFallback.Spec(LAVENDER_DUST, 1, 2, 0.04, 0.0));
        VanillaFallback.register(PuppetIds.FX_DIRECTIVE_PULSE, new VanillaFallback.Spec(LAVENDER_DUST, 1, 30, 1.2, 0.05));
        VanillaFallback.register(PuppetIds.FX_FOCUS_MARK, new VanillaFallback.Spec(ParticleTypes.CRIT, 1, 12, 0.4, 0.05));
        VanillaFallback.register(PuppetIds.FX_CURIO_BURST_EXPLOSION_TAP, new VanillaFallback.Spec(ParticleTypes.EXPLOSION, 1, 2, 0.4, 0.0));
        VanillaFallback.register(PuppetIds.FX_CURIO_BURST_EXPLOSION_HALF, new VanillaFallback.Spec(ParticleTypes.EXPLOSION, 1, 4, 0.7, 0.0));
        VanillaFallback.register(PuppetIds.FX_CURIO_BURST_EXPLOSION_FULL, new VanillaFallback.Spec(ParticleTypes.EXPLOSION, 1, 7, 1.1, 0.0));
        VanillaFallback.register(PuppetIds.FX_STAND_IN_GROW, new VanillaFallback.Spec(LAVENDER_DUST, 1, 30, 0.6, 0.08));
        VanillaFallback.register(PuppetIds.FX_UNDERSTUDY_SWAP, new VanillaFallback.Spec(ParticleTypes.PORTAL, 1, 24, 0.5, 0.5));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PuppetRegistry.PUPPET.get(), PuppetRenderer::new);
        event.registerEntityRenderer(PuppetRegistry.SHELL.get(), ShellRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PuppetGlowLayer.WIDE, () -> PuppetGlowLayer.definition(false));
        event.registerLayerDefinition(PuppetGlowLayer.SLIM, () -> PuppetGlowLayer.definition(true));
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PuppetPoses());
    }

    /**
     * A full puppet's outline, in its glow color, while this client can see it: unlike vanilla glowing it never shows
     * through walls, so it gives nobody's hiding puppet away.
     */
    private static boolean outlined(PuppetEntity puppet) {
        if (puppet.tier() != PuppetTier.FULL || puppet.isInvisible()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        long now = mc.level.getGameTime();
        long[] cached = SIGHT.get(puppet.getId());
        if (cached != null && now - cached[0] < 5) return cached[1] != 0;
        if (SIGHT.size() > 512) SIGHT.clear();
        boolean visible = mc.player.hasLineOfSight(puppet) && mc.player.distanceToSqr(puppet) < 48 * 48;
        SIGHT.put(puppet.getId(), new long[] {now, visible ? 1 : 0});
        return visible;
    }
}
