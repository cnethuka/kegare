package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.advancement.KegareAdvancements;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * "Are They High?" (DESIGN §3.31's companion, Base tab: task, hidden, announced; under It's been a while, and only for those who have
 * it): explain Reverse Malice Technique to the person you're fighting. Chat is only read here, never blocked or changed.
 * <p>
 * A player's own chat messages from the last {@link #WINDOW_TICKS} (20 s), taken together, must hold at least {@link #MIN_WORDS} (12)
 * words and all four ideas: "malice" or "energy"; "negative"; "multiply", "multiplying" or "multiplied"; "positive". Extra text is
 * always fine, in any wording, in one message or several. Case doesn't matter, and small typos are allowed: edit distance 2 on a typed
 * word of 6 letters or more, 1 on a shorter one. They must be in a fight with a living player within {@link #RANGE} (16) blocks: a hit
 * between the two within {@link #FIGHT_TICKS} (10 s). It's awarded on the message that completes it.
 */
public final class AreTheyHigh {
    public static final long WINDOW_TICKS = 20 * 20;
    public static final long FIGHT_TICKS = 10 * 20;
    public static final int MIN_WORDS = 12;
    public static final double RANGE = 16.0;
    /** Each idea, as the words that say it. */
    static final List<List<String>> IDEAS = List.of(List.of("malice", "energy"), List.of("negative"),
            List.of("multiply", "multiplying", "multiplied"), List.of("positive"));

    private record Message(long tick, String text) {}

    private static final Map<UUID, Deque<Message>> SAID = new HashMap<>();
    private record Pending(ServerPlayer player, String text, int tick) {}
    /** Messages said, waiting for the end of the tick after (so their broadcast goes first). */
    private static final List<Pending> PENDING = new ArrayList<>();
    /** When each player last hit each other player: attacker → victim → game tick. */
    private static final Map<UUID, Map<UUID, Long>> HITS = new HashMap<>();

    private AreTheyHigh() {}

    static void init() {
        // Lowest priority, and cancelled messages are skipped: only what was really said is read, and never changed.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerChatEvent e) -> {
            ServerPlayer player = e.getPlayer();
            String text = e.getRawText();
            // Read at the end of the next server tick, not here: the message is broadcast first (its broadcast is itself queued after
            // this event), so the announcement follows the line that earned it, 50 ms later at most.
            synchronized (PENDING) {
                PENDING.add(new Pending(player, text, player.server.getTickCount() + 1));
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> {
            List<Pending> due = new ArrayList<>();
            synchronized (PENDING) {
                PENDING.removeIf(p -> p.tick() <= e.getServer().getTickCount() && due.add(p));
            }
            for (Pending p : due) if (!p.player().isRemoved()) onChat(p.player(), p.text());
        });
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer victim && attacker(e.getSource()) instanceof ServerPlayer attacker && attacker != victim) {
                hit(attacker, victim);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> clear());
    }

    private static Entity attacker(DamageSource source) {
        Entity e = source.getEntity();
        if (e == null && source.getDirectEntity() instanceof Projectile p) e = p.getOwner();
        return e;
    }

    static void hit(ServerPlayer attacker, ServerPlayer victim) {
        HITS.computeIfAbsent(attacker.getUUID(), k -> new HashMap<>()).put(victim.getUUID(), attacker.level().getGameTime());
    }

    /** A message they sent: kept for 20 s, and checked with what came before. Returns whether this one earned it. */
    public static boolean onChat(ServerPlayer player, String text) {
        long now = player.level().getGameTime();
        Deque<Message> said = SAID.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        said.addLast(new Message(now, text));
        while (!said.isEmpty() && now - said.peekFirst().tick() > WINDOW_TICKS) said.pollFirst();
        if (!hasItsBeenAWhile(player)) return false;
        StringBuilder all = new StringBuilder();
        for (Message m : said) all.append(m.text()).append(' ');
        if (!explains(all.toString()) || !inAFight(player)) return false;
        KegareAdvancements.award(player, KegareAdvancements.ARE_THEY_HIGH);
        return true;
    }

    /** At least 12 words, and all four ideas among them. */
    static boolean explains(String text) {
        List<String> words = words(text);
        if (words.size() < MIN_WORDS) return false;
        for (List<String> idea : IDEAS) {
            boolean found = false;
            for (String word : words) {
                for (String target : idea) {
                    if (close(word, target)) {
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (!found) return false;
        }
        return true;
    }

    /** Words: runs of letters, lower case. */
    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) if (!part.isEmpty()) out.add(part);
        return out;
    }

    /** A typo away: edit distance 2 on a typed word of 6 letters or more, 1 on a shorter one. */
    static boolean close(String typed, String target) {
        int allowed = typed.length() >= 6 ? 2 : 1;
        if (Math.abs(typed.length() - target.length()) > allowed) return false;
        return distance(typed, target) <= allowed;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** A living player within 16 blocks they've traded a hit with (either way) in the last 10 s. */
    static boolean inAFight(ServerPlayer player) {
        long now = player.level().getGameTime();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !other.isAlive() || other.isSpectator() || other.distanceTo(player) > RANGE) continue;
            if (recent(player, other, now) || recent(other, player, now)) return true;
        }
        return false;
    }

    private static boolean recent(ServerPlayer attacker, ServerPlayer victim, long now) {
        Long at = HITS.getOrDefault(attacker.getUUID(), Map.of()).get(victim.getUUID());
        return at != null && now - at <= FIGHT_TICKS;
    }

    static void forget(UUID player) {
        SAID.remove(player);
        HITS.remove(player);
        for (Map<UUID, Long> hits : HITS.values()) hits.remove(player);
    }

    public static void clear() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        SAID.clear();
        HITS.clear();
    }

    /** "Requires It's been a while": only those who have it can earn this. */
    private static boolean hasItsBeenAWhile(ServerPlayer player) {
        var holder = player.server.getAdvancements().get(KegareAdvancements.ITS_BEEN_A_WHILE);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }
}
