package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.sixeyes.Edge;
import com.cnethuka.kegare.sixeyes.Strain;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Strain on the bearer's own screen (DESIGN §3.32): a small meter under the crosshair with the other status lines; at the top a throbbing
 * headache, a pulse at the edges of the screen and a heartbeat, with nothing in the middle of the view (the map, Reads and voids stay
 * sharp); a collapse's dark flash; and the uncovered edge's spark on whoever is about to cast. All private: nobody else is sent any of it.
 */
public final class StrainClient {
    static final ResourceLocation LAYER = Kegare.id("six_eyes_headache");
    private static final int BAR_W = 40;
    /** A heartbeat every this many ticks at the top. */
    private static final int BEAT_TICKS = 16;
    /** How far in from the edge the headache reaches, as a share of the shorter side: nothing near the middle. */
    private static final float EDGE_DEPTH = 0.14f;

    private static Strain.Hud hud = new Strain.Hud(-1, false, false, 0);
    private static int beatClock;
    private static long collapseUntil;
    /** Sparks showing: entity id to when they end (ms). */
    private static final Map<Integer, Long> SPARKS = new HashMap<>();
    static final long SPARK_MS = 350;

    private StrainClient() {}

    public static void init(IEventBus modBus) {
        Strain.Hud.clientHandler = p -> hud = p;
        Strain.Collapsed.clientHandler = p -> collapseUntil = SightClient.now() + p.ticks() * 50L;
        Edge.Spark.clientHandler = p -> {
            SPARKS.put(p.entityId(), SightClient.now() + SPARK_MS);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9f, 0.5f));
        };
        StatusLayer.addExtra(StrainClient::meter);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER, StrainClient::headache));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            hud = new Strain.Hud(-1, false, false, 0);
            SPARKS.clear();
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;
        if (hud.top() && ++beatClock >= BEAT_TICKS) {
            beatClock = 0;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.8f));
        }
        long now = SightClient.now();
        for (Iterator<Map.Entry<Integer, Long>> it = SPARKS.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() <= now) it.remove();
        }
    }

    /** Is a spark showing on this entity? (SightRender draws it.) */
    static Map<Integer, Long> sparks() {
        return SPARKS;
    }

    private static int meter(GuiGraphics g, Minecraft mc, int cx, int y) {
        if (hud.strain() < 0) return y;
        float fill = hud.strain() / 100f;
        int color = hud.top() ? 0xFFFF5A5A : hud.strain() >= 60 ? 0xFFFFB24A : 0xFF8FC8FF;
        g.fill(cx - BAR_W / 2 - 1, y, cx + BAR_W / 2 + 1, y + 3, 0xAA000000);
        g.fill(cx - BAR_W / 2, y + 1, cx - BAR_W / 2 + Mth.ceil(BAR_W * fill), y + 2, color);
        if (hud.top()) GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.six_eyes.strain_top"), cx, y + 4, 0.6f, 0xFFFF7A7A);
        return y + (hud.top() ? 10 : 5);
    }

    /** The headache: a red throb from the screen's edges, in time with the heart; a collapse's dark flash over everything. */
    private static void headache(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        long now = SightClient.now();
        if (collapseUntil > now) g.fill(0, 0, w, h, 0xB0000000);
        if (!hud.top()) return;
        // Quick swell on the beat, slower fall, as a heart goes.
        float t = (beatClock + delta.getGameTimeDeltaPartialTick(false)) / BEAT_TICKS;
        float throb = t < 0.2f ? t / 0.2f : 1f - (t - 0.2f) / 0.8f;
        int depth = (int) (Math.min(w, h) * EDGE_DEPTH);
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int a = (int) (110 * throb * k * k);
            if (a <= 0) continue;
            int col = a << 24 | 0x7A0A0A;
            int d0 = depth * i / steps, d1 = depth * (i + 1) / steps;
            g.fill(0, d0, w, d1, col);
            g.fill(0, h - d1, w, h - d0, col);
            g.fill(d0, d1, d1, h - d1, col);
            g.fill(w - d1, d1, w - d0, h - d1, col);
        }
    }
}
