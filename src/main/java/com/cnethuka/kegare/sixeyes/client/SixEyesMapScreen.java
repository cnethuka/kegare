package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.client.gui.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Six Eyes' map (DESIGN §3.32, key M): a dark, top-down Malice radar of the bearer's whole dimension, not a terrain map. Players are
 * glowing dots in their Malice color, sized by Malice capacity; mobs small, dim dots (a faint trace when passive, clearer when hostile);
 * Severed players voids, dark holes in the flow, at the same range as everyone. North is up; the bearer is the arrow in the middle.
 * Scroll to zoom from close up to the whole dimension, drag to look round, right-click to come back. Hover a dot for name and distance
 * (the eyes identify people by their energy); click to pin it (a private HUD arrow points to it).
 */
public final class SixEyesMapScreen extends Screen {
    static final ResourceLocation DOT = Kegare.id("six_eyes/dot");
    static final ResourceLocation GLOW = Kegare.id("six_eyes/glow");
    static final ResourceLocation VOID = Kegare.id("six_eyes/void");
    static final ResourceLocation PIN = Kegare.id("six_eyes/pin");
    static final ResourceLocation SELF = Kegare.id("six_eyes/self");
    /** Blocks a pixel: from close up (16 pixels a block) out to the whole dimension. */
    static final double MIN_SCALE = 1 / 16.0;
    private static double scale = 0.25; // 4 pixels a block to start: a fight's players apart, not on top of one another
    private double panX, panZ;
    private boolean dragging;

    public SixEyesMapScreen() {
        super(Component.translatable("gui.kegare.six_eyes.map"));
    }

    /** The whole dimension fits at the most zoomed-out: its world border across the shorter side. */
    private double maxScale() {
        Minecraft mc = Minecraft.getInstance();
        double size = mc.level == null ? 6.0e7 : mc.level.getWorldBorder().getSize();
        return Math.max(MIN_SCALE * 2, size / Math.max(1, Math.min(width, height) - 40));
    }

    private Vec3 centre() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 me = mc.player == null ? Vec3.ZERO : mc.player.position();
        return new Vec3(me.x + panX, 0, me.z + panZ);
    }

    private float sx(double x) {
        return (float) (width / 2.0 + (x - centre().x) / scale);
    }

    private float sy(double z) {
        return (float) (height / 2.0 + (z - centre().z) / scale);
    }

    /** One dot on the map: a player, a void or a mob, and where it is on screen. */
    private record Dot(@Nullable SightClient.Seen player, @Nullable SightClient.Mob mob, float x, float y, Vec3 world) {}

    private List<Dot> dots() {
        List<Dot> out = new ArrayList<>();
        long now = SightClient.now();
        for (SightClient.Mob m : SightClient.mobs()) out.add(new Dot(null, m, sx(m.pos().x), sy(m.pos().z), m.pos()));
        for (SightClient.Seen s : SightClient.players()) {
            Vec3 at = s.position(now);
            out.add(new Dot(s, null, sx(at.x), sy(at.z), at));
        }
        return out;
    }

    /** No blur and no world behind: the radar is all there is. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xFF040309);
        super.render(g, mouseX, mouseY, partial);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        rings(g);
        List<Dot> dots = dots();
        Dot hovered = nearest(dots, mouseX, mouseY);
        // Mobs under players: a faint trace, clearer when hostile.
        for (Dot d : dots) {
            if (d.mob() == null || off(d)) continue;
            boolean hostile = d.mob().hostile();
            int size = hostile ? 4 : 3;
            GuiDraw.tinted(g, DOT, Math.round(d.x()) - size / 2, Math.round(d.y()) - size / 2, size, size,
                    hostile ? SightClient.MOB_HOSTILE : SightClient.MOB_PASSIVE, hostile ? 0.8f : 0.35f);
            if (SightClient.pinned(d.mob())) GuiDraw.tinted(g, PIN, Math.round(d.x()) - 6, Math.round(d.y()) - 6, 13, 13, 0xFFFFFF, 0.9f);
        }
        for (Dot d : dots) {
            if (d.player() == null || off(d)) continue;
            SightClient.Seen s = d.player();
            int x = Math.round(d.x()), y = Math.round(d.y());
            if (s.severed) {
                GuiDraw.sprite(g, VOID, x - 5, y - 5, 11, 11); // a dark hole in the flow
            } else {
                int size = size(s.capacity), glow = size * 3;
                int rgb = SightClient.bright(s.color);
                float pulse = SightClient.pulsing(s.entityId) ? 1f : 0.45f;
                GuiDraw.tinted(g, GLOW, x - glow / 2, y - glow / 2, glow, glow, rgb, pulse);
                GuiDraw.tinted(g, DOT, x - size / 2, y - size / 2, size, size, rgb, 1f);
            }
            if (SightClient.pinned(s)) GuiDraw.tinted(g, PIN, x - 8, y - 8, 17, 17, 0xFFFFFF, 0.95f);
        }
        // You, in the middle of it (or where you are, once you've looked away), pointing the way you face.
        float mx = sx(mc.player.getX()), my = sy(mc.player.getZ());
        g.pose().pushPose();
        g.pose().translate(mx, my, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mc.player.getYRot() + 180f));
        GuiDraw.sprite(g, SELF, -4, -4, 9, 9);
        g.pose().popPose();

        GuiDraw.centered(g, font, Component.translatable("gui.kegare.six_eyes.map"), width / 2, 8, 1f, 0xFFB9D8FF);
        GuiDraw.centered(g, font, Component.translatable("gui.kegare.six_eyes.map.hint"), width / 2, height - 14, 0.75f, 0xFF6B7890);
        if (hovered != null) tooltip(g, hovered, mouseX, mouseY);
    }

    private boolean off(Dot d) {
        return d.x() < -10 || d.y() < -10 || d.x() > width + 10 || d.y() > height + 10;
    }

    /** A dot's size by Malice capacity: bigger reserves glow bigger. */
    static int size(int capacity) {
        return Mth.clamp((int) Math.round(3 + 2.2 * Math.log10(Math.max(100, capacity) / 100.0)), 3, 9);
    }

    /** Range rings round you, a round number of blocks apart, labelled. */
    private void rings(GuiGraphics g) {
        double step = niceStep(scale * 90);
        Minecraft mc = Minecraft.getInstance();
        float cx = sx(mc.player.getX()), cy = sy(mc.player.getZ());
        for (int k = 1; k <= 4; k++) {
            float r = (float) (step * k / scale);
            int points = Math.max(24, (int) (r * 2 * Math.PI / 5));
            for (int i = 0; i < points; i++) {
                double a = i * 2 * Math.PI / points;
                int x = Math.round(cx + (float) Math.cos(a) * r), y = Math.round(cy + (float) Math.sin(a) * r);
                g.fill(x, y, x + 1, y + 1, 0x302A4A70);
            }
            GuiDraw.text(g, font, Component.literal(distance(step * k)), Math.round(cx + r) + 2, Math.round(cy) - 4, 0.6f, 0x605A7890);
        }
    }

    static double niceStep(double blocks) {
        double p = Math.pow(10, Math.floor(Math.log10(Math.max(1.0e-3, blocks))));
        double f = blocks / p;
        return (f < 1.5 ? 1 : f < 3.5 ? 2 : f < 7.5 ? 5 : 10) * p;
    }

    static String distance(double blocks) {
        return blocks >= 10_000 ? String.format(java.util.Locale.ROOT, "%.1f km", blocks / 1000) : Math.round(blocks) + " m";
    }

    @Nullable
    private Dot nearest(List<Dot> dots, double mx, double my) {
        Dot best = null;
        double bestD = 7 * 7;
        for (Dot d : dots) {
            double dd = (d.x() - mx) * (d.x() - mx) + (d.y() - my) * (d.y() - my);
            // a player wins a tie with a mob under it
            if (dd < bestD || (best != null && best.player() == null && d.player() != null && dd <= bestD + 4)) {
                best = d;
                bestD = dd;
            }
        }
        return best;
    }

    private void tooltip(GuiGraphics g, Dot d, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        if (d.player() != null) {
            lines.add(d.player().severed ? Component.translatable("gui.kegare.six_eyes.void") : Component.literal(d.player().name));
        } else {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(d.mob().type());
            lines.add(type.getDescription());
        }
        lines.add(Component.literal(distance(d.world().distanceTo(mc.player.position()))).withStyle(net.minecraft.ChatFormatting.GRAY));
        boolean pinned = d.player() != null ? SightClient.pinned(d.player()) : SightClient.pinned(d.mob());
        lines.add(Component.translatable(pinned ? "gui.kegare.six_eyes.unpin" : "gui.kegare.six_eyes.pin").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Zoom round the mouse: what's under it stays under it.
        Vec3 c = centre();
        double wx = c.x + (mouseX - width / 2.0) * scale, wz = c.z + (mouseY - height / 2.0) * scale;
        scale = Mth.clamp(scale * Math.pow(1.25, -scrollY), MIN_SCALE, maxScale());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            panX = wx - (mouseX - width / 2.0) * scale - mc.player.getX();
            panZ = wz - (mouseY - height / 2.0) * scale - mc.player.getZ();
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            panX = panZ = 0;
            return true;
        }
        if (button == 0) {
            Dot d = nearest(dots(), mouseX, mouseY);
            if (d != null) {
                if (d.player() != null) SightClient.togglePin(d.player());
                else SightClient.togglePin(d.mob());
                return true;
            }
            dragging = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging) {
            panX -= dragX * scale;
            panZ -= dragY * scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KegareKeys.SIX_EYES_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
