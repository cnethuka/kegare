package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Freeze on a player, for everyone who can see them: frost creeping over the body as the stacks build, pixel art on the skin's own
 * texels ({@code textures/entity/freeze_frost*.png}, tools/blockbench/build_freeze_frost.py), so the body looks frozen itself.
 * <ul>
 * <li>How much: the server shows the stacks as a loop keyed by how many (FreezeService, {@link RimeIds#freezeStackKey}), so this
 * covers the stacks out of the most: up from the soles and the hands, the legs and arms, then the body, the head last. It creeps
 * to a new stack over about half a second, with a rim of rime on its edge, and melts back as stacks melt.</li>
 * <li>Frozen solid (the frozen loop): all of it, the whole body glazed in ice.</li>
 * <li>Breaking free ({@code freeze_shatter}): it's gone at once, burst off the body as solid chips of ice.</li>
 * </ul>
 * Drawn on the skin's outer layer (over a jacket or sleeves), lit by the world with a faint glow of its own ({@link #GLOW}) so it
 * reads at night. Not on an invisible player. Your own first person
 * draws no body; you have the stacks on the HUD.
 */
public final class FreezeClient {
    private static final ResourceLocation[] ART = {Kegare.id("textures/entity/freeze_frost.png"),
            Kegare.id("textures/entity/freeze_frost_slim.png")};
    private static final ResourceLocation[] ORDER = {Kegare.id("textures/entity/freeze_frost_order.png"),
            Kegare.id("textures/entity/freeze_frost_slim_order.png")};
    /** How finely the creep is drawn: a texture at each step of it. */
    static final int STEPS = 24;
    /** How far the frost creeps a tick, as a share of the whole body: a stack's worth (of 5) in about half a second; melting slower. */
    static final float GROW = 0.025f, MELT = 0.01f, FREEZE = 0.1f;
    /** Texels this close behind the creeping edge are rime. */
    static final float RIM = 0.05f;
    /** Rime on the creeping edge, and the glaze of ice in the art (block marks' RIME and ICE). */
    static final int RIME = argb(230, 230, 244, 255), GLAZE = argb(150, 112, 172, 234);
    /** Frozen solid, the glaze thickens to solid pale ice: plainly more than the most frost. */
    static final int SOLID = argb(215, 170, 214, 250);
    /**
     * The frost's faint glow (a minimum light, 0 to 15, as block marks' glow): it reads on a body at night, so you can count the
     * stacks in the dark, without the body looking lit.
     */
    static final int GLOW = 6;
    /** Stacks counted up to at most this many, whatever the most is set to. */
    private static final int MOST = 32;

    /** One layout's art: each texel's colour and when it frosts over (1..255; 0 off the outer layer). */
    record Art(int[] argb, int[] order) {}

    private static final Map<Integer, Float> COVER = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Art[] LOADED = new Art[2];
    private static final RandomSource RANDOM = RandomSource.create();

    private FreezeClient() {}

    static void init() {
        ClientVisuals.listen(new ClientVisuals.Listener() {
            @Override
            public void loopStopped(Entity entity, String key, float ticks, int growTicks) {
                if (RimeIds.FROZEN_LOOP_KEY.equals(key)) COVER.remove(entity.getId()); // burst off: FreezeClient#onThaw
            }
        });
        ClientVisuals.onPlayAt(RimeIds.FX_FREEZE_SHATTER, FreezeClient::onThaw);
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
        }
    }

    static int argb(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    // ---------------------------------------------------------------------------------------------- how much

    /** The most stacks (the server's, synced). */
    static int maxStacks() {
        try {
            return Math.max(1, RimeConfig.FREEZE_MAX_STACKS.get());
        } catch (IllegalStateException notLoaded) {
            return 5;
        }
    }

    static boolean frozen(Entity entity) {
        return ClientVisuals.loopTime(entity, RimeIds.FROZEN_LOOP_KEY, 0f) != null;
    }

    /** How much of the body the frost should cover: its stacks out of the most, all of it frozen solid. */
    static float target(Entity entity) {
        if (frozen(entity)) return 1f;
        int max = maxStacks();
        for (int n = 1; n < Math.min(max, MOST); n++) {
            if (ClientVisuals.loopTime(entity, RimeIds.freezeStackKey(n), 0f) != null) return n / (float) max;
        }
        return 0f;
    }

    /** A tick of creeping toward {@code target}: up steadily, down (melting) slower, and fast when frozen solid. */
    static float step(float shown, float target, boolean frozen) {
        if (shown < target) return Math.min(target, shown + (frozen ? FREEZE : GROW));
        return Math.max(target, shown - MELT);
    }

    static void tick(ClientLevel level) {
        Set<Integer> seen = new HashSet<>();
        for (Player player : level.players()) {
            float target = target(player);
            float shown = step(COVER.getOrDefault(player.getId(), 0f), target, target >= 1f);
            if (shown <= 0f) continue;
            COVER.put(player.getId(), shown);
            seen.add(player.getId());
        }
        COVER.keySet().retainAll(seen);
    }

    // ---------------------------------------------------------------------------------------------- the frost texture

    /** The step of the creep a coverage is drawn at: at least the first while there's any. */
    static int quantize(float cover) {
        return cover <= 0f ? 0 : Mth.clamp(Math.round(cover * STEPS), 1, STEPS);
    }

    /**
     * The skin-layout frost at {@code step} of {@link #STEPS}: each texel shown once the creep has passed it, rime where the edge
     * is now; frozen solid, the glaze thickened to solid ice all over (any bare texel too).
     */
    static int[] frost(Art art, int step, boolean frozen) {
        float f = step / (float) STEPS;
        int[] out = new int[art.argb().length];
        for (int i = 0; i < out.length; i++) {
            int order = art.order()[i];
            if (order == 0) continue;
            float t = (order - 1) / 254f;
            if (t > f + 1e-4f) continue;
            int c = art.argb()[i];
            if (c != 0 && !frozen && f < 1f && f - t < RIM) c = RIME;
            if (frozen && (c == 0 || c == GLAZE)) c = SOLID;
            out[i] = c;
        }
        return out;
    }

    private static @Nullable ResourceLocation texture(boolean slim, int step, boolean frozen) {
        String key = (slim ? "slim_" : "wide_") + step + (frozen ? "_frozen" : "");
        ResourceLocation id = TEXTURES.get(key);
        if (id != null) return id;
        Art art = art(slim ? 1 : 0);
        if (art == null) return null;
        int[] px = frost(art, step, frozen);
        NativeImage image = new NativeImage(64, 64, true);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int c = px[y * 64 + x];
                // NativeImage keeps ABGR.
                image.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF);
            }
        }
        id = Kegare.id("dynamic/freeze_frost_" + key);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        TEXTURES.put(key, id);
        return id;
    }

    private static @Nullable Art art(int layout) {
        if (LOADED[layout] != null) return LOADED[layout];
        var manager = Minecraft.getInstance().getResourceManager();
        try (InputStream a = manager.getResourceOrThrow(ART[layout]).open();
                InputStream o = manager.getResourceOrThrow(ORDER[layout]).open();
                NativeImage ai = NativeImage.read(a); NativeImage oi = NativeImage.read(o)) {
            int[] argb = new int[64 * 64], order = new int[64 * 64];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int abgr = ai.getPixelRGBA(x, y);
                    int alpha = abgr >>> 24;
                    argb[y * 64 + x] = alpha == 0 ? 0 : alpha << 24 | (abgr & 0xFF) << 16 | (abgr >> 8 & 0xFF) << 8 | (abgr >> 16 & 0xFF);
                    order[y * 64 + x] = oi.getPixelRGBA(x, y) & 0xFF;
                }
            }
            LOADED[layout] = new Art(argb, order);
        } catch (IOException | RuntimeException e) {
            Kegare.LOGGER.warn("Couldn't read Freeze's frost {}: {}", ART[layout], e.toString());
        }
        return LOADED[layout];
    }

    /** Resources reloaded: make the textures again when they're next drawn. */
    static void forget() {
        TEXTURES.values().forEach(id -> Minecraft.getInstance().getTextureManager().release(id));
        TEXTURES.clear();
        LOADED[0] = LOADED[1] = null;
    }

    static void clear() {
        COVER.clear();
    }

    // ---------------------------------------------------------------------------------------------- on the body

    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Float cover = COVER.get(player.getId());
            if (cover == null || cover <= 0f || player.isInvisible()) return;
            ResourceLocation texture = texture(player.getSkin().model() == PlayerSkin.Model.SLIM, quantize(cover), frozen(player));
            if (texture == null) return;
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            // Only the outer layer, shown whatever the skin's settings: the frost lies over everything.
            ModelPart[] inner = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
            ModelPart[] outer = {model.hat, model.jacket, model.rightSleeve, model.leftSleeve, model.rightPants, model.leftPants};
            boolean[] was = new boolean[12];
            for (int i = 0; i < 6; i++) {
                was[i] = inner[i].visible;
                was[i + 6] = outer[i].visible;
                inner[i].visible = false;
                outer[i].visible = true;
            }
            try {
                model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(texture)), BlockMarks.lit(light, GLOW),
                        OverlayTexture.NO_OVERLAY, -1);
            } finally {
                for (int i = 0; i < 6; i++) {
                    inner[i].visible = was[i];
                    outer[i].visible = was[i + 6];
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- breaking free

    /** Breaking free: the frost bursts off the body as solid chips of ice (not by your own camera), then the flash and ring play. */
    static boolean onThaw(ClientLevel level, Vec3 pos, int entityId, Visual visual) {
        if (LanceAftermath.nearOwnCamera(pos, 1.6)) return false;
        for (int i = 0; i < 10; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = pos.add(out.scale(0.25)).add(0, (RANDOM.nextDouble() - 0.5) * 1.2, 0);
            Vec3 vel = out.scale(0.08 + RANDOM.nextDouble() * 0.1).add(0, 0.06 + RANDOM.nextDouble() * 0.12, 0);
            LanceAftermath.chunk(level, at, vel, 0.8f + RANDOM.nextFloat() * 0.5f, LanceMesh.SHARD, "chip_" + RANDOM.nextInt(4),
                    14 + RANDOM.nextInt(10));
        }
        return false;
    }
}
