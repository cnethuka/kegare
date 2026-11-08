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
    private static final int BAR_WIDTH = 34;

    private SeveredClient() {}

    public static void init(IEventBus modBus) {
        SensesClient.init();
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener((ResourceManagerReloadListener) m -> SensesClient.reload()));
        BlurPayload.setClientHandler(BlurDrive::set);
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
            if (mc.player != null) BlurDrive.tick(mc.player);
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
            BlurFeel.reset();
            BlurFx.clear();
        });
        StatusLayer.addExtra(SeveredClient::hud);
    }

    /** Under the crosshair with the melee cues: "Blur" while it's on, "Winded" while it can't come back on. */
    private static int hud(GuiGraphics g, Minecraft mc, int cx, int y) {
        BlurPayload blur = BlurDrive.state;
        if (blur.on()) {
            GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.severed.blur"), cx, y, 0.75f, BLUR_COLOR);
            y += 9;
        }
        long left = mc.level == null ? 0 : blur.windedUntil() - mc.level.getGameTime();
        if (left > 0 && blur.windedTicks() > 0) {
            GuiDraw.centered(g, mc.font, Component.translatable("hud.kegare.severed.winded"), cx, y, 0.75f, WINDED_COLOR);
            int top = y + 7;
            int fill = Mth.ceil(BAR_WIDTH * Math.min(1f, left / (float) blur.windedTicks()));
            g.fill(cx - BAR_WIDTH / 2 - 1, top, cx + BAR_WIDTH / 2 + 1, top + 3, 0xAA000000);
            g.fill(cx - BAR_WIDTH / 2, top + 1, cx - BAR_WIDTH / 2 + fill, top + 2, WINDED_COLOR);
            y = top + 5;
        }
        return y;
    }
}
