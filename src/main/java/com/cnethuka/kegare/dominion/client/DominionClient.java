package com.cnethuka.kegare.dominion.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.dominion.DominionEntity;
import com.cnethuka.kegare.dominion.DominionNet;
import com.cnethuka.kegare.dominion.DominionRegistry;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dominions on a client (DESIGN §3.35): the Dominion Meter with the melee cues, which side of each barrier the local player is on (the
 * server says, as it closes), the barrier itself ({@link DominionRenderer}), the fog inside, and the moments: its name for those trapped
 * as it closes, and the sounds of it opening, taking hits, breaking and ending.
 */
public final class DominionClient {
    private static final ResourceLocation TITLE_LAYER = Kegare.id("dominion_title");
    private static final int BAR_WIDTH = 34;
    private static final int METER_COLOR = 0xFFD8D2E6;
    private static float meter;
    private static boolean shown;
    /** The name shown to those trapped as it closes, and when (game time). */
    @Nullable private static Component title;
    private static long titleAt;

    private DominionClient() {}

    public static void init(IEventBus modBus) {
        DominionNet.Meter.clientHandler = p -> {
            meter = p.meter();
            shown = p.shown();
        };
        DominionNet.Trapped.clientHandler = p -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getEntity(p.dominion()) instanceof DominionEntity d) d.setLocalTrapped(p.inside());
        };
        DominionNet.Moment.clientHandler = DominionClient::moment;
        StatusLayer.addExtra(DominionClient::renderMeter);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(DominionRegistry.DOMINION.get(), DominionRenderer::new));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, TITLE_LAYER, DominionClient::renderTitle));
        // The Incomplete Dominion's sweep would cross the middle of your own first-person view: there it's vanilla's hands.
        com.cnethuka.kegare.client.visual.PlayerAnimations.keepVanillaHands(com.cnethuka.kegare.dominion.DominionIds.FIRST_PERSON_VANILLA);
        NeoForge.EVENT_BUS.addListener(DominionClient::onFog);
        NeoForge.EVENT_BUS.addListener(DominionClient::onFogColor);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            meter = 0;
            shown = false;
            title = null;
        });
    }

    /** The barrier the camera is inside (risen enough to close it off), if any. */
    @Nullable
    public static DominionEntity around(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Vec3 at = camera.getPosition();
        for (DominionEntity d : DominionEntity.in(mc.level)) {
            if (d.contains(at) && d.risen(0f) >= 1f) return d;
        }
        return null;
    }

    private static void onFog(ViewportEvent.RenderFog event) {
        DominionEntity d = around(event.getCamera());
        if (d == null) return;
        float end = Math.min(event.getFarPlaneDistance(), d.look().fogEnd());
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), end * 0.15f));
        event.setFarPlaneDistance(end);
        event.setCanceled(true);
    }

    private static void onFogColor(ViewportEvent.ComputeFogColor event) {
        DominionEntity d = around(event.getCamera());
        if (d == null) return;
        int c = d.look().fogColor();
        event.setRed((c >> 16 & 0xFF) / 255f);
        event.setGreen((c >> 8 & 0xFF) / 255f);
        event.setBlue((c & 0xFF) / 255f);
    }

    private static void moment(DominionNet.Moment m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = new Vec3(m.x(), m.y(), m.z());
        switch (m.kind()) {
            case DominionNet.Moment.OPENING -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.6f, 0.5f, false);
            case DominionNet.Moment.CLOSED -> {
                mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.6f, 0.5f, false);
                Entity e = mc.level.getEntity(m.dominion());
                if (e instanceof DominionEntity d && d.localTrapped()) {
                    ResourceLocation ability = ResourceLocation.tryParse(m.ability());
                    if (ability != null) {
                        title = Component.translatable(Util.makeDescriptionId("ability", ability));
                        titleAt = mc.level.getGameTime();
                    }
                }
            }
            case DominionNet.Moment.HIT -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.2f, 0.6f, false);
            case DominionNet.Moment.BROKE -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f, false);
            case DominionNet.Moment.ENDED -> mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4f, 0.6f, false);
            default -> {}
        }
    }

    /** "Dominion 73" and a bar under the crosshair, with the melee cues: only while there's a Dominion to open with it. */
    private static int renderMeter(GuiGraphics g, Minecraft mc, int cx, int y) {
        if (!shown || meter <= 0.5f) return y;
        int whole = (int) Math.floor(meter);
        int color = whole >= 100 ? GuiDraw.GOLD : METER_COLOR;
        GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.dominion.meter", whole), cx, y, 1f, color);
        int top = y + 9;
        int fill = Mth.ceil(BAR_WIDTH * Math.min(1f, meter / 100f));
        g.fill(cx - BAR_WIDTH / 2 - 1, top, cx + BAR_WIDTH / 2 + 1, top + 3, 0xAA000000);
        g.fill(cx - BAR_WIDTH / 2, top + 1, cx - BAR_WIDTH / 2 + fill, top + 2, color);
        // The half-way mark: what an Incomplete Dominion takes.
        g.fill(cx, top, cx + 1, top + 3, 0xCC000000);
        return top + 5;
    }

    /** Its name, for those trapped as it closes: large over the middle of the screen, fading after a moment. */
    private static void renderTitle(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (title == null || mc.level == null || mc.options.hideGui) return;
        float age = mc.level.getGameTime() - titleAt + delta.getGameTimeDeltaPartialTick(false);
        if (age > 60) {
            title = null;
            return;
        }
        float alpha = age < 6 ? age / 6f : age > 40 ? 1f - (age - 40) / 20f : 1f;
        int a = Mth.clamp((int) (alpha * 255), 8, 255);
        GuiDraw.centered(g, mc.font, title, g.guiWidth() / 2, g.guiHeight() / 3, 2.5f, (a << 24) | 0xEDE8F2);
    }

    /** The local player's meter (0 to 100). */
    public static float meter() {
        return meter;
    }
}
