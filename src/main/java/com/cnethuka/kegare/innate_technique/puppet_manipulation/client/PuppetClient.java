package com.cnethuka.kegare.innate_technique.puppet_manipulation.client;

import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetIds;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetRegistry;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetTier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Puppet Manipulation's client side: the puppet renderer, its spin keyframes, outlines and its effects (PuppetFx). */
public final class PuppetClient {
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
        StringsClient.init(modBus);
        // All its effects are drawn by PuppetFx (thread, and lavender specks at Vanilla), not Photon.
        PuppetFx.init(modBus);
        // First-person hands (PuppetIds): the way back and Zip always stay vanilla; the rest only while looking roughly level.
        PlayerAnimations.keepVanillaHands(PuppetIds.FIRST_PERSON_VANILLA);
        PlayerAnimations.handsWhileLevel(PuppetIds.FIRST_PERSON_WHILE_LEVEL);
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
     * A full puppet's outline, in its glow colour, only while this client can see it: unlike vanilla glowing it never
     * shows through walls, so a hidden puppet isn't given away. A new puppet's outline appears as its weave ends (PuppetFx).
     */
    private static boolean outlined(PuppetEntity puppet) {
        if (puppet.tier() != PuppetTier.FULL || puppet.isInvisible() || puppet.isStandIn() || PuppetFx.forming(puppet)) return false;
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
