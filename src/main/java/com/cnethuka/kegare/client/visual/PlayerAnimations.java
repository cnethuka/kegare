package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Kegare's player animation layer (Player Animation Library by ZigyTheBird). Animations live in
 * {@code assets/<namespace>/player_animations/*.json} (Blockbench/GeckoLib format); an animation's ID is the
 * namespace plus the animation's name inside the file. A missing animation logs a warning once and is skipped.
 */
public final class PlayerAnimations {
    public static final ResourceLocation LAYER = Kegare.id("abilities");
    /** PAL priorities: 1500+ is for important gameplay animations. */
    private static final int PRIORITY = 1600;

    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    /** What this layer last started per player, so stopping one loop never cuts a newer animation. */
    private static final Map<UUID, ResourceLocation> CURRENT = new ConcurrentHashMap<>();

    private PlayerAnimations() {}

    /** Must run in {@code FMLClientSetupEvent#enqueueWork} (PAL's rule on NeoForge). */
    public static void registerLayer() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
            // Only triggered animations play on this layer; the state handler never starts anything itself.
            PlayerAnimationController controller = new PlayerAnimationController(player, (c, state, setter) -> PlayState.STOP);
            controller.setFirstPersonModeHandler(c -> KegareClientConfig.FIRST_PERSON_ANIMATIONS.get()
                    ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
            controller.setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true));
            return controller;
        });
    }

    public static void play(AbstractClientPlayer player, ResourceLocation animation, boolean loop) {
        try {
            PlayerAnimationController controller = controller(player);
            if (controller == null) return;
            if (!PlayerAnimResources.hasAnimation(animation)) {
                warnOnce(animation, "no such animation");
                return;
            }
            var raw = PlayerRawAnimationBuilder.begin()
                    .then(animation, loop ? Animation.LoopType.LOOP : Animation.LoopType.PLAY_ONCE)
                    .build();
            controller.triggerAnimation(raw);
            CURRENT.put(player.getUUID(), animation);
        } catch (Throwable t) {
            warnOnce(animation, t.toString());
        }
    }

    /** Stops {@code animation} if it is what this layer is playing on the player. */
    public static void stop(AbstractClientPlayer player, ResourceLocation animation) {
        if (animation.equals(CURRENT.get(player.getUUID()))) stopAll(player);
    }

    public static void stopAll(AbstractClientPlayer player) {
        CURRENT.remove(player.getUUID());
        try {
            PlayerAnimationController controller = controller(player);
            if (controller != null) controller.stopTriggeredAnimation();
        } catch (Throwable t) {
            Kegare.LOGGER.debug("Couldn't stop animations on {}", player.getScoreboardName(), t);
        }
    }

    public static void clear() {
        CURRENT.clear();
    }

    @Nullable
    private static PlayerAnimationController controller(AbstractClientPlayer player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController c ? c : null;
    }

    private static void warnOnce(ResourceLocation animation, String why) {
        if (WARNED.add(animation)) Kegare.LOGGER.warn("Player animation {} can't play ({}); skipping it", animation, why);
    }
}
