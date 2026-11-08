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

    private Senses() {}

    static void tick(ServerPlayer player) {
        if (player.tickCount % EVERY != 0) return;
        List<Integer> ids = Severed.is(player) && player.isAlive() && !player.isSpectator() ? ids(player) : List.of();
        List<Integer> was = SENT.get(player.getUUID());
        if (ids.equals(was) || (ids.isEmpty() && was == null)) return;
        if (ids.isEmpty()) SENT.remove(player.getUUID());
        else SENT.put(player.getUUID(), ids);
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new SensesPayload(ids));
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
    }

    static void clear() {
        SENT.clear();
    }
}
