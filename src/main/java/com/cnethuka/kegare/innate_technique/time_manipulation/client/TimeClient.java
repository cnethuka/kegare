package com.cnethuka.kegare.innate_technique.time_manipulation.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeIds;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeNet;
import com.cnethuka.kegare.innate_technique.time_manipulation.TimeRegistry;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Time Manipulation's client entry point (DESIGN §3.26): its renderer, what it draws, Standstill's freeze, and its hand rules. */
public final class TimeClient {
    private TimeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(TimeClient::registerRenderers);
        TimeStopClient.init();
        TimeNet.Fx.setClientHandler(TimeFx::onFx);
        TimeNet.Mark.setClientHandler(MarkClient::onMark);
        // Accelerate's afterimages follow whoever runs it, from its toggle loop (drawn here, not by Photon).
        ClientVisuals.drawnByMove(TimeIds.FX_ACCELERATE);
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void loopStarted(Entity entity, String key, Visual visual) {
                if (visual.effect().filter(TimeIds.FX_ACCELERATE::equals).isPresent()) TimeFx.accelerateStarted(entity);
            }

            @Override
            public void loopStopped(Entity entity, String key, float ticks, int growTicks) {
                if (key.startsWith(TimeIds.ACCELERATE_LOOP_KEY)) TimeFx.accelerateStopped(entity);
            }
        });
        // Standstill's watch is raised before the face and Stasis Field's palm pushed at the ground: in your own first person
        // they'd cross the middle of the view, so there it's vanilla's hands.
        PlayerAnimations.keepVanillaHands(TimeIds.FIRST_PERSON_VANILLA);
        // The rest keep the hands low and to the sides: in your own first person, only while you look about level.
        PlayerAnimations.handsWhileLevel(TimeIds.FIRST_PERSON_WHILE_LEVEL);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) TimeFx.tick(level);
        });
        NeoForge.EVENT_BUS.addListener(TimeClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            TimeFx.clear();
            MarkClient.clear();
        });
        TimeHud.init();
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MarkClient.render(new PoseStack(), event.getCamera().getPosition());
        TimeFx.render(new PoseStack(), event.getCamera(), partial);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TimeRegistry.DECELERATE.get(), DecelerateRenderer::new);
    }
}
