package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.battledamage.BattleDamage;
import com.cnethuka.kegare.battledamage.BattleDamageConfig;
import com.cnethuka.kegare.melee.MeleeHits;
import com.cnethuka.kegare.melee.ReinforcedStrike;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.FightTracker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Reinforced Fists' and Black Spark's advancements (DESIGN §3.28): Base tab, hidden, announced in chat, Challenge, players only, awarded
 * from here. None needs a kill or a win.
 * <ul>
 *   <li>{@code locked_in}: three Reinforced Strikes in a row on players, no plain melee hit on a player between them, in one fight.
 *       Anyone (Severed too).</li>
 *   <li>{@code 120_percent}, {@code nothing_to_celebrate}, {@code blessed_by_the_sparks}: a chain of 2, 4 and 7 Black Sparks on
 *       players (§3.28's chain; sparks on mobs don't count, and it ends with the zone).</li>
 *   <li>{@code lights_out}: a Black Spark sends a player into a block hard enough for a wall-slam crater (§3.24), whether or not
 *       battle damage is on. Anyone.</li>
 * </ul>
 */
public final class FistsAdvancements {
    public static final ResourceLocation LOCKED_IN = Kegare.id("locked_in");
    public static final ResourceLocation ONE_TWENTY_PERCENT = Kegare.id("120_percent");
    public static final ResourceLocation NOTHING_TO_CELEBRATE = Kegare.id("nothing_to_celebrate");
    public static final ResourceLocation BLESSED_BY_THE_SPARKS = Kegare.id("blessed_by_the_sparks");
    public static final ResourceLocation LIGHTS_OUT = Kegare.id("lights_out");
    public static final int LOCKED_IN_STRIKES = 3;
    public static final int CHAIN_120 = 2, CHAIN_NOTHING_TO_CELEBRATE = 4, CHAIN_BLESSED = 7;

    private record Mark(UUID attacker, long tick) {}

    /** Strikes in a row on players, per attacker. */
    private static final Map<UUID, Integer> STREAK = new HashMap<>();
    /** The Strike that just landed, per attacker: its target and tick (so the damage it's part of isn't taken for a plain hit). */
    private static final Map<UUID, Mark> STRIKING = new HashMap<>();
    /** The last Black Spark on each player: who, and when (a slam within the window after it is its doing). */
    private static final Map<UUID, Mark> SPARKED = new HashMap<>();

    private FistsAdvancements() {}

    static void init() {
        ReinforcedStrike.addLandedListener((attacker, target, damage) -> onStrike(attacker, target));
        BlackSpark.listen(FistsAdvancements::onSpark);
        BattleDamage.addSlamListener(FistsAdvancements::onSlam);
        FightTracker.addListener(report -> STREAK.remove(report.player().getUUID())); // one fight
    }

    static void award(ServerPlayer player, ResourceLocation id) {
        KegareAdvancements.award(player, id);
    }

    private static void onStrike(ServerPlayer attacker, LivingEntity target) {
        if (!(target instanceof ServerPlayer) || !Attribution.isRealTarget(attacker, target)) return;
        STRIKING.put(attacker.getUUID(), new Mark(target.getUUID(), attacker.level().getGameTime()));
        int streak = STREAK.merge(attacker.getUUID(), 1, Integer::sum);
        if (streak >= LOCKED_IN_STRIKES) award(attacker, LOCKED_IN);
    }

    /** A melee hit on a player that wasn't a Strike breaks the streak. */
    static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (!(victim.level() instanceof ServerLevel level) || MeleeHits.of(event.getSource(), level) != MeleeHits.Kind.MELEE) return;
        Mark strike = STRIKING.remove(attacker.getUUID()); // the Strike marks the damage it's part of, and only that
        if (strike != null && strike.attacker().equals(victim.getUUID()) && strike.tick() == level.getGameTime()) return; // the Strike itself
        STREAK.remove(attacker.getUUID());
    }

    private static void onSpark(ServerPlayer attacker, LivingEntity target, int chain) {
        if (!(target instanceof ServerPlayer)) return; // players only
        SPARKED.put(target.getUUID(), new Mark(attacker.getUUID(), attacker.level().getGameTime()));
        int onPlayers = Zone.playerChain(attacker);
        if (onPlayers >= CHAIN_120) award(attacker, ONE_TWENTY_PERCENT);
        if (onPlayers >= CHAIN_NOTHING_TO_CELEBRATE) award(attacker, NOTHING_TO_CELEBRATE);
        if (onPlayers >= CHAIN_BLESSED) award(attacker, BLESSED_BY_THE_SPARKS);
    }

    private static void onSlam(LivingEntity victim, @Nullable Entity attacker, double speed) {
        if (!(victim instanceof ServerPlayer) || !(attacker instanceof ServerPlayer player)) return;
        Mark spark = SPARKED.get(victim.getUUID());
        long window = Math.round(BattleDamageConfig.SLAM_WINDOW.get() * 20);
        if (spark == null || !spark.attacker().equals(player.getUUID()) || player.level().getGameTime() - spark.tick() > window) return;
        SPARKED.remove(victim.getUUID());
        award(player, LIGHTS_OUT);
    }

    static void forget(UUID player) {
        STREAK.remove(player);
        STRIKING.remove(player);
        SPARKED.remove(player);
    }

    public static void clear() {
        STREAK.clear();
        STRIKING.clear();
        SPARKED.clear();
    }
}
