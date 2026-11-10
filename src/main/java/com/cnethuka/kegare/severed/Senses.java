package com.cnethuka.kegare.severed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A Severed player's senses (DESIGN §3.27): every player and mob within {@code [severed.senses] range}, through walls, still or
 * moving, invisible ones too: heard, smelled and felt in the air, not seen. The server decides who that is a few times a second
 * and tells the Severed player alone ({@link SensesPayload}); their client draws a faint outline for each. Never sent to anyone
 * else, and an empty set once someone stops being Severed.
 */
public final class Senses {
    /** How often the set is worked out again: four times a second. */
    static final int EVERY = 5;
    private static final Map<UUID, List<Integer>> SENT = new HashMap<>();
    /** Who's been sent real positions (too fast for the eye), so an empty set goes once when that stops. */
    private static final java.util.Set<UUID> SENT_POSITIONS = new java.util.HashSet<>();

    private Senses() {}

    static void tick(ServerPlayer player) {
        positions(player);
        if (player.tickCount % EVERY != 0) return;
        List<Integer> ids = Severed.is(player) && player.isAlive() && !player.isSpectator() ? ids(player) : List.of();
        List<Integer> was = SENT.get(player.getUUID());
        if (ids.equals(was) || (ids.isEmpty() && was == null)) return;
        if (ids.isEmpty()) SENT.remove(player.getUUID());
        else SENT.put(player.getUUID(), ids);
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new SensesPayload(ids));
    }

    /**
     * Every tick: the real positions of sensed entities that the eye sees behind where they are (DESIGN §3.30). Sensing isn't seeing, so
     * the outline goes where they really are; the body itself they see where everyone does.
     */
    private static void positions(ServerPlayer player) {
        List<Integer> ids = SENT.get(player.getUUID());
        List<SensedPositionsPayload.Entry> entries = ids == null ? List.of() : realPositions(player, ids);
        if (entries.isEmpty() && !SENT_POSITIONS.remove(player.getUUID())) return;
        if (!entries.isEmpty()) SENT_POSITIONS.add(player.getUUID());
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new SensedPositionsPayload(entries));
    }

    /** Of these sensed entities, the real positions of the ones eyes see behind where they are (DESIGN §3.30). Public for the tests. */
    public static List<SensedPositionsPayload.Entry> realPositions(ServerPlayer player, List<Integer> ids) {
        List<SensedPositionsPayload.Entry> entries = new ArrayList<>();
        for (int id : ids) {
            net.minecraft.world.entity.Entity e = player.serverLevel().getEntity(id);
            if (e != null && com.cnethuka.kegare.sight.DelayedSight.hidden(e) && !com.cnethuka.kegare.sight.DelayedSight.seesReal(player, e)) {
                entries.add(new SensedPositionsPayload.Entry(id, e.position()));
            }
        }
        return entries;
    }

    /** Who a Severed player senses right now, nearest first. Nothing for anyone else. Package-visible for the tests. */
    static List<LivingEntity> sensed(ServerPlayer player) {
        if (!Severed.is(player)) return List.of();
        double range = SeveredConfig.SENSES_RANGE.get();
        if (range <= 0) return List.of();
        List<LivingEntity> out = new ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && !(e instanceof Player p && p.isSpectator())
                        && e.distanceToSqr(player) <= range * range));
        out.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        return out;
    }

    private static List<Integer> ids(ServerPlayer player) {
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : sensed(player)) ids.add(e.getId());
        ids.sort(Integer::compare);
        return List.copyOf(ids);
    }

    static void forget(UUID player) {
        SENT.remove(player);
        SENT_POSITIONS.remove(player);
    }

    static void clear() {
        SENT.clear();
        SENT_POSITIONS.clear();
    }
}
