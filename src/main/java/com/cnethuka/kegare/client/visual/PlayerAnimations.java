package com.cnethuka.kegare.client.visual;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.animation.PlayerRawAnimationBuilder;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
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
 * <p>
 * <b>Fades live here, not in the files</b>, because a Blockbench export can't carry them: every animation blends in from
 * whatever the player was doing and back out to it, through PAL's fade modifiers. Client config {@code visuals.animationFades}
 * turns them off. A channel an animation doesn't key keeps the vanilla pose either way.
 */
public final class PlayerAnimations {
    public static final ResourceLocation LAYER = Kegare.id("abilities");
    /** PAL priorities: 1500+ is for important gameplay animations. */
    private static final int PRIORITY = 1600;
    /** How long each blend takes. Short: a cast has to read the moment it goes off (the tell, DESIGN §3.15). */
    private static final int FADE_TICKS = 3;
    private static final EasingType FADE_EASING = EasingType.EASE_IN_OUT_SINE;

    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    /** What this layer last started per player, so stopping one loop never cuts a newer animation. */
    private static final Map<UUID, ResourceLocation> CURRENT = new ConcurrentHashMap<>();
    /** What this layer last started per player, staying after it's stopped: a stopped animation still has its fade out to play. */
    private static final Map<UUID, ResourceLocation> LATEST = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> VANILLA_HANDS = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> WHILE_LEVEL = ConcurrentHashMap.newKeySet();
    /** How far down (degrees) you can look and still see a {@link #handsWhileLevel} animation's hands in your own first person. */
    private static final float LEVEL_PITCH = 15f;

    private PlayerAnimations() {}

    /** Must run in {@code FMLClientSetupEvent#enqueueWork} (PAL's rule on NeoForge). */
    public static void registerLayer() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
            // Only triggered animations play on this layer; the state handler never starts anything itself.
            PlayerAnimationController controller = new PlayerAnimationController(player, (c, state, setter) -> PlayState.STOP);
            controller.setFirstPersonModeHandler(c -> KegareClientConfig.FIRST_PERSON_ANIMATIONS.get() && !keepsVanillaHands(player)
                    ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE);
            controller.setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true));
            return controller;
        });
    }

    /**
     * These animations play for everyone else but leave the player's own first-person hands to vanilla. The first-person model draws
     * whatever's held at its real size right by the camera, usually edge on: fine for an empty hand, but a sword or an axe swung into
     * a guard fills the view with its dark outline and hides the opponent.
     */
    public static void keepVanillaHands(Iterable<ResourceLocation> animations) {
        animations.forEach(VANILLA_HANDS::add);
    }

    /**
     * These show their hands in your own first person only while you look about level. The first-person model's arms hang from the
     * body, which doesn't tip with the view: hands held in front of the chest sit at the bottom corners of a level view, but looking
     * down brings them up into its middle. Looking further down than that, the hands are vanilla's until you look up again.
     */
    public static void handsWhileLevel(Iterable<ResourceLocation> animations) {
        animations.forEach(WHILE_LEVEL::add);
    }

    private static boolean keepsVanillaHands(AbstractClientPlayer player) {
        ResourceLocation latest = LATEST.get(player.getUUID());
        return latest != null && (VANILLA_HANDS.contains(latest) || WHILE_LEVEL.contains(latest) && player.getXRot() > LEVEL_PITCH);
    }

    /** Whether this layer last started {@code animation} on the player, and it hasn't been stopped. A play-once counts until something replaces it. */
    public static boolean isCurrent(AbstractClientPlayer player, ResourceLocation animation) {
        return animation.equals(CURRENT.get(player.getUUID()));
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
                    .then(animation, loop ? loopType(animation) : Animation.LoopType.PLAY_ONCE)
                    .build();
            controller.removeAllModifiers(); // whatever is left of the last animation's fades
            if (KegareClientConfig.ANIMATION_FADES.get()) {
                // From the animation being replaced if there is one, else from the vanilla pose.
                controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, FADE_EASING), raw, true);
                // A play-once hands back to the vanilla pose over its last ticks. A loop never reaches an end: see stopAll.
                if (!loop) controller.addModifierLast(AbstractFadeModifier.standardFadeOut(FADE_TICKS, FADE_EASING));
            } else {
                controller.triggerAnimation(raw);
            }
            CURRENT.put(player.getUUID(), animation);
            LATEST.put(player.getUUID(), animation);
        } catch (Throwable t) {
            warnOnce(animation, t.toString());
        }
    }

    /**
     * How a looping phase repeats: from the loop point the animation's file declares ({@code loopTick}: an intro that
     * plays once, then the loop), else from its start.
     */
    private static Animation.LoopType loopType(ResourceLocation animation) {
        Animation.LoopType own = PlayerAnimResources.getAnimation(animation).loopType();
        boolean hasLoopPoint = own != Animation.LoopType.DEFAULT && own != Animation.LoopType.PLAY_ONCE
                && own != Animation.LoopType.HOLD_ON_LAST_FRAME && own != Animation.LoopType.LOOP;
        return hasLoopPoint ? own : Animation.LoopType.LOOP;
    }

    /** Stops {@code animation} if it is what this layer is playing on the player. */
    public static void stop(AbstractClientPlayer player, ResourceLocation animation) {
        if (animation.equals(CURRENT.get(player.getUUID()))) stopAll(player);
    }

    public static void stopAll(AbstractClientPlayer player) {
        CURRENT.remove(player.getUUID());
        try {
            PlayerAnimationController controller = controller(player);
            if (controller == null) return;
            controller.removeAllModifiers();
            if (KegareClientConfig.ANIMATION_FADES.get() && controller.isActive()) {
                // Stopped part way (a loop ending, a cast cut short): blends from the pose it's in to an empty animation,
                // which keys nothing, so that's the vanilla pose. It's over when the blend is.
                controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, FADE_EASING), RawAnimation.begin().thenWait(FADE_TICKS), false);
            } else {
                controller.stopTriggeredAnimation();
            }
        } catch (Throwable t) {
            Kegare.LOGGER.debug("Couldn't stop animations on {}", player.getScoreboardName(), t);
        }
    }

    public static void clear() {
        CURRENT.clear();
        LATEST.clear();
    }

    @Nullable
    private static PlayerAnimationController controller(AbstractClientPlayer player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController c ? c : null;
    }

    private static void warnOnce(ResourceLocation animation, String why) {
        if (WARNED.add(animation)) Kegare.LOGGER.warn("Player animation {} can't play ({}); skipping it", animation, why);
    }
}
