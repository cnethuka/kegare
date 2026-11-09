package com.cnethuka.kegare.severed.client;

import com.cnethuka.kegare.client.StatusLayer;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.severed.BlurPayload;
import com.cnethuka.kegare.severed.SeveredIds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Severed players' client side (DESIGN §3.27): Senses, Blur's drive, feel, smear and HUD cues. */
public final class SeveredClient {
    private static final int BLUR_COLOR = 0xFFE4DDD2;
    private static final int WINDED_COLOR = 0xFFC9A27E;
    private static final int CLOSING_COLOR = 0xFFD9C9AE;
    private static final int BAR_WIDTH = 34;

    private SeveredClient() {}

    public static void init(IEventBus modBus) {
        SensesClient.init();
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) m -> SensesClient.reload()));
        BlurPayload.setClientHandler(BlurDrive::set);
        com.cnethuka.kegare.severed.RushPayload.setClientHandler(RushDrive::start);
        // R while Blurring is Closing Line: it goes to the server on the press, in or out of Technique Mode (which they never have).
        com.cnethuka.kegare.client.ClientInput.addSpecialKeyNeeded(BlurDrive::on);
        BlurFeel.init(modBus);
        // Blur's smear and dust are drawn here (BlurFx), not by Photon, from its loop on the one blurring.
        ClientVisuals.drawnByMove(SeveredIds.FX_BLUR);
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void loopStarted(Entity entity, String key, Visual visual) {
                if (visual.effect().filter(SeveredIds.FX_BLUR::equals).isPresent()) BlurFx.started(entity);
            }

            @Override
            public void loopStopped(Entity entity, String key, float ticks, int growTicks) {
                if (key.equals(SeveredIds.BLUR_LOOP)) BlurFx.stopped(entity);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                RushDrive.tick(mc.player);
                BlurDrive.tick(mc.player);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            BlurFeel.tick();
            BlurFx.tick(level);
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                BlurFx.render(new PoseStack(), e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            BlurDrive.reset();
            RushDrive.reset();
            BlurFeel.reset();
            BlurFx.clear();
        });
        StatusLayer.addExtra(SeveredClient::hud);
        com.cnethuka.kegare.client.visual.PlayerAnimations.keepVanillaHands(SeveredIds.FIRST_PERSON_VANILLA);
        com.cnethuka.kegare.client.visual.PlayerAnimations.handsWhileLevel(SeveredIds.FIRST_PERSON_WHILE_LEVEL);
    }

    /**
     * Under the crosshair with the melee cues: "Blur" while it's on, "Winded" while it can't come back on, and "Closing Line" while
     * it cools down (a countdown bar, as Dash's).
     */
    private static int hud(GuiGraphics g, Minecraft mc, int cx, int y) {
        BlurPayload blur = BlurDrive.state;
        if (blur.on()) {
            GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.severed.blur"), cx, y, 0.75f, BLUR_COLOR);
            y += 9;
        }
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        y = countdown(g, mc, cx, y, "hud.kegare.severed.winded", blur.windedUntil() - now, blur.windedTicks(), WINDED_COLOR);
        return countdown(g, mc, cx, y, "hud.kegare.severed.closing_line", blur.closingReadyAt() - now, blur.closingCooldown(), CLOSING_COLOR);
    }

    private static int countdown(GuiGraphics g, Minecraft mc, int cx, int y, String key, long left, int total, int color) {
        if (left <= 0 || total <= 0) return y;
        GuiDraw.centered(g, mc.font, Component.translatable(key), cx, y, 0.75f, color);
        int top = y + 7;
        int fill = Mth.ceil(BAR_WIDTH * Math.min(1f, left / (float) total));
        g.fill(cx - BAR_WIDTH / 2 - 1, top, cx + BAR_WIDTH / 2 + 1, top + 3, 0xAA000000);
        g.fill(cx - BAR_WIDTH / 2, top + 1, cx - BAR_WIDTH / 2 + fill, top + 2, color);
        return top + 5;
    }
}
