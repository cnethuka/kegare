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
 * the prompt keys as they go down (stamped for sub-tick timing first), keeps them within the leash in Maintain, and draws it: "Hold" and
 * the key at the start, then the tug of war, the prompts closing on their beat, and the fight's scores.
 */
public final class ClashClient {
    private static final ResourceLocation LAYER = Kegare.id("domain_clash");
    private static final int BAR_WIDTH = 160;
    private static final int MINE = 0xFFE6E1DA, THEIRS = 0xFF5E5953, HIT = 0xFF7FD27F, MISS = 0xFFE05A4E;
    @Nullable private static DomainNet.ClashState state;
    @Nullable private static DomainNet.ClashPrompt prompt;
    /** When the prompt came, and its beat, on this client's clock (ms). */
    private static double promptBeatMs;
    /** The last judged prompt, and when (client ms), for a moment's flash. */
    private static int judged;
    private static long judgedAt;
    /** A decided clash's result, and when (client ms), shown a moment. */
    private static int result;
    private static long resultAt;
    private static Boolean sentHeld;

    private ClashClient() {}

    public static void init(IEventBus modBus) {
        DomainNet.ClashState.clientHandler = ClashClient::onState;
        DomainNet.ClashPrompt.clientHandler = ClashClient::onPrompt;
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, ClashClient::render));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener(ClashClient::onKey);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    private static void clear() {
        state = null;
        prompt = null;
        sentHeld = null;
        result = 0;
        DomainBounds.localHold = null;
    }

    private static void onState(DomainNet.ClashState s) {
        if (s.phase() < 0) {
            result = s.result();
            resultAt = ClientStamps.nowMs();
            state = null;
            prompt = null;
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
        if (s.phase() != 1) prompt = null;
    }

    private static void onPrompt(DomainNet.ClashPrompt p) {
        if (p.result() != DomainNet.ClashPrompt.PENDING) {
            if (prompt != null && prompt.id() == p.id()) prompt = null;
            judged = p.result();
            judgedAt = ClientStamps.nowMs();
            return;
        }
        prompt = p;
        promptBeatMs = Double.isNaN(p.beatClientMs()) ? ClientStamps.nowMs() + p.leadMs() : p.beatClientMs();
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

    /** A prompt key going down while a prompt waits: stamped, then sent. */
    private static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || prompt == null || state == null || state.phase() != 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        int which = which(mc, key);
        if (which < 0) return;
        PacketDistributor.sendToServer(new TimingNet.Stamp(TimedInput.CLASH, ClientStamps.nowMs()));
        PacketDistributor.sendToServer(new DomainNet.ClashPress(which));
    }

    private static int which(Minecraft mc, InputConstants.Key key) {
        KeyMapping[] keys = promptKeys(mc);
        for (int i = 0; i < keys.length; i++) if (!keys[i].isUnbound() && keys[i].getKey().equals(key)) return i;
        return -1;
    }

    private static KeyMapping[] promptKeys(Minecraft mc) {
        return new KeyMapping[] {mc.options.keyJump, mc.options.keyShift, mc.options.keySprint};
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
            case 1 -> { // Maintain: the tug of war and the prompts
                GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.maintain", left), cx, top - 12, 1f, 0xFFEDE8F2);
                bar(g, cx, top, s.bar(), s.barrier());
                if (!s.holding()) GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.let_go"), cx, top + 14, 1f, MISS);
                drawPrompt(g, mc, cx, top + 38, now);
            }
            case 2 -> { // Fight: the scores
                GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.fight", left), cx, top - 12, 1f, 0xFFEDE8F2);
                GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.domain.clash.score", score(s.myScore()), score(s.theirScore())),
                        cx, top, 1.5f, s.myScore() >= s.theirScore() ? GuiDraw.GOLD : 0xFFEDE8F2);
            }
            default -> {}
        }
        if (judged != 0 && now - judgedAt < 500) {
            boolean hit = judged == DomainNet.ClashPrompt.HIT;
            GuiDraw.centered(g, mc.font, Component.translatable(hit ? "hud.kegare.domain.clash.hit" : "hud.kegare.domain.clash.miss"),
                    cx, top + 62, 1f, hit ? HIT : MISS);
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

    /** The prompt: its key in a box, and a frame closing on it that meets it on the beat; the window lit round it. */
    private static void drawPrompt(GuiGraphics g, Minecraft mc, int cx, int y, long now) {
        DomainNet.ClashPrompt p = prompt;
        if (p == null) return;
        KeyMapping key = promptKeys(mc)[Mth.clamp(p.key(), 0, 2)];
        Component name = key.getTranslatedKeyMessage();
        double toBeat = promptBeatMs - now;
        float lead = Math.max(1, p.leadMs());
        float t = (float) Mth.clamp(toBeat / lead, 0.0, 1.0); // 1 when it shows, 0 on the beat
        boolean inWindow = Math.abs(toBeat) <= p.windowMs() / 2.0;
        int w = Math.max(40, mc.font.width(name) + 16), h = 20;
        int grow = Math.round(t * 30);
        int frame = inWindow ? HIT : 0xFFEDE8F2;
        outline(g, cx - w / 2 - grow, y - h / 2 - grow, w + grow * 2, h + grow * 2, frame);
        g.fill(cx - w / 2, y - h / 2, cx + w / 2, y + h / 2, 0xD0101010);
        outline(g, cx - w / 2, y - h / 2, w, h, inWindow ? HIT : 0xFF9A948C);
        GuiDraw.centered(g, mc.font, name, cx, y - 4, 1f, inWindow ? HIT : 0xFFEDE8F2);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Whether this client's player is in a clash right now. */
    public static boolean inClash() {
        return state != null;
    }
}
