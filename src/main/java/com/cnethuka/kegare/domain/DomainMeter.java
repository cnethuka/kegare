package com.cnethuka.kegare.domain;

import com.cnethuka.kegare.api.domain.DomainExpansion;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.FightTracker;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Domain Meter (DESIGN §3.35): 0 to 100 per player, private to its owner. It fills from damage dealt to and taken from players in
 * fights (§3.21's damage record: after defences, a single hit capped at the victim's max health), and drains slowly out of combat. A full
 * Domain takes it all; an Incomplete Domain part of it. Saved and kept on death. Its owner sees it on the HUD with the melee cues, only
 * while they have a Domain to open with it.
 */
public final class DomainMeter {
    /** What makes the meter worth showing to a player: they have something that opens a Domain. */
    private static final List<Predicate<ServerPlayer>> SHOWN = new CopyOnWriteArrayList<>();

    private DomainMeter() {}

    public static void init() {
        FightTracker.addDamageListener((attacker, victim, amount) -> {
            add(attacker, amount * DomainConfig.METER_PER_DAMAGE_DEALT.get());
            add(victim, amount * DomainConfig.METER_PER_DAMAGE_TAKEN.get());
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0) tick(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                if (p.hasData(DomainRegistry.OLD_METER)) { // saved before Dominions became Domains
                    set(p, Math.max(get(p), p.getData(DomainRegistry.OLD_METER).meter()));
                    p.removeData(DomainRegistry.OLD_METER);
                }
                data(p).sent = -1;
                sync(p);
            }
        });
        // A technique whose ultimate is a Domain Expansion makes the meter worth showing, once its ultimate is unlocked.
        addShownWhen(p -> InnateTechniqueService.current(p).flatMap(t -> t.ultimate()).filter(u -> u instanceof DomainExpansion)
                .filter(u -> com.cnethuka.kegare.progression.TechniqueUnlocks.isUnlocked(p, u)).isPresent());
    }

    public static void addShownWhen(Predicate<ServerPlayer> rule) {
        SHOWN.add(rule);
    }

    static DomainData data(Player player) {
        return player.getData(DomainRegistry.METER.get());
    }

    public static double get(Player player) {
        return data(player).meter;
    }

    public static void set(ServerPlayer player, double meter) {
        data(player).meter = Math.max(0, Math.min(DomainData.MAX, meter));
        sync(player);
    }

    public static void add(ServerPlayer player, double amount) {
        if (amount <= 0 || MaliceService.isSevered(player)) return; // nothing to open a Domain with
        set(player, get(player) + amount);
    }

    static void take(ServerPlayer player, double amount) {
        set(player, get(player) - amount);
    }

    /** Once a second: drains out of combat, and keeps the owner's HUD up to date. */
    static void tick(ServerPlayer player) {
        if (!CombatHelper.isInCombat(player) && get(player) > 0) {
            data(player).meter = Math.max(0, get(player) - DomainConfig.METER_DRAIN.get());
        }
        sync(player);
    }

    static boolean shown(ServerPlayer player) {
        if (MaliceService.isSevered(player)) return false;
        for (Predicate<ServerPlayer> rule : SHOWN) {
            if (rule.test(player)) return true;
        }
        return false;
    }

    /** Sends the owner their meter when its whole number, or whether it's shown, has changed. */
    static void sync(ServerPlayer player) {
        DomainData data = data(player);
        int whole = (int) Math.floor(data.meter);
        boolean shown = shown(player);
        if (whole == data.sent && shown == data.sentShown) return;
        data.sent = whole;
        data.sentShown = shown;
        if (player.connection != null) PacketDistributor.sendToPlayer(player, new DomainNet.Meter((float) data.meter, shown));
    }
}
