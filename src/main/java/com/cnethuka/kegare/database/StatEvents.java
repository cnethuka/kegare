package com.cnethuka.kegare.database;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.fists.BlackSpark;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.FightTracker;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

/**
 * What goes in {@code stat_event} (DESIGN §3.33): fights, kills and deaths, damage dealt and taken, Black Sparks and chains, and Kegare advancements earned.
 * Written off the server thread, append-only; losing some in an outage is fine, as these aren't player data. Grade, Assessment, Refinement, Insight, unlocks and
 * traits never go in here at all.
 */
public final class StatEvents {
    public static final String PVP_KILL = "pvp_kill", DEATH = "death", FIGHT = "fight", DAMAGE_DEALT = "damage_dealt", DAMAGE_TAKEN = "damage_taken", BLACK_SPARK = "black_spark", ADVANCEMENT = "advancement";

    private StatEvents() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StatEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(StatEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(StatEvents::onAdvancement);
        FightTracker.addListener(report -> record(report.player(), FIGHT, 1, null, false));
        BlackSpark.listen((attacker, target, chain) -> record(attacker, BLACK_SPARK, chain, null, false));
    }

    private static void record(ServerPlayer player, String kind, double amount, String detail, boolean announced) {
        KegareDatabase database = KegareDatabase.current();
        if (database != null && !(player instanceof FakePlayer)) database.recordStat(player.getUUID(), kind, amount, detail, announced);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        record(victim, DEATH, 1, null, false);
        Attribution.Hit blow = Attribution.of(event.getSource(), victim.server);
        // Knocked off a ledge, the last blow's the fall: the kill's still whoever hit them.
        ServerPlayer killer = blow != null ? blow.attacker() : victim.getKillCredit() instanceof ServerPlayer credited ? credited : null;
        if (killer != null && killer != victim) record(killer, PVP_KILL, 1, null, false);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        float amount = event.getNewDamage();
        if (amount <= 0 || !(event.getEntity() instanceof ServerPlayer victim)) return;
        record(victim, DAMAGE_TAKEN, amount, null, false);
        Attribution.Hit hit = Attribution.of(event.getSource(), victim.server);
        if (hit != null && hit.attacker() != victim) record(hit.attacker(), DAMAGE_DEALT, amount, null, false); // to other players: a farm isn't a leaderboard
    }

    private static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) advancement(player, event.getAdvancement());
    }

    /**
     * A Kegare advancement earned. Recorded with whether it was announced: a hidden one that was announced is public once it's been earned, as chat
     * has already said so; a silent one (the Six Eyes tab, and anything else silent) never is.
     */
    public static void advancement(ServerPlayer player, AdvancementHolder holder) {
        if (!holder.id().getNamespace().equals(Kegare.MODID) || holder.value().display().isEmpty()) return;
        boolean announced = holder.value().display().map(DisplayInfo::shouldAnnounceChat).orElse(false);
        record(player, ADVANCEMENT, 1, holder.id().toString(), announced);
    }
}
