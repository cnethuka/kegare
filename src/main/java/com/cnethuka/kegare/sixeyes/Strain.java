package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.backgroundtask.BackgroundRmt;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.rmt.ReverseMaliceTechnique;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Strain, the eyes' fatigue (DESIGN §3.32), private to the bearer. It builds in a fight: fastest uncovered, slower with glasses, slowest with
 * a blindfold; Background RMT holds it back. It falls out of a fight, fastest covered; channelling RMT lowers it. Eyewear never touches what
 * the eyes see or how little they waste.
 * <p>
 * At the top: severe brain fatigue. A throbbing headache (client: a pulse at the edges of the screen and a heartbeat, nothing in the middle
 * of the view), the uncovered edge gone until Strain cools below {@code edgeBackBelow}, and, staying uncovered there, a growing risk of
 * collapse: a short stun (no moving or casting) and damage armour doesn't stop. Covering the eyes stops the risk growing; channelling RMT
 * lowers it.
 */
public final class Strain {
    public static final double MAX = 100.0;
    public static final ResourceKey<DamageType> COLLAPSE = ResourceKey.create(Registries.DAMAGE_TYPE, Kegare.id("eyes_collapse"));
    private static final ResourceLocation HOLD_MOVE = Kegare.id("six_eyes_collapse");

    /** Background RMT told us it ran on this tick (the §3.31 hook). */
    private static final Map<UUID, Long> HELD_BACK = new HashMap<>();
    private static final Map<UUID, Snapshot> SENT = new HashMap<>();
    /** Set by tests: the next roll for a collapse. */
    static java.util.function.DoubleSupplier roll = Math::random;

    private record Snapshot(int strain, boolean top, boolean edge, int eyewear) {}

    private Strain() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            for (ServerPlayer p : SixEyes.bearers(e.getServer())) {
                tick(p);
                Attunement.tick(p);
            }
            releaseHolds(e.getServer());
        });
        BackgroundRmt.addHook(p -> HELD_BACK.put(p.getUUID(), p.level().getGameTime()));
        ReverseMaliceTechnique.addChannelListener(Strain::channelling);
    }

    public static SixEyesData data(ServerPlayer player) {
        return player.getData(KegareAttachments.SIX_EYES);
    }

    /** One tick for a bearer. */
    static void tick(ServerPlayer player) {
        SixEyesData d = data(player);
        Eyewear wear = Eyewear.of(player);
        if (CombatHelper.isInCombat(player)) {
            double perSecond = SixEyesConfig.STRAIN_BUILD.get();
            if (wear == Eyewear.SUNGLASSES) perSecond *= SixEyesConfig.SUNGLASSES_BUILD.get();
            if (wear == Eyewear.BLINDFOLD) perSecond *= SixEyesConfig.BLINDFOLD_BUILD.get();
            if (heldBack(player)) perSecond *= SixEyesConfig.BACKGROUND_RMT_BUILD.get();
            d.strain = Math.min(MAX, d.strain + perSecond / 20.0);
        } else {
            double perSecond = wear.covered() ? SixEyesConfig.STRAIN_FALL_COVERED.get() : SixEyesConfig.STRAIN_FALL.get();
            d.strain = Math.max(0, d.strain - perSecond / 20.0);
        }
        settle(player, d, wear);
    }

    /** What follows from where Strain stands: the edge, and the risk. */
    private static void settle(ServerPlayer player, SixEyesData d, Eyewear wear) {
        boolean top = d.strain >= MAX;
        if (top) d.edgeLost = true;
        else if (d.strain < SixEyesConfig.EDGE_BACK_BELOW.get()) d.edgeLost = false;
        if (!top) {
            d.risk = 0;
        } else if (!wear.covered()) {
            d.risk = Math.min(1.0, d.risk + SixEyesConfig.RISK_GROWTH.get() / 20.0);
            if (player.level().getGameTime() % 20 == 0 && roll.getAsDouble() < d.risk) collapse(player, d);
        }
        send(player, d, wear);
    }

    private static boolean heldBack(ServerPlayer player) {
        Long at = HELD_BACK.get(player.getUUID());
        return at != null && player.level().getGameTime() - at <= 1;
    }

    /** Channelling RMT refreshes the brain. */
    private static void channelling(ServerPlayer player) {
        if (!SixEyes.isBearer(player)) return;
        SixEyesData d = data(player);
        d.strain = Math.max(0, d.strain - SixEyesConfig.RMT_STRAIN_RELIEF.get() / 20.0);
        d.risk = Math.max(0, d.risk - SixEyesConfig.RMT_RISK_RELIEF.get() / 20.0);
    }

    /** Central nervous system overload: a short stun, no moving or casting, and damage armour doesn't stop. The risk starts over. */
    static void collapse(ServerPlayer player, SixEyesData d) {
        d.risk = 0;
        int ticks = (int) Math.round(SixEyesConfig.COLLAPSE_STUN.get() * 20);
        CombatHelper.stun(player, ticks);
        hold(player, Attributes.MOVEMENT_SPEED);
        hold(player, Attributes.JUMP_STRENGTH);
        float damage = SixEyesConfig.COLLAPSE_DAMAGE.get().floatValue();
        if (damage > 0) {
            int invulnerable = player.invulnerableTime;
            player.hurt(source(player), damage);
            player.invulnerableTime = invulnerable;
        }
        PacketDistributor.sendToPlayer(player, new Collapsed(ticks));
    }

    static DamageSource source(ServerPlayer player) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(COLLAPSE));
    }

    private static void hold(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.addOrUpdateTransientModifier(new AttributeModifier(HOLD_MOVE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** The stun over: they can move again. Everyone is checked, since the eyes may have gone while they lay there. */
    private static void releaseHolds(MinecraftServer server) {
        for (ServerPlayer p : SixEyes.everyone(server)) release(p);
    }

    static void release(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(HOLD_MOVE) == null || CombatHelper.isStunned(player)) return;
        speed.removeModifier(HOLD_MOVE);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(HOLD_MOVE);
    }

    public static boolean moveHeld(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(HOLD_MOVE) != null;
    }

    /** Is the uncovered edge there? Only uncovered, and not lost at the top. */
    public static boolean edgeAvailable(ServerPlayer player) {
        return !Eyewear.of(player).covered() && !data(player).edgeLost;
    }

    /** Admin and tests: sets Strain, and what follows from it. */
    public static void set(ServerPlayer player, double value) {
        SixEyesData d = data(player);
        d.strain = Math.max(0, Math.min(MAX, value));
        if (d.strain >= MAX) d.edgeLost = true;
        else if (d.strain < SixEyesConfig.EDGE_BACK_BELOW.get()) d.edgeLost = false;
        if (d.strain < MAX) d.risk = 0;
        send(player, d, Eyewear.of(player));
    }

    /** The bearer's own HUD: sent to them alone, when what it shows changes. */
    private static void send(ServerPlayer player, SixEyesData d, Eyewear wear) {
        Snapshot now = new Snapshot((int) Math.round(d.strain), d.strain >= MAX, !wear.covered() && !d.edgeLost, wear.ordinal());
        if (now.equals(SENT.get(player.getUUID()))) return;
        SENT.put(player.getUUID(), now);
        PacketDistributor.sendToPlayer(player, new Hud(now.strain(), now.top(), now.edge(), now.eyewear()));
    }

    /** They've lost the eyes (or logged out): their HUD goes. */
    static void forget(ServerPlayer player) {
        if (SENT.remove(player.getUUID()) != null) PacketDistributor.sendToPlayer(player, new Hud(-1, false, false, 0));
        HELD_BACK.remove(player.getUUID());
    }

    static void forget(UUID player) {
        SENT.remove(player);
        HELD_BACK.remove(player);
    }

    static void clear() {
        SENT.clear();
        HELD_BACK.clear();
    }

    /** Server to the bearer: their Strain meter. {@code strain} -1: no eyes, no meter. */
    public record Hud(int strain, boolean top, boolean edge, int eyewear) implements CustomPacketPayload {
        public static final Type<Hud> TYPE = new Type<>(Kegare.id("six_eyes_strain"));
        public static final StreamCodec<FriendlyByteBuf, Hud> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, Hud::strain, ByteBufCodecs.BOOL, Hud::top, ByteBufCodecs.BOOL, Hud::edge, ByteBufCodecs.VAR_INT, Hud::eyewear, Hud::new);
        public static Consumer<Hud> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Hud p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }

    /** Server to the bearer: a collapse (the screen goes for a moment). */
    public record Collapsed(int ticks) implements CustomPacketPayload {
        public static final Type<Collapsed> TYPE = new Type<>(Kegare.id("six_eyes_collapse"));
        public static final StreamCodec<FriendlyByteBuf, Collapsed> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Collapsed::ticks, Collapsed::new);
        public static Consumer<Collapsed> clientHandler;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Collapsed p, IPayloadContext context) {
            if (clientHandler != null) context.enqueueWork(() -> clientHandler.accept(p));
        }
    }
}
