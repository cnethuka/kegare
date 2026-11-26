package com.cnethuka.kegare.domain.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.domain.DomainBounds;
import com.cnethuka.kegare.domain.DomainNet;
import com.cnethuka.kegare.timing.TimedInput;
import com.cnethuka.kegare.timing.TimingNet;
import com.cnethuka.kegare.timing.client.ClientStamps;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * A Domain clash on its caster's client (DESIGN §3.35): it tells the server whether the key that opens their Domain is held, sends
 * their attack key as it goes down in Maintain (stamped for sub-tick timing first), keeps them within the leash there, and draws it:
 * "Hold" and the key at the start, then the tug of war with their meter under it (a line sweeping over a mark), and the fight's scores.
 */
public final class ClashClient {
    private static final ResourceLocation LAYER = Kegare.id("domain_clash");
    private static final int BAR_WIDTH = 160;
    private static final int MINE = 0xFFE6E1DA, THEIRS = 0xFF5E5953, HIT = 0xFF7FD27F, MISS = 0xFFE05A4E;
    @Nullable private static DomainNet.ClashState state;
    private static final int MARK = 0xFF4FA3E8, MARK_MIDDLE = 0xFFB5DCFF, LINE = 0xFFE5403A;
    @Nullable private static DomainNet.ClashSweep sweep;
    /** When the line began sweeping, on this client's clock (ms). */
    private static double sweepStartMs;
    /** The last judged press and how good it was, and when (client ms), for a moment's flash. */
    private static int judged;
    private static float judgedPrecision;
    private static long judgedAt;
    /** A decided clash's result, and when (client ms), shown a moment. */
    private static int result;
    private static long resultAt;
    private static Boolean sentHeld;

    private ClashClient() {}

    public static void init(IEventBus modBus) {
        DomainNet.ClashState.clientHandler = ClashClient::onState;
        DomainNet.ClashSweep.clientHandler = ClashClient::onSweep;
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, ClashClient::render));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener(ClashClient::onKey);
        NeoForge.EVENT_BUS.addListener(ClashClient::onMouse);
        NeoForge.EVENT_BUS.addListener(ClashClient::onClick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    private static void clear() {
        state = null;
        sweep = null;
        sentHeld = null;
        result = 0;
        DomainBounds.localHold = null;
    }

    private static void onState(DomainNet.ClashState s) {
        if (s.phase() < 0) {
            result = s.result();
            resultAt = ClientStamps.nowMs();
            state = null;
            sweep = null;
            sentHeld = null;
            DomainBounds.localHold = null;
            return;
        }
        boolean fresh = state == null;
        state = s;
        if (fresh) sentHeld = null; // say at once whether the key is held
        if (s.leash() >= 0 && s.phase() <= 1) {
            DomainBounds.localLeash = s.leash();
            DomainBounds.localHold = new Vec3(s.holdX(), s.holdY(), s.holdZ());
        } else {
            DomainBounds.localHold = null;
        }
        if (s.phase() != 1) sweep = null;
    }

    private static void onSweep(DomainNet.ClashSweep p) {
        if (p.result() != DomainNet.ClashSweep.NONE) {
            judged = p.result();
            judgedPrecision = p.precision();
            judgedAt = ClientStamps.nowMs();
        }
        // The line's own start never changes within a clash: kept from the first one, so it doesn't jump as later ones arrive.
        if (sweep == null) sweepStartMs = Double.isNaN(p.startClientMs()) ? ClientStamps.nowMs() - p.elapsedMs() : p.startClientMs();
        sweep = p;
    }

    /** Where the line is on the meter now, 0 to 1: across and back. */
    static double lineAt(double nowMs, double startMs, int sweepMs) {
        double u = ((nowMs - startMs) / Math.max(1, sweepMs)) % 2.0;
        if (u < 0) u += 2.0;
        return u <= 1.0 ? u : 2.0 - u;
    }

    /** Every client tick in a clash: whether the key that opens their Domain is down, told to the server when it changes. */
    private static void tick() {
        DomainNet.ClashState s = state;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || mc.getConnection() == null) return;
        if (s.phase() > 1) return; // the Fight: nothing to hold
        boolean held = held(mc, s.holdSlot());
        if (sentHeld == null || sentHeld != held) {
            sentHeld = held;
            PacketDistributor.sendToServer(new DomainNet.ClashHold(held));
        }
    }

    @Nullable
    private static KeyMapping holdKey(Minecraft mc, int slot) {
        if (slot == -1) return KegareKeys.ULTIMATE;
        if (slot >= 0 && slot < mc.options.keyHotbarSlots.length) return mc.options.keyHotbarSlots[slot];
        return null;
    }

    /** The key itself, read from the keyboard (or mouse): Technique Mode may keep it from vanilla, but it's still down. */
    private static boolean held(Minecraft mc, int slot) {
        KeyMapping mapping = holdKey(mc, slot);
        if (mapping == null || mapping.isUnbound()) return false;
        InputConstants.Key key = mapping.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(window, key.getValue());
    }

    private static boolean pressing() {
        Minecraft mc = Minecraft.getInstance();
        return sweep != null && state != null && state.phase() == 1 && mc.player != null && mc.screen == null;
    }

    /** The attack key going down in the Maintain, on the keyboard or the mouse: stamped, then sent. */
    private static void pressed(InputConstants.Key key) {
        if (!pressing()) return;
        KeyMapping attack = Minecraft.getInstance().options.keyAttack;
        if (attack.isUnbound() || !attack.getKey().equals(key)) return;
        PacketDistributor.sendToServer(new TimingNet.Stamp(TimedInput.CLASH, ClientStamps.nowMs()));
        PacketDistributor.sendToServer(new DomainNet.ClashPress());
    }

    private static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) pressed(InputConstants.getKey(event.getKey(), event.getScanCode()));
    }

    private static void onMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS) pressed(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
    }

    /** In the Maintain the attack key is the press: no swing, no block broken. */
    private static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() || !pressing()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    // ---------------------------------------------------------------------------------------------- drawing

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        int cx = g.guiWidth() / 2, top = g.guiHeight() / 7 + 10; // above a Domain's name, which shows at a third of the way down
        long now = ClientStamps.nowMs();
        DomainNet.ClashState s = state;
        if (s == null) {
            if (result != 0 && now - resultAt < 2500) {
                String key = switch (result) {
                    case 1 -> "hud.kegare.domain.clash.won";
                    case 2 -> "hud.kegare.domain.clash.lost";
                    default -> "hud.kegare.domain.clash.tie";
                };
                GuiDraw.centered(g, mc.font, Component.translatable(key), cx, top, 2f, result == 1 ? GuiDraw.GOLD : 0xFFEDE8F2);
            }
            return;
        }
        float secondsLeft = s.ticksLeft() / 20f;
        String left = String.format(Locale.ROOT, "%.1f", secondsLeft);
        switch (s.phase()) {
            case 0 -> { // Grip: hold the key
                Component keyName = holdName(mc, s.holdSlot());
                int color = s.holding() ? HIT : (((now / 250) & 1) == 0 ? MISS : 0xFFEDE8F2);
                GuiDraw.centered(g, mc.font, s.holdSlot() == -2 ? Component.translatable("hud.kegare.domain.clash.nowhere")
                        : Component.translatable("hud.kegare.domain.clash.grip", keyName), cx, top, 2f, color);
                bar(g, cx, top + 24, s.bar(), s.barrier());
            }
            case 1 -> { // Maintain: the tug of war and their meter
                GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.maintain", left), cx, top - 12, 1f, 0xFFEDE8F2);
                bar(g, cx, top, s.bar(), s.barrier());
                if (!s.holding()) GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.let_go"), cx, top + 14, 1f, MISS);
                drawMeter(g, mc, cx, top + 30, now);
            }
            case 2 -> { // Fight: the scores
                GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.fight", left), cx, top - 12, 1f, 0xFFEDE8F2);
                GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.score", score(s.myScore()), score(s.theirScore())),
                        cx, top, 1.5f, s.myScore() >= s.theirScore() ? GuiDraw.GOLD : 0xFFEDE8F2);
            }
            default -> {}
        }
        if (judged != 0 && now - judgedAt < 500) {
            boolean hit = judged == DomainNet.ClashSweep.HIT;
            String key = !hit ? "hud.kegare.domain.clash.miss" : judgedPrecision >= PERFECT ? "hud.kegare.domain.clash.perfect" : "hud.kegare.domain.clash.hit";
            GuiDraw.centered(g, mc.font, Component.translatable(key), cx, top + 62, hit && judgedPrecision >= PERFECT ? 1.5f : 1f,
                    !hit ? MISS : judgedPrecision >= PERFECT ? GuiDraw.GOLD : HIT);
        }
    }

    private static String score(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static Component holdName(Minecraft mc, int slot) {
        KeyMapping key = holdKey(mc, slot);
        return key == null ? Component.literal("?") : key.getTranslatedKeyMessage();
    }

    /** The tug of war: theirs from the left, yours from the right, meeting where the bar stands; your barrier's strength under it. */
    private static void bar(GuiGraphics g, int cx, int y, float bar, float barrier) {
        int half = BAR_WIDTH / 2;
        int meet = cx - Math.round(bar * half); // pushed toward them: the meeting point moves to their (left) end
        g.fill(cx - half - 2, y - 2, cx + half + 2, y + 10, 0xC0000000);
        g.fill(cx - half, y, meet, y + 8, THEIRS);
        g.fill(meet, y, cx + half, y + 8, MINE);
        g.fill(meet - 1, y - 3, meet + 1, y + 11, 0xFFFFFFFF);
        g.fill(cx, y + 8, cx + 1, y + 10, 0xFF000000);
        int w = Math.round(BAR_WIDTH * Mth.clamp(barrier, 0f, 1f));
        g.fill(cx - half, y + 11, cx - half + w, y + 13, 0xFFB8B2AA);
    }

    /** A press this near the mark's middle (1: on it) reads "Perfect". */
    private static final float PERFECT = 0.8f;
    private static final int METER_HEIGHT = 12;

    /**
     * Their meter: a dark strip as wide as the bar, their mark on it (blue, lighter in its middle, where a press pushes furthest), and the
     * red line sweeping across and back; under it, the key to press.
     */
    private static void drawMeter(GuiGraphics g, Minecraft mc, int cx, int y, long now) {
        DomainNet.ClashSweep p = sweep;
        if (p == null) return;
        int half = BAR_WIDTH / 2, left = cx - half;
        g.fill(left - 2, y - 2, cx + half + 2, y + METER_HEIGHT + 2, 0xC0000000);
        g.fill(left, y, cx + half, y + METER_HEIGHT, 0xFF24212A);
        int m0 = left + Math.round((p.mark() - p.halfWidth()) * BAR_WIDTH), m1 = left + Math.round((p.mark() + p.halfWidth()) * BAR_WIDTH);
        g.fill(m0, y, m1, y + METER_HEIGHT, MARK);
        int mid = Math.max(1, Math.round(p.halfWidth() * (1f - PERFECT) * BAR_WIDTH)), mx = left + Math.round(p.mark() * BAR_WIDTH);
        g.fill(mx - mid, y, mx + mid, y + METER_HEIGHT, MARK_MIDDLE);
        int lx = left + (int) Math.round(lineAt(now, sweepStartMs, p.sweepMs()) * BAR_WIDTH);
        g.fill(lx - 1, y - 3, lx + 1, y + METER_HEIGHT + 3, LINE);
        GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.press", mc.options.keyAttack.getTranslatedKeyMessage()),
                cx, y + METER_HEIGHT + 6, 1f, 0xFFEDE8F2);
    }

    /** Whether this client's player is in a clash right now. */
    public static boolean inClash() {
        return state != null;
    }
}
