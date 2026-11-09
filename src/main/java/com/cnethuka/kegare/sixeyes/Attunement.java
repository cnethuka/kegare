package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Attunement (DESIGN §3.32): how well a bearer has grown into the eyes. It grows by using them: fighting while holding them
 * ({@code perFightSecond}), full-detail Reads of someone ({@code perRead}, each player at most once a minute) and pins ({@code perPin}, each
 * one at most once a minute). Saved; fully reset when the eyes pass on or are lost, so a new bearer starts out clumsy. Private: only the
 * Sorcerer screen shows it. Perception never grows: this moves only efficiency and RMT output ({@link Efficiency}).
 */
public final class Attunement {
    /** A Read or pin of the same one counts again after this long. */
    static final long REPEAT_TICKS = 20 * 60;
    /** bearer -> target -> tick it last counted: reads and pins each. */
    private static final Map<UUID, Map<Integer, Long>> READS = new HashMap<>(), PINS = new HashMap<>();

    private Attunement() {}

    static void init() {
        Reads.addFocusListener((bearer, target) -> {
            if (!com.cnethuka.kegare.malice.MaliceService.isSevered(target) && fresh(READS, bearer, target.getId())) {
                add(bearer, SixEyesConfig.ATTUNEMENT_READ.get());
            }
        });
    }

    public static double get(ServerPlayer player) {
        return Strain.data(player).attunement();
    }

    public static void add(ServerPlayer player, double amount) {
        SixEyesData d = Strain.data(player);
        long before = Math.round(d.attunement);
        d.attunement = Math.max(0, d.attunement + amount);
        if (Math.round(d.attunement) != before) com.cnethuka.kegare.network.PlayerSync.markDirty(player);
    }

    /** Admin and tests. */
    public static void set(ServerPlayer player, double value) {
        Strain.data(player).attunement = Math.max(0, value);
        com.cnethuka.kegare.network.PlayerSync.markDirty(player);
    }

    /** Every tick for a bearer: a fight counts by the second. */
    static void tick(ServerPlayer player) {
        if (CombatHelper.isInCombat(player)) add(player, SixEyesConfig.ATTUNEMENT_FIGHT.get() / 20.0);
    }

    private static boolean fresh(Map<UUID, Map<Integer, Long>> counted, ServerPlayer bearer, int target) {
        long now = bearer.level().getGameTime();
        Map<Integer, Long> mine = counted.computeIfAbsent(bearer.getUUID(), k -> new HashMap<>());
        Long last = mine.get(target);
        if (last != null && now - last < REPEAT_TICKS) return false;
        mine.put(target, now);
        return true;
    }

    /** A pin the client says it made: only a bearer, only something living in their own dimension. */
    static void pinned(ServerPlayer bearer, int entityId) {
        if (!SixEyes.isBearer(bearer)) return;
        Entity target = bearer.serverLevel().getEntity(entityId);
        if (target instanceof LivingEntity && target != bearer && fresh(PINS, bearer, entityId)) add(bearer, SixEyesConfig.ATTUNEMENT_PIN.get());
    }

    static void forget(UUID player) {
        READS.remove(player);
        PINS.remove(player);
    }

    static void clear() {
        READS.clear();
        PINS.clear();
    }

    /** Client to server: the bearer pinned something on the map. */
    public record PinPayload(int entityId) implements CustomPacketPayload {
        public static final Type<PinPayload> TYPE = new Type<>(Kegare.id("six_eyes_pin"));
        public static final StreamCodec<FriendlyByteBuf, PinPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, PinPayload::entityId, PinPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(PinPayload p, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) pinned(player, p.entityId());
        }
    }
}
