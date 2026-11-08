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
 * What the Impact System does to the whole picture (DESIGN §3.23), once the world and your hand are drawn and before the HUD (called
 * from {@code GameRendererMixin}):
 * <ul>
 * <li><b>Hit-stop</b>: the frame a hit lands on is kept and shown again for a few frames, so the picture freezes. Only the picture:
 * the game ticks on underneath, keys are read, the HUD stays live, and when it ends you see where everything really is now.</li>
 * <li><b>Screen effects</b>: post-processing chains ({@code assets/<namespace>/shaders/post/<path>.json}, Minecraft's own
 * {@link PostChain}) played over the whole screen for a time, fading in and out by their {@code Amount} uniform. The impact frame is
 * one; the server's screen effect hook plays others (grayscale for Standstill, to come).</li>
 * </ul>
 * A chain that won't load (a driver that can't run it) is logged once and skipped; the rest still plays.
 */
public final class ScreenFx {
    private static @Nullable TextureTarget held;
    private static boolean capturing, holding;
    private static long holdUntil;

    private static final Map<ResourceLocation, PostChain> CHAINS = new HashMap<>();
    private static final Set<ResourceLocation> BROKEN = new HashSet<>();
    private static final Map<ResourceLocation, Playing> PLAYING = new LinkedHashMap<>();
    private static int chainWidth, chainHeight;

    private record Playing(long start, long end, long fade) {
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
        if (!holding) capturing = true;
        holdUntil = Math.max(holdUntil, System.nanoTime() + millis * 1_000_000L);
    }

    public static boolean holding() {
        return capturing || holding;
    }

    /** Plays a screen effect for {@code millis}, fading in and out over {@code fadeMillis}. Again while it plays: from now. */
    public static void play(ResourceLocation effect, long millis, long fadeMillis) {
        if (millis <= 0) {
            PLAYING.remove(effect);
            return;
        }
        long now = System.nanoTime();
        Playing was = PLAYING.get(effect);
        long start = was != null && was.end > now ? was.start : now; // already showing: no fade in again
        PLAYING.put(effect, new Playing(start, now + millis * 1_000_000L, Math.min(fadeMillis, millis / 2) * 1_000_000L));
    }

    public static boolean playing(ResourceLocation effect) {
        Playing p = PLAYING.get(effect);
        return p != null && p.end > System.nanoTime();
    }

    public static void afterLevel(DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        long now = System.nanoTime();
        if (capturing) { // this frame is the one the hit landed on: keep it, and show it as it is
            capturing = false;
            holding = true;
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
            if (now >= e.getValue().end) {
                it.remove();
                continue;
            }
            PostChain chain = chain(mc, main, e.getKey());
            if (chain == null) continue;
            chain.setUniform("Amount", e.getValue().amount(now));
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
        PLAYING.clear();
    }
}
