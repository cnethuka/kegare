package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Six Eyes on the HUD (DESIGN §3.32), only for a bearer: an arrow round the crosshair pointing to each pin, with its name and how far
 * (never over the crosshair itself), and the optional minimap in the top-right corner (a client setting, off by default).
 */
public final class SightHud {
    static final ResourceLocation LAYER = Kegare.id("six_eyes_hud");
    static final ResourceLocation ARROW = Kegare.id("six_eyes/arrow");
    /** Pin arrows sit this far from the crosshair. */
    static final int ARROW_RADIUS = 38;
    static final int MINIMAP_RADIUS = 46;
    /** Blocks a pixel on the minimap. */
    static final double MINIMAP_SCALE = 2.0;

    private SightHud() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER, SightHud::render));
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!SightClient.isBearer() || mc.player == null || mc.options.hideGui || mc.screen instanceof SixEyesMapScreen) return;
        pins(g, mc);
        if (KegareClientConfig.SIX_EYES_MINIMAP.get()) minimap(g, mc);
    }

    private static void pins(GuiGraphics g, Minecraft mc) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        for (SightClient.Pin pin : SightClient.pins()) {
            Vec3 to = pin.pos().subtract(mc.player.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            float turn = Mth.wrapDegrees(yaw - mc.player.getYRot()); // 0: straight ahead
            double a = Math.toRadians(turn);
            float x = cx + (float) Math.sin(a) * ARROW_RADIUS, y = cy - (float) Math.cos(a) * ARROW_RADIUS;
            int rgb = pin.severed() ? 0x9090A0 : SightClient.bright(pin.color());
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(turn));
            GuiDraw.tinted(g, ARROW, -4, -4, 9, 9, rgb, 0.95f);
            g.pose().popPose();
            Component name = pin.name() != null ? (pin.severed() ? Component.translatable("gui.kegare.six_eyes.void") : Component.literal(pin.name()))
                    : BuiltInRegistries.ENTITY_TYPE.byId(pin.type()).getDescription();
            Component label = Component.translatable("gui.kegare.six_eyes.pin_label", name, SixEyesMapScreen.distance(to.length()));
            GuiDraw.centered(g, mc.font, label, Math.round(x), Math.round(y) + 6, 0.6f, 0xD0FFFFFF);
        }
    }

    /** North up, you in the middle, what the eyes see round you. */
    private static void minimap(GuiGraphics g, Minecraft mc) {
        int r = MINIMAP_RADIUS, cx = g.guiWidth() - r - 8, cy = r + 8;
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xB0030207);
        g.renderOutline(cx - r, cy - r, 2 * r, 2 * r, 0x602A4A70);
        Vec3 me = mc.player.position();
        long now = SightClient.now();
        for (SightClient.Mob m : SightClient.mobs()) {
            int x = Math.round(cx + (float) ((m.pos().x - me.x) / MINIMAP_SCALE)), y = Math.round(cy + (float) ((m.pos().z - me.z) / MINIMAP_SCALE));
            if (Math.abs(x - cx) > r - 2 || Math.abs(y - cy) > r - 2) continue;
            GuiDraw.tinted(g, SixEyesMapScreen.DOT, x - 1, y - 1, 2, 2, m.hostile() ? SightClient.MOB_HOSTILE : SightClient.MOB_PASSIVE, m.hostile() ? 0.8f : 0.35f);
        }
        for (SightClient.Seen s : SightClient.players()) {
            Vec3 at = s.position(now);
            int x = Math.round(cx + (float) ((at.x - me.x) / MINIMAP_SCALE)), y = Math.round(cy + (float) ((at.z - me.z) / MINIMAP_SCALE));
            x = Mth.clamp(x, cx - r + 3, cx + r - 3);
            y = Mth.clamp(y, cy - r + 3, cy + r - 3); // players off the map are held at its edge, on their side
            if (s.severed) GuiDraw.sprite(g, SixEyesMapScreen.VOID, x - 3, y - 3, 7, 7);
            else GuiDraw.tinted(g, SixEyesMapScreen.DOT, x - 2, y - 2, 5, 5, SightClient.bright(s.color), 1f);
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mc.player.getYRot() + 180f));
        GuiDraw.sprite(g, SixEyesMapScreen.SELF, -3, -3, 7, 7);
        g.pose().popPose();
    }
}
