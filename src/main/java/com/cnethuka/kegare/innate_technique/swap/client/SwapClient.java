package com.cnethuka.kegare.innate_technique.swap.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.swap.SwapLink;
import com.cnethuka.kegare.innate_technique.swap.SwapRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Swap's client side: effects drawn along a line (the swap link, Exchange's travelling orbs) and the disguises
 * ({@link DisguiseClient}).
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

    private SwapClient() {}

    public static void init(IEventBus modBus) {
        SwapLink.Payload.setClientHandler(SwapClient::onLink);
        DisguiseClient.init();
        modBus.addListener(SwapClient::registerRenderers);
        NeoForge.EVENT_BUS.addListener(SwapClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SwapClient::onLogout);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The anchor's item sprite, full-bright so it reads in the dark.
        event.registerEntityRenderer(SwapRegistry.ANCHOR.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.9f, true));
    }

    private static void onLink(SwapLink.Payload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
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
        if (level == null || TRAVELLING.isEmpty()) return;
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
    }

    private static int beads(Vec3 from, Vec3 to) {
        return Math.max(1, Math.min(MAX_BEADS, (int) Math.ceil(from.distanceTo(to) / SPACING)));
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return from.add(to.subtract(from).scale(t));
    }
}
