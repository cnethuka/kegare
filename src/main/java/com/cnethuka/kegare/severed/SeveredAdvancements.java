package com.cnethuka.kegare.severed;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.advancement.KegareAdvancements;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.Fight;
import com.cnethuka.kegare.progression.FightReport;
import com.cnethuka.kegare.progression.FightTracker;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Severed's two advancements (DESIGN §3.27), under the Severed one: hidden, announced in chat, awarded from code and only while the
 * player is Severed. A "Malice user" is any player who isn't Severed; a "fight" is the fight tracker's (§3.21). Mobs never count.
 * <ul>
 * <li><b>How cruel, don't you have a human heart?</b> (challenge): {@code cruelKills} (3) different Malice users killed within
 * {@code cruelWindowSeconds} (60).</li>
 * <li><b>Our fight is just getting started!</b> (goal): down to {@code startedHearts} (3) hearts or fewer in a fight with a Malice
 * user, then that same player killed before the fight ends. A Totem of Undying they use in that fight disqualifies it; golden apples
 * and natural healing don't.</li>
 * </ul>
 */
public final class SeveredAdvancements {
    public static final ResourceLocation HOW_CRUEL = Kegare.id("how_cruel");
    public static final ResourceLocation JUST_GETTING_STARTED = Kegare.id("just_getting_started");

    private record Kill(UUID victim, long tick) {}

    /** A fight between a Severed player and a Malice user, by when it started. */
    private record FightKey(UUID severed, UUID opponent, long start) {}

    private static final Map<UUID, ArrayDeque<Kill>> KILLS = new HashMap<>();
    /** Fights a Severed player has been down to the line in. */
    private static final Set<FightKey> LOW = new HashSet<>();
    /** Fights a Severed player used a totem in. */
    private static final Set<FightKey> TOTEM = new HashSet<>();

    private SeveredAdvancements() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(SeveredAdvancements::onDeath);
        // After the fight tracker has recorded the hit (and opened the fight, if this hit did).
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SeveredAdvancements::onDamaged);
        NeoForge.EVENT_BUS.addListener(SeveredAdvancements::onTotem);
        FightTracker.addListener(SeveredAdvancements::onFightEnded);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clear());
    }

    static void award(ServerPlayer player, ResourceLocation id) {
        if (Severed.is(player)) KegareAdvancements.award(player, id);
    }

    private static boolean maliceUser(LivingEntity entity) {
        return entity instanceof ServerPlayer p && !Severed.is(p);
    }

    /** Down to the line: noted against every fight they're in with a Malice user. */
    static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Severed.is(p)) return;
        noteIfLow(p);
    }

    static void noteIfLow(ServerPlayer p) {
        if (p.getHealth() > SeveredConfig.STARTED_HEARTS.get() * 2 || !p.isAlive()) return;
        for (Fight fight : FightTracker.of(p)) {
            UUID opponent = fight.opponentOf(p.getUUID()).player;
            net.minecraft.world.entity.player.Player other = p.level().getPlayerByUUID(opponent);
            if (other == null) other = Attribution.player(p.server, opponent);
            if (other instanceof ServerPlayer o && maliceUser(o)) LOW.add(new FightKey(p.getUUID(), opponent, fight.startTick()));
        }
    }

    static void onTotem(LivingUseTotemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Severed.is(p)) return;
        for (Fight fight : FightTracker.of(p)) TOTEM.add(new FightKey(p.getUUID(), fight.opponentOf(p.getUUID()).player, fight.startTick()));
    }

    /** Runs before the fight tracker ends the dead player's fights (it listens last), so the fight is still on. */
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !maliceUser(event.getEntity())) return;
        ServerPlayer victim = (ServerPlayer) event.getEntity();
        Attribution.Hit hit = Attribution.of(event.getSource(), victim.server);
        ServerPlayer killer = hit != null ? hit.attacker() : event.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        if (killer == null || killer == victim || !Severed.is(killer)) return;
        long now = killer.level().getGameTime();

        ArrayDeque<Kill> kills = KILLS.computeIfAbsent(killer.getUUID(), k -> new ArrayDeque<>());
        long window = SeveredConfig.ticks(SeveredConfig.CRUEL_WINDOW.get());
        kills.removeIf(k -> now - k.tick() > window || k.victim().equals(victim.getUUID()));
        kills.addLast(new Kill(victim.getUUID(), now));
        if (kills.size() >= SeveredConfig.CRUEL_KILLS.get()) award(killer, HOW_CRUEL);

        Fight fight = FightTracker.between(killer, victim);
        if (fight != null) {
            FightKey key = new FightKey(killer.getUUID(), victim.getUUID(), fight.startTick());
            if (LOW.contains(key) && !TOTEM.contains(key)) award(killer, JUST_GETTING_STARTED);
        }
    }

    static void onFightEnded(FightReport report) {
        UUID a = report.player().getUUID(), b = report.opponent().getUUID();
        LOW.removeIf(k -> k.severed().equals(a) && k.opponent().equals(b));
        TOTEM.removeIf(k -> k.severed().equals(a) && k.opponent().equals(b));
    }

    static void clear() {
        KILLS.clear();
        LOW.clear();
        TOTEM.clear();
    }
}
