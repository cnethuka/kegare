package com.cnethuka.kegare.impact.client;

import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * What the Impact System does to the whole picture (DESIGN §3.23), after the world and your hand are drawn and before the HUD (called
 * from {@code GameRendererMixin}):
 * <ul>
 * <li><b>Hit-stop</b>: the frame a hit lands on is shown again for a few frames, freezing the picture. Only the picture: the game
 * ticks on underneath, keys are read, the HUD stays live, and when it ends everything shows where it really is.</li>
 * <li><b>Screen effects</b>: post-processing chains ({@code assets/<namespace>/shaders/post/<path>.json}, vanilla's
 * {@link PostChain}) over the whole screen for a time, faded by their {@code Amount} uniform. The impact frame is one; the server's
 * screen effect hook plays others (grayscale for players a Standstill freezes; its inverted frame comes from its own client code).</li>
 * </ul>
 * A chain that won't load (a driver that can't run it) is logged once and skipped; the rest still play.
 */
public final class ScreenFx {
    private static @Nullable TextureTarget held;
    private static boolean capturing, holding;
    /** The freeze asked for, counted from the frame it's captured on: a slow frame must never eat it. */
    private static long holdMillis;
    private static long holdUntil;

    private static final Map<ResourceLocation, PostChain> CHAINS = new HashMap<>();
    private static final Set<ResourceLocation> BROKEN = new HashSet<>();
    private static final Map<ResourceLocation, Playing> PLAYING = new LinkedHashMap<>();
    private static int chainWidth, chainHeight;

    /** {@code start} and {@code end} are -1 until it's first drawn: its time counts from there, so it's always seen at least once. */
    private record Playing(long millis, long fade, long start, long end) {
        Playing started(long now) {
            return start >= 0 ? this : new Playing(millis, fade, now, now + millis * 1_000_000L);
        }

        float amount(long now) {
            if (fade <= 0) return 1f;
            float in = (now - start) / (float) fade, out = (end - now) / (float) fade;
            return Math.max(0f, Math.min(1f, Math.min(in, out)));
        }
    }

    private ScreenFx() {}

    /** Freezes the picture for {@code millis} from the next frame (a hit landing). Overlapping freezes run to the latest end. */
    public static void hold(long millis) {
        if (millis <= 0) return;
        if (holding) {
            holdUntil = Math.max(holdUntil, System.nanoTime() + millis * 1_000_000L);
        } else {
            capturing = true;
            holdMillis = Math.max(holdMillis, millis);
        }
    }

    public static boolean holding() {
        return capturing || holding;
    }

    /** Plays a screen effect for {@code millis}, fading in and out over {@code fadeMillis}. Replayed while showing: {@code millis} from now. */
    public static void play(ResourceLocation effect, long millis, long fadeMillis) {
        if (millis <= 0) {
            PLAYING.remove(effect);
            return;
        }
        long now = System.nanoTime();
        Playing was = PLAYING.get(effect);
        long fade = Math.min(fadeMillis, millis / 2) * 1_000_000L;
        if (was != null && was.start >= 0 && was.end > now) { // already showing: no fade in again, it just lasts longer
            PLAYING.put(effect, new Playing(millis, fade, was.start, Math.max(was.end, now + millis * 1_000_000L)));
        } else {
            PLAYING.put(effect, new Playing(millis, fade, -1, -1));
        }
    }

    public static boolean playing(ResourceLocation effect) {
        Playing p = PLAYING.get(effect);
        return p != null && (p.start < 0 || p.end > System.nanoTime());
    }

    public static void afterLevel(DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        long now = System.nanoTime();
        if (capturing) { // this frame is the one the hit landed on: keep it, and show it as it is
            capturing = false;
            holding = true;
            holdUntil = now + holdMillis * 1_000_000L;
            holdMillis = 0;
            copy(main, held(main));
        } else if (holding) {
            if (now >= holdUntil) holding = false;
            else if (held != null && held.width == main.width && held.height == main.height) copy(held, main);
        }
        if (PLAYING.isEmpty()) return;
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        for (Iterator<Map.Entry<ResourceLocation, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ResourceLocation, Playing> e = it.next();
            Playing p = e.getValue().started(now);
            e.setValue(p);
            if (now > p.end && p.start != now) {
                it.remove();
                continue;
            }
            PostChain chain = chain(mc, main, e.getKey());
            if (chain == null) continue;
            chain.setUniform("Amount", p.amount(now));
            chain.process(deltaTracker.getGameTimeDeltaTicks());
        }
        main.bindWrite(false);
    }

    private static TextureTarget held(RenderTarget main) {
        if (held == null) held = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        else if (held.width != main.width || held.height != main.height) held.resize(main.width, main.height, Minecraft.ON_OSX);
        return held;
    }

    private static void copy(RenderTarget from, RenderTarget to) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    private static @Nullable PostChain chain(Minecraft mc, RenderTarget main, ResourceLocation effect) {
        if (BROKEN.contains(effect)) return null;
        if (chainWidth != main.width || chainHeight != main.height) {
            chainWidth = main.width;
            chainHeight = main.height;
            CHAINS.values().forEach(c -> c.resize(main.width, main.height));
        }
        PostChain chain = CHAINS.get(effect);
        if (chain != null) return chain;
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(effect.getNamespace(), "shaders/post/" + effect.getPath() + ".json");
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, file);
            chain.resize(main.width, main.height);
            CHAINS.put(effect, chain);
            return chain;
        } catch (Exception | LinkageError e) {
            BROKEN.add(effect);
            Kegare.LOGGER.warn("Screen effect {} can't load ({}); skipping it", effect, e.toString());
            return null;
        }
    }

    /** Resource packs changed: the chains load again. */
    public static void reload() {
        CHAINS.values().forEach(PostChain::close);
        CHAINS.clear();
        BROKEN.clear();
    }

    public static void clear() {
        capturing = holding = false;
        holdMillis = 0;
        PLAYING.clear();
    }
}
