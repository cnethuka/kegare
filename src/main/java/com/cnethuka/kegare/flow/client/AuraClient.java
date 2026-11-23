package com.cnethuka.kegare.flow.client;

import com.cnethuka.kegare.api.visual.Visual;
import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareClientConfig.VfxQuality;
import com.cnethuka.kegare.flow.AuraPayload;
import com.cnethuka.kegare.flow.FlowIds;
import com.cnethuka.kegare.melee.MeleeTell;
import com.cnethuka.kegare.rmt.RmtIds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Flow's aura (DESIGN §3.16) on every player who has it, as this client should see it: a Photon loop on the body
 * ({@link FlowFxAuthoring}), one flame standing behind the player and drawn up to a point over the head.
 * <ul>
 * <li>Fuller and faster in a fight ({@link AuraPayload#FIGHT}). Background Flow alone ({@link AuraPayload#CALM}): the same, much calmer
 * and fainter.</li>
 * <li>Your own first person: only a few flames licking up at your sides, never round your head.</li>
 * <li>Reduced: half the flames (Photon's own reduction). Vanilla: dust in the Malice color rising round the body (none round your own in
 * first person). An invisible player shows nothing.</li>
 * </ul>
 * The color is what the server sends this viewer ({@code AuraTell}): under a disguise, the face's. Background RMT's shimmer rides the
 * same message and is played here too.
 */
public final class AuraClient {
    /** Background RMT's shimmer, seen by everyone: RMT's white light, a Photon loop on the player (DESIGN §3.31). */
    private static final String SHIMMER_LOOP = "background_rmt_shimmer";
    /** Flow's aura, a Photon loop on the player. */
    private static final String AURA_LOOP = "flow_aura";

    private static final RandomSource RANDOM = RandomSource.create();

    /** A player's aura as this client knows it. */
    private static final class Entry {
        AuraPayload payload;
        int shimmerClock;
        /** What's playing now (null: nothing), so the loop restarts only when what should show changes. */
        @Nullable ResourceLocation playing;
        int playingTint;
        VfxQuality playingQuality;

        Entry(AuraPayload payload) {
            this.payload = payload;
        }

        boolean aura() {
            return payload.has(AuraPayload.FULL) || payload.has(AuraPayload.CALM);
        }

        boolean calm() {
            return !payload.has(AuraPayload.FULL) && payload.has(AuraPayload.CALM);
        }

        boolean shimmer() {
            return payload.has(AuraPayload.SHIMMER);
        }
    }

    private static final Map<Integer, Entry> SHOWN = new HashMap<>();

    private AuraClient() {}

    public static void init(IEventBus modBus) {
        AuraPayload.setClientHandler(AuraClient::update);
        ClientVisuals.turnWithBody(FlowIds.FX_AURA_SELF, FlowFxAuthoring.SELF_AT);
        ClientVisuals.turnWithBody(FlowIds.FX_AURA_CALM_SELF, FlowFxAuthoring.SELF_AT);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> SHOWN.clear());
    }

    static void update(AuraPayload payload) {
        if (payload.flags() == 0) {
            SHOWN.remove(payload.entityId());
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                ClientVisuals.stopLoop(level, payload.entityId(), SHIMMER_LOOP);
                ClientVisuals.stopLoop(level, payload.entityId(), AURA_LOOP);
            }
            return;
        }
        Entry entry = SHOWN.get(payload.entityId());
        if (entry == null) SHOWN.put(payload.entityId(), new Entry(payload));
        else entry.payload = payload;
    }

    /** What's shown on this player for you (null if nothing, or you can't see them). */
    @Nullable
    public static AuraPayload on(Player player) {
        Entry entry = SHOWN.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        if (entry == null || mc.player == null || player.isInvisibleTo(mc.player)) return null;
        return entry.payload;
    }

    static VfxQuality quality() {
        return KegareClientConfig.VFX_QUALITY.get();
    }

    private static boolean firstPerson(Minecraft mc, Player player) {
        return player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        for (Map.Entry<Integer, Entry> e : SHOWN.entrySet()) {
            Entry entry = e.getValue();
            if (!(level.getEntity(e.getKey()) instanceof Player player)) continue;
            boolean visible = player.isAlive() && on(player) != null;
            boolean mine = firstPerson(mc, player);
            shimmer(level, player, entry, visible && entry.shimmer() && !mine);
            aura(level, player, entry, visible && entry.aura(), mine);
        }
    }

    /** Which of the aura's effects shows on this player now. */
    static ResourceLocation effect(AuraPayload payload, boolean mine) {
        boolean calm = !payload.has(AuraPayload.FULL) && payload.has(AuraPayload.CALM);
        if (calm) return mine ? FlowIds.FX_AURA_CALM_SELF : FlowIds.FX_AURA_CALM;
        if (mine) return FlowIds.FX_AURA_SELF;
        return payload.has(AuraPayload.FIGHT) ? FlowIds.FX_AURA_FIGHT : FlowIds.FX_AURA;
    }

    /** Keeps the right loop playing on them, in the color they're shown in; Vanilla: dust instead. */
    private static void aura(ClientLevel level, Player player, Entry entry, boolean shown, boolean mine) {
        VfxQuality quality = quality();
        boolean vanilla = quality == VfxQuality.VANILLA;
        ResourceLocation fx = shown && !vanilla ? effect(entry.payload, mine) : null;
        boolean playing = ClientVisuals.loopTime(player, AURA_LOOP, 0f) != null;
        if (fx == null) {
            if (playing) ClientVisuals.stopLoop(level, player.getId(), AURA_LOOP);
            entry.playing = null;
        } else {
            int tint = 0xFF000000 | MeleeTell.bright(entry.payload.color()) & 0xFFFFFF;
            if (!playing || !fx.equals(entry.playing) || tint != entry.playingTint || quality != entry.playingQuality) {
                ClientVisuals.startLoop(player, AURA_LOOP, Visual.effect(fx, Visual.Anchor.BODY).tinted(tint), 0);
                entry.playing = fx;
                entry.playingTint = tint;
                entry.playingQuality = quality;
            }
        }
        if (shown && vanilla && !mine) dust(level, player, entry, MeleeTell.bright(entry.payload.color()) & 0xFFFFFF);
    }

    /**
     * Background RMT, as everyone sees it (DESIGN §3.31): RMT's white licks coming and going over the body, a Photon loop on them
     * (Reduced: half as many). Vanilla: a few specks of white dust instead. Not on your own body in your own first person.
     */
    private static void shimmer(ClientLevel level, Player player, Entry entry, boolean shown) {
        boolean vanilla = quality() == VfxQuality.VANILLA;
        boolean playing = ClientVisuals.loopTime(player, SHIMMER_LOOP, 0f) != null;
        if (shown && !vanilla && !playing) {
            ClientVisuals.startLoop(player, SHIMMER_LOOP, Visual.effect(RmtIds.FX_BACKGROUND_SHIMMER, Visual.Anchor.BODY), 0);
        } else if ((!shown || vanilla) && playing) {
            ClientVisuals.stopLoop(level, player.getId(), SHIMMER_LOOP);
        }
        if (!shown || !vanilla || ++entry.shimmerClock < 4) return;
        entry.shimmerClock = 0;
        double w = player.getBbWidth() * 0.6;
        double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 2 * w, z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 2 * w;
        double y = player.getY() + 0.1 + RANDOM.nextDouble() * player.getBbHeight();
        level.addParticle(new DustParticleOptions(new Vector3f(1f, 1f, 1f), 0.5f), x, y, z, 0, 0.2, 0);
    }

    /** Vanilla: dust in the Malice color rising round the body. */
    private static void dust(ClientLevel level, Player player, Entry entry, int rgb) {
        int count = entry.calm() ? (RANDOM.nextInt(3) == 0 ? 1 : 0) : entry.payload.has(AuraPayload.FIGHT) ? 3 : 2;
        Vector3f color = new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        DustParticleOptions dust = new DustParticleOptions(color, entry.calm() ? 0.7f : 1.0f);
        double w = player.getBbWidth() * 0.5 + 0.2;
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            level.addParticle(dust, player.getX() + Math.cos(a) * w, player.getY() + RANDOM.nextDouble() * player.getBbHeight() * 1.1,
                    player.getZ() + Math.sin(a) * w, 0, 0.6, 0);
        }
    }
}
