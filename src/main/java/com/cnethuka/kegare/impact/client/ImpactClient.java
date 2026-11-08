package com.cnethuka.kegare.impact.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.impact.ImpactPayload;
import com.cnethuka.kegare.impact.ScreenEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Impact System on this client (DESIGN §3.23). A hit arrives as an {@link ImpactPayload} naming its attacker and its victim, and
 * this client plays its own part of it: the two in the hit get the freeze ({@link ScreenFx}), the shake ({@link ScreenShake}, the
 * victim's stronger) and, on the biggest hits, the impact frame; everyone else only the flash ({@link ImpactFlash}). How strong each
 * looks is this player's client config ({@code impact}), so it can be tuned while the game runs.
 */
public final class ImpactClient {
    public static final ResourceLocation IMPACT_FRAME = Kegare.id("impact_frame");

    private enum Role { ATTACKER, VICTIM, WITNESS }

    private ImpactClient() {}

    public static void init(IEventBus modBus) {
        ImpactPayload.setClientHandler(ImpactClient::onImpact);
        ScreenEffects.Payload.setClientHandler(p -> ScreenFx.play(p.effect(), p.ticks() * 50L, Math.min(300L, p.ticks() * 50L / 3)));
        NeoForge.EVENT_BUS.addListener(ScreenShake::onCameraAngles);
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> ImpactFlash.render(e));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> DeathBurst.render(e));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                ImpactFlash.tick(level);
                DeathBurst.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) m -> ScreenFx.reload()));
    }

    static void onImpact(ImpactPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        int me = mc.player.getId();
        Role role = me == p.attacker() ? Role.ATTACKER : me == p.victim() ? Role.VICTIM : Role.WITNESS;
        int t = p.tier().ordinal();
        DeathBurst.hit(mc.level, p);
        if (p.has(ImpactPayload.FLASH)) {
            ImpactFlash.spawn(mc.level, p, KegareClientConfig.FLASH_SIZE[t].get().floatValue(), role == Role.VICTIM);
        }
        if (role == Role.WITNESS) return;
        long stop = p.has(ImpactPayload.HIT_STOP) ? Math.round(KegareClientConfig.HIT_STOP_MILLIS[t].get() * KegareClientConfig.HIT_STOP_STRENGTH.get()) : 0;
        ScreenFx.hold(stop);
        if (p.has(ImpactPayload.SHAKE)) {
            double share = role == Role.ATTACKER ? KegareClientConfig.ATTACKER_SHAKE.get() : 1.0;
            float degrees = (float) (KegareClientConfig.SHAKE_DEGREES[t].get() * KegareClientConfig.SHAKE_STRENGTH.get() * share);
            ScreenShake.add(degrees, KegareClientConfig.SHAKE_MILLIS[t].get(), stop, p.direction(), role == Role.VICTIM ? 1f : -0.5f);
        }
        if (p.has(ImpactPayload.FRAME) && impactFrames()) {
            ScreenFx.play(IMPACT_FRAME, KegareClientConfig.IMPACT_FRAME_MILLIS.get(), 0);
        }
    }

    /** On in the settings, and only at Full: Reduced and Vanilla never flash the whole screen. */
    public static boolean impactFrames() {
        return KegareClientConfig.IMPACT_FRAMES.get() && KegareClientConfig.VFX_QUALITY.get() == VfxQuality.FULL;
    }

    static void clear() {
        ScreenFx.clear();
        ScreenShake.clear();
        ImpactFlash.clear();
        DeathBurst.clear();
    }
}
