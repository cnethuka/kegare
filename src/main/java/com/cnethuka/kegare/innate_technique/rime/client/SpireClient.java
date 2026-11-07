package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.innate_technique.rime.GlacialSpireEntity;
import com.cnethuka.kegare.innate_technique.rime.Rime;
import com.cnethuka.kegare.innate_technique.rime.RimeConfig;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.Kegare;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Everything round Glacial Spire's spike that isn't the spike, all on the client, read off the server's own loops on the marker (so it
 * keeps the server's time): while it warns, a pixel crack spreads over the ground to the reach of the hit, just as long as the
 * warning lasts, and chips of the ground jump out of it; when it erupts, the ground round its foot shatters and frosts and its
 * debris is thrown up; when the marker goes, the spike breaks up into solid chunks. No block is touched.
 * <p>
 * Also the caster's raised hand while charging: frost gathering in the fist (others, and you in third person; never in your own first
 * person, where the aiming ring is enough).
 */
public final class SpireClient {
    static final ResourceLocation CRACK = Kegare.id("spire_crack");
    static final ResourceLocation BURST = Kegare.id("spire_burst");
    static final String CHARGE = VisualService.key(Rime.GLACIAL_SPIRE, VisualPhase.CHARGE_LOOP);
    /** Ticks the crack and the shattered ground take to melt from the edge in, after the spike is gone. */
    static final int MELT_TICKS = 30;
    /** How far the hit reaches, for the rumble (the config's default when it can't be read). */
    private static final double RADIUS = 1.5;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Spire {
        Vec3 pos;
        float charge;
        boolean cracked;
        boolean erupted;
    }

    private static final Map<Integer, Spire> SPIRES = new HashMap<>();
    /** Where each charging player's raised fist was drawn last, and on which frame. */
    private record Fist(Vec3 at, long frame) {}
    private static final Map<Integer, Fist> FISTS = new HashMap<>();
    private static final Map<Integer, ClientVisuals.PositionalEffect> GATHER = new HashMap<>();

    private SpireClient() {}

    static int warningTicks() {
        try {
            return RimeConfig.ticks(RimeConfig.SPIRE_DELAY);
        } catch (IllegalStateException notLoaded) {
            return 15;
        }
    }

    static int standTicks() {
        try {
            return Math.max(1, RimeConfig.ticks(RimeConfig.SPIRE_STANDS_SECONDS));
        } catch (IllegalStateException notLoaded) {
            return 25;
        }
    }

    private static double radius() {
        try {
            return RimeConfig.SPIRE_RADIUS.get();
        } catch (IllegalStateException notLoaded) {
            return RADIUS;
        }
    }

    private static VfxQuality quality() {
        return KegareClientConfig.VFX_QUALITY.get();
    }

    /** How fast the warning crack races out to its ring: the whole circle is there early in the warning, to dodge. */
    static final int CRACK_SPREAD = 5;

    /**
     * The crack's time: it races out to the ring in the first part of the warning ({@link #CRACK_SPREAD}), holds through the rest
     * and while the spike stands, then melts.
     */
    static BlockMarks.Timing crackTiming(int warningLeft, int stand) {
        int spread = Math.max(3, Math.min(CRACK_SPREAD, warningLeft));
        return new BlockMarks.Timing(1f, 0, spread, Math.max(0, warningLeft - spread) + stand + 6, MELT_TICKS);
    }

    /** The shattered ground at the foot: out in a few ticks, further with more charge, there while the spike stands. */
    static BlockMarks.Timing burstTiming(float charge, int stand) {
        return new BlockMarks.Timing(Mth.lerp(Mth.clamp(charge, 0f, 1f), 0.55f, 1f), 0, 3, stand + 4, MELT_TICKS);
    }

    // ---------------------------------------------------------------------------------------------- per tick

    static void tick(ClientLevel level) {
        Set<Integer> seen = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof GlacialSpireEntity marker) || e.isRemoved()) continue;
            seen.add(e.getId());
            Spire s = SPIRES.computeIfAbsent(e.getId(), id -> new Spire());
            s.pos = marker.position();
            s.charge = marker.charge();
            ClientVisuals.LoopTime warning = ClientVisuals.loopTime(marker, RimeIds.SPIRE_WARNING_KEY, 0f);
            ClientVisuals.LoopTime spike = ClientVisuals.loopTime(marker, RimeIds.SPIRE_SPIKE_KEY, 0f);
            if (!s.cracked && warning != null && spike == null) {
                s.cracked = true;
                stamp(level, CRACK, s.pos, crackTiming(Math.max(0, warningTicks() - Math.round(warning.ticks())), standTicks()));
            }
            if (warning != null && spike == null) rumble(level, s.pos);
            if (!s.erupted && spike != null) {
                s.erupted = true;
                erupt(level, s);
            }
        }
        for (Iterator<Map.Entry<Integer, Spire>> it = SPIRES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Spire> e = it.next();
            if (seen.contains(e.getKey())) continue;
            if (e.getValue().erupted) shatter(level, e.getValue());
            it.remove();
        }
        gather(level);
    }

    private static void stamp(ClientLevel level, ResourceLocation name, Vec3 at, BlockMarks.Timing timing) {
        BlockMarks.Pattern p = BlockMarks.pattern(name);
        if (p != null) BlockMarks.stamp(level, p, at, Direction.UP, RANDOM.nextInt(8), timing);
    }

    private static BlockState ground(ClientLevel level, Vec3 at) {
        return level.getBlockState(BlockPos.containing(at.x, at.y - 0.05, at.z));
    }

    /** While it warns: now and then a chip of the ground jumps out of the crack. */
    private static void rumble(ClientLevel level, Vec3 at) {
        BlockState ground = ground(level, at);
        if (ground.isAir()) return;
        int n = quality() == VfxQuality.FULL ? 2 : 1;
        double r = radius();
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = Math.sqrt(RANDOM.nextDouble()) * r;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + Math.cos(a) * d, at.y + 0.05, at.z + Math.sin(a) * d,
                    0, 0.08 + RANDOM.nextDouble() * 0.1, 0);
        }
    }

    /** It erupts: the ground round its foot shatters and frosts, and its debris goes up with the spike. */
    private static void erupt(ClientLevel level, Spire s) {
        stamp(level, BURST, s.pos, burstTiming(s.charge, standTicks()));
        BlockState ground = ground(level, s.pos);
        if (ground.isAir()) return;
        int n = Math.round((16 + 18 * s.charge) * (quality() == VfxQuality.REDUCED ? 0.5f : 1f));
        double r = radius();
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = Math.sqrt(RANDOM.nextDouble()) * r;
            double out = 0.08 + RANDOM.nextDouble() * 0.12;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), s.pos.x + Math.cos(a) * d, s.pos.y + 0.1, s.pos.z + Math.sin(a) * d,
                    Math.cos(a) * out, 0.3 + RANDOM.nextDouble() * 0.35 * (0.6 + s.charge), Math.sin(a) * out);
        }
    }

    /** The marker's gone: the spike breaks into solid chunks of its own ice, thrown off it and falling. */
    private static void shatter(ClientLevel level, Spire s) {
        float height = SpireShape.height(s.charge) * 3.0f, width = SpireShape.width(s.charge);
        int n = Math.round(7 + 9 * s.charge);
        for (int i = 0; i < n; i++) {
            float up = 0.15f + RANDOM.nextFloat() * height;
            double a = RANDOM.nextDouble() * Math.PI * 2, d = RANDOM.nextDouble() * 0.45 * width;
            Vec3 at = s.pos.add(Math.cos(a) * d, up, Math.sin(a) * d);
            double out = 0.06 + RANDOM.nextDouble() * 0.12;
            Vec3 vel = new Vec3(Math.cos(a) * out, 0.05 + RANDOM.nextDouble() * 0.16, Math.sin(a) * out);
            float size = width * (0.75f + RANDOM.nextFloat() * 0.5f) * (up > height * 0.6f ? 0.7f : 1f);
            LanceAftermath.chunk(level, at, vel, size, LanceMesh.SPIRE, "chunk_" + RANDOM.nextInt(SpireShape.CHUNKS), 26 + RANDOM.nextInt(14));
        }
        Vec3 middle = s.pos.add(0, height * 0.45, 0);
        // Standing in it (launched and landing back): nothing soft in your face, the chunks are enough.
        if (!LanceAftermath.nearOwnCamera(middle, 2.5)) ClientVisuals.playAt(level, middle, RimeIds.FX_GLACIAL_SPIRE_SHATTER, 0, SpireShape.height(s.charge));
    }

    // ---------------------------------------------------------------------------------------------- the raised fist

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
        }
    }

    /** Remembers where a charging player's fist is drawn (others, and you in third person): the frost gathers there. */
    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (ClientVisuals.loopTime(player, CHARGE, partialTick) == null) return;
            boolean left = player.getMainArm() == HumanoidArm.LEFT;
            pose.pushPose();
            (left ? getParentModel().leftArm : getParentModel().rightArm).translateAndRotate(pose);
            pose.translate((left ? 1f : -1f) / 16f, 11f / 16f, 0f); // the fist, at the end of the arm
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vector3f p = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.last().pose())
                    .transformPosition(new Vector3f());
            pose.popPose();
            FISTS.put(player.getId(), new Fist(new Vec3(p.x, p.y, p.z), player.level().getGameTime()));
        }
    }

    private static void gather(ClientLevel level) {
        long now = level.getGameTime();
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Map.Entry<Integer, ClientVisuals.PositionalEffect>> it = GATHER.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, ClientVisuals.PositionalEffect> e = it.next();
            Fist fist = FISTS.get(e.getKey());
            if (fist == null || now - fist.frame() > 1) {
                e.getValue().stop(false);
                it.remove();
            }
        }
        for (AbstractClientPlayer player : level.players()) {
            if (ClientVisuals.loopTime(player, CHARGE, 0f) == null) continue;
            squareUp(player);
            Fist fist = FISTS.get(player.getId());
            boolean self = player == mc.player && mc.options.getCameraType().isFirstPerson();
            if (fist == null || now - fist.frame() > 1 || self) continue;
            if (quality() == VfxQuality.VANILLA) {
                if (RANDOM.nextInt(3) == 0) level.addParticle(ParticleTypes.SNOWFLAKE, fist.at().x, fist.at().y, fist.at().z, 0, -0.02, 0);
                continue;
            }
            ClientVisuals.PositionalEffect effect = GATHER.get(player.getId());
            if (effect == null) {
                effect = ClientVisuals.startAt(RimeIds.FX_GLACIAL_SPIRE_CHARGE, fist.at());
                if (effect == null) continue;
                GATHER.put(player.getId(), effect);
            }
            effect.moveTo(fist.at());
        }
        FISTS.entrySet().removeIf(e -> now - e.getValue().frame() > 40);
    }

    /** Charging, the body turns to face the aim (where the free hand points), quickly but not in a snap. Only how it's drawn. */
    private static void squareUp(AbstractClientPlayer player) {
        float off = Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot);
        player.yBodyRot += Mth.clamp(off, -20f, 20f);
    }

    static void clear() {
        SPIRES.clear();
        FISTS.clear();
        GATHER.values().forEach(e -> e.stop(true));
        GATHER.clear();
    }
}
