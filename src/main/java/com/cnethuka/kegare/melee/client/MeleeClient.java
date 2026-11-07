package com.cnethuka.kegare.melee.client;

import com.cnethuka.kegare.client.KegareKeys;
import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.melee.MeleeDashPayload;
import com.cnethuka.kegare.melee.MeleeHudPayload;
import com.cnethuka.kegare.melee.MeleeIds;
import com.cnethuka.kegare.melee.MeleeIntentPayload;
import com.cnethuka.kegare.client.visual.VanillaFallback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** The layer's keys and HUD. Input only asks: the server times and decides everything. */
public final class MeleeClient {
    private static final int BAR_WIDTH = 34;
    private static final int STRIKE_COLOR = 0xFFFFD24A;
    private static final int DASH_COLOR = 0xFFBFE9FF;
    private static final int RECOVERY_COLOR = 0xFFE08A6A;

    private static final DoubleTap SPRINT_TAPS = new DoubleTap();
    private static final DashDrive DRIVE = new DashDrive();
    private static MeleeHudPayload hud = MeleeHudPayload.NONE;
    private static boolean parryHeld;

    private MeleeClient() {}

    public static void init() {
        MeleeHudPayload.setClientHandler(MeleeClient::onHud);
        MeleeDashPayload.setClientHandler(payload -> {
            DRIVE.start(payload);
            DashFeel.start(payload);
        });
        // Only the target's own camera is that close to it. An attacker in your face still sees it land.
        ClientVisuals.setNearCameraDistance(MeleeIds.FX_STRIKE_IMPACT, 1.0);
        ClientVisuals.setNearCameraDistance(MeleeIds.FX_STRIKE_IMPACT_PHYSICAL, 1.0);
        ClientVisuals.setNearCameraDistance(MeleeIds.FX_STRIKE_BURST, 1.0);
        ClientVisuals.setFirstPersonVariant(MeleeIds.FX_DASH_TRAIL, MeleeIds.FX_DASH_TRAIL_SELF);
        ClientVisuals.setFirstPersonVariant(MeleeIds.FX_PARRY_SPARK, MeleeIds.FX_PARRY_SPARK_SELF);
        ClientVisuals.setFirstPersonVariant(MeleeIds.FX_GUARD_HIT, MeleeIds.FX_GUARD_HIT_SELF);
        StatusLayer.addExtra(MeleeClient::render);
        NeoForge.EVENT_BUS.addListener(MeleeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(MeleeClient::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(MeleeClient::onKey);
        NeoForge.EVENT_BUS.addListener(MeleeClient::onMouse);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            hud = MeleeHudPayload.NONE;
            parryHeld = false;
            SPRINT_TAPS.reset();
            DRIVE.stop();
            DashFeel.reset();
        });
        VanillaFallback.register(MeleeIds.FX_DASH_BURST, new VanillaFallback.Spec(ParticleTypes.CLOUD, 1, 22, 0.4, 0.12));
        VanillaFallback.register(MeleeIds.FX_DASH_TRAIL, new VanillaFallback.Spec(ParticleTypes.CLOUD, 1, 10, 0.3, 0.04));
        VanillaFallback.register(MeleeIds.FX_DASH_TRAIL_SELF, new VanillaFallback.Spec(ParticleTypes.CLOUD, 1, 2, 0.2, 0.02));
        VanillaFallback.register(MeleeIds.FX_PARRY_SPARK, new VanillaFallback.Spec(ParticleTypes.CRIT, 1, 14, 0.2, 0.3));
        VanillaFallback.register(MeleeIds.FX_GUARD_HIT, new VanillaFallback.Spec(ParticleTypes.CRIT, 1, 5, 0.15, 0.12));
        VanillaFallback.register(MeleeIds.FX_PARRY_SPARK_SELF, new VanillaFallback.Spec(ParticleTypes.CRIT, 1, 5, 0.2, 0.2));
        VanillaFallback.register(MeleeIds.FX_GUARD_HIT_SELF, new VanillaFallback.Spec(ParticleTypes.CRIT, 1, 2, 0.15, 0.1));
        VanillaFallback.register(MeleeIds.FX_STRIKE_IMPACT, new VanillaFallback.Spec(ParticleTypes.ENCHANTED_HIT, 1, 18, 0.3, 0.3));
        VanillaFallback.register(MeleeIds.FX_STRIKE_BURST, new VanillaFallback.Spec(ParticleTypes.ENCHANTED_HIT, 1, 8, 0.4, 0.2));
        VanillaFallback.register(MeleeIds.FX_STRIKE_IMPACT_PHYSICAL, new VanillaFallback.Spec(ParticleTypes.POOF, 1, 14, 0.3, 0.12));
    }

    private static void onHud(MeleeHudPayload payload) {
        if (payload.resetAttack() && Minecraft.getInstance().player != null) Minecraft.getInstance().player.resetAttackStrengthTicker();
        hud = payload;
    }

    /** Before the player moves this tick. */
    private static void onClientTickPre(ClientTickEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) DRIVE.tick(player);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            parryHeld = false;
            return;
        }
        while (KegareKeys.DASH.consumeClick()) dash(mc);
        boolean clicked = false;
        while (KegareKeys.PARRY.consumeClick()) clicked = true;
        boolean down = KegareKeys.PARRY.isDown() || clicked;
        if (down != parryHeld) {
            parryHeld = down;
            send(down ? MeleeIntentPayload.Action.PARRY_PRESS : MeleeIntentPayload.Action.PARRY_RELEASE);
        }
    }

    /** Sprint is Ctrl unless it's been rebound, so the double tap follows it. Holding it to sprint is one press. */
    private static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS && mc.options.keySprint.matches(event.getKey(), event.getScanCode())) sprintPressed(mc);
    }

    private static void onMouse(InputEvent.MouseButton.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() == GLFW.GLFW_PRESS && mc.options.keySprint.matchesMouse(event.getButton())) sprintPressed(mc);
    }

    private static void sprintPressed(Minecraft mc) {
        if (mc.screen != null || mc.player == null) return;
        if (SPRINT_TAPS.press(Util.getMillis(), KegareClientConfig.DASH_DOUBLE_TAP_MILLIS.get())) dash(mc);
    }

    private static void dash(Minecraft mc) {
        if (mc.player == null) return;
        PacketDistributor.sendToServer(new MeleeIntentPayload(MeleeIntentPayload.Action.DASH,
                (byte) Mth.sign(mc.player.input.forwardImpulse), (byte) Mth.sign(mc.player.input.leftImpulse)));
    }

    private static void send(MeleeIntentPayload.Action action) {
        PacketDistributor.sendToServer(new MeleeIntentPayload(action, (byte) 0, (byte) 0));
    }

    /** Under the crosshair, in and out of Technique Mode: only while there's something to wait for or use. */
    private static int render(GuiGraphics g, Minecraft mc, int cx, int y) {
        long now = mc.level.getGameTime();
        y = line(g, mc, cx, y, "hud.kegare.melee.strike", hud.strikeUntil() - now, hud.strikeTicks(), STRIKE_COLOR, 1f);
        y = line(g, mc, cx, y, "hud.kegare.melee.dash", hud.dashReadyAt() - now, hud.dashCooldown(), DASH_COLOR, 0.75f);
        return line(g, mc, cx, y, "hud.kegare.melee.recovery", hud.recoveryUntil() - now, hud.recovery(), RECOVERY_COLOR, 0.75f);
    }

    private static int line(GuiGraphics g, Minecraft mc, int cx, int y, String key, long left, int total, int color, float textScale) {
        if (left <= 0 || total <= 0) return y;
        GuiDraw.centered(g, mc.font, Component.translatable(key), cx, y, textScale, color);
        int top = y + Math.round(9 * textScale);
        int fill = Mth.ceil(BAR_WIDTH * Math.min(1f, left / (float) total));
        g.fill(cx - BAR_WIDTH / 2 - 1, top, cx + BAR_WIDTH / 2 + 1, top + 3, 0xAA000000);
        g.fill(cx - BAR_WIDTH / 2, top + 1, cx - BAR_WIDTH / 2 + fill, top + 2, color);
        return top + 5;
    }
}
