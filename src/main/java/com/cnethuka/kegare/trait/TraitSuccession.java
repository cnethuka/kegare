package com.cnethuka.kegare.trait;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Losing and passing on unique traits.
 * <ul>
 *   <li>PvP death: lostOnPvpDeath traits are stripped from the victim (non-PvP deaths keep them).</li>
 *   <li>Becoming Severed: every Malice-based trait is stripped.</li>
 *   <li>Release: admin command or inactivity frees a unique trait from its holder, online or offline
 *       (offline holders lose it on their next login).</li>
 *   <li>Inheritable traits then wait {@link Trait#successionDelayTicks()} and pass to an heir picked by
 *       {@link TransferRules}: online, not Severed (if the trait needs Malice), not the previous bearer.
 *       If nobody is eligible, the first eligible player to log in receives it.</li>
 * </ul>
 */
public final class TraitSuccession {
    private TraitSuccession() {}

    /** The clock a pending trait's due time uses: game time, or with Postgres storage wall-clock time in ticks, since game time differs per server. */
    public static long now(MinecraftServer server) {
        return UniqueTraitSavedData.get(server).isNetworked() ? System.currentTimeMillis() / 50 : server.overworld().getGameTime();
    }

    /** The bearer was killed by {@code killer}: strip lostOnPvpDeath traits and start their succession. */
    public static void onPlayerKilled(ServerPlayer victim, ServerPlayer killer) {
        for (ResourceLocation id : List.copyOf(TraitService.get(victim).view())) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait == null || !trait.isLostOnPvpDeath()) continue;
            TraitService.remove(victim, trait, Trait.RemoveReason.LOST_IN_PVP);
            victim.sendSystemMessage(Component.translatable("message.kegare.trait.lost").withStyle(ChatFormatting.DARK_GRAY));
            trait.onBearerKilledByPlayer(victim, killer);
            Kegare.LOGGER.info("{} lost {} to {}", victim.getGameProfile().getName(), id, killer.getGameProfile().getName());
            if (trait.isInheritable()) begin(victim.server, trait, victim.getUUID(), killer.getUUID());
        }
    }

    /**
     * The player is Severed: strip every trait that needs Malice ({@link TraitRules#whenSevered}). Unique inheritable
     * ones go through the normal succession (their usual delay, then an eligible heir); others are just removed.
     * Called by {@code MaliceService} whenever a player becomes Severed, and on login as a safety net.
     */
    public static void stripMaliceTraits(ServerPlayer player) {
        for (ResourceLocation id : List.copyOf(TraitService.get(player).view())) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait == null) continue;
            TraitRules.SeveredAction action = TraitRules.whenSevered(trait.requiresMalice(), trait.isUnique(), trait.isInheritable());
            if (action == TraitRules.SeveredAction.KEEP) continue;
            TraitService.remove(player, trait, Trait.RemoveReason.SEVERED);
            player.sendSystemMessage(Component.translatable("message.kegare.trait.lost").withStyle(ChatFormatting.DARK_GRAY));
            Kegare.LOGGER.info("{} became Severed and lost {}", player.getGameProfile().getName(), id);
            if (action == TraitRules.SeveredAction.TRANSFER) begin(player.server, trait, player.getUUID(), null);
        }
    }

    /**
     * Frees a unique trait from its holder (admin release or inactivity). Online holders lose it now,
     * offline holders on their next login. Inheritable traits then start their succession.
     * Returns the previous holder, or empty if nobody held it.
     */
    public static Optional<UUID> release(MinecraftServer server, Trait trait) {
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(server);
        Optional<UUID> holder = saved.bearer(trait.id());
        if (holder.isEmpty()) return Optional.empty();
        ServerPlayer online = server.getPlayerList().getPlayer(holder.get());
        if (online != null) {
            TraitService.remove(online, trait, Trait.RemoveReason.RELEASED);
            online.sendSystemMessage(Component.translatable("message.kegare.trait.lost").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            saved.release(trait.id(), holder.get());
            saved.revoke(holder.get(), trait.id());
        }
        if (trait.isInheritable()) begin(server, trait, holder.get(), null);
        return holder;
    }

    private static void begin(MinecraftServer server, Trait trait, UUID previousBearer, @Nullable UUID killer) {
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(server);
        saved.release(trait.id(), previousBearer);
        long due = now(server) + Math.max(0, trait.successionDelayTicks());
        saved.startSuccession(trait.id(), new UniqueTraitSavedData.Pending(previousBearer, Optional.ofNullable(killer), due));
        Kegare.LOGGER.info("{} is passing on (due at tick {})", trait.id(), due);
    }

    /** Every second: hand each due trait to an eligible online heir, if there is one. */
    public static void tick(MinecraftServer server) {
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(server);
        if (saved.pendingView().isEmpty() || saved.isNetworked()) return; // a network's succession runs in NetworkTraits
        long now = now(server);
        for (Map.Entry<ResourceLocation, UniqueTraitSavedData.Pending> entry : List.copyOf(saved.pendingView().entrySet())) {
            UniqueTraitSavedData.Pending pending = entry.getValue();
            Trait trait = KegareRegistries.TRAIT.get(entry.getKey());
            if (trait == null || pending.dueTick() > now) continue;
            List<TransferRules.Candidate> online = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!(p instanceof FakePlayer)) online.add(candidate(p, trait));
            }
            Optional<UUID> heir = TransferRules.pickHeir(trait.successionMode(), online, pending.previousBearer(),
                    pending.killer().orElse(null), server.overworld().getRandom()::nextInt);
            ServerPlayer heirPlayer = heir.map(server.getPlayerList()::getPlayer).orElse(null);
            if (heirPlayer != null) assign(server, trait, heirPlayer);
        }
    }

    /** On login: an eligible player receives any trait that is due and still waiting for an heir. */
    public static void onLogin(ServerPlayer player) {
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(player.server);
        if (saved.isNetworked()) return; // the next network pass sees them online
        long now = now(player.server);
        for (Map.Entry<ResourceLocation, UniqueTraitSavedData.Pending> entry : List.copyOf(saved.pendingView().entrySet())) {
            Trait trait = KegareRegistries.TRAIT.get(entry.getKey());
            if (trait == null || entry.getValue().dueTick() > now) continue;
            if (TransferRules.isEligibleHeir(candidate(player, trait), entry.getValue().previousBearer())) {
                assign(player.server, trait, player);
            }
        }
    }

    private static TransferRules.Candidate candidate(ServerPlayer player, Trait trait) {
        return new TransferRules.Candidate(player.getUUID(), trait.requiresMalice() && MaliceService.isSevered(player));
    }

    private static void assign(MinecraftServer server, Trait trait, ServerPlayer heir) {
        if (inherit(heir, trait)) broadcast(server, trait);
    }

    private static boolean inherit(ServerPlayer heir, Trait trait) {
        if (TraitService.add(heir, trait, Trait.AddReason.INHERITED) != TraitService.AddResult.ADDED) return false;
        Kegare.LOGGER.info("{} passed to {}", trait.id(), heir.getGameProfile().getName());
        heir.sendSystemMessage(Component.translatable("message.kegare.trait.inherited").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    /** Announces to everyone that it has passed on, naming no one (DESIGN §1). */
    private static void broadcast(MinecraftServer server, Trait trait) {
        String broadcast = trait.successionBroadcastKey();
        if (broadcast != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(broadcast).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        }
    }

    // ------------------------------------------------------------------ across a network (DESIGN §3.33)

    /** A network event: this server applies a grant if the player is here, or relays the broadcast. */
    public static void networkEvent(MinecraftServer server, String kind, ResourceLocation id, @Nullable UUID player) {
        Trait trait = KegareRegistries.TRAIT.get(id);
        if (trait == null) return;
        if (kind.equals("broadcast")) {
            broadcast(server, trait);
        } else if (kind.equals("grant") && player != null) {
            ServerPlayer heir = server.getPlayerList().getPlayer(player);
            if (heir != null) inherit(heir, trait);
        }
    }

    /** The database refused a claim: someone else on the network has it. Silently undone, naming nothing (DESIGN §1). */
    public static void claimRefused(MinecraftServer server, ResourceLocation id, UUID player) {
        Trait trait = KegareRegistries.TRAIT.get(id);
        ServerPlayer here = server.getPlayerList().getPlayer(player);
        if (trait != null && here != null && TraitService.has(here, trait)) TraitService.remove(here, trait, Trait.RemoveReason.CONFLICT);
    }

    /** The record was refreshed: apply revocations made elsewhere to anyone who is here. */
    public static void networkRefreshed(MinecraftServer server) {
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(server);
        for (UUID player : saved.revokedPlayers()) {
            ServerPlayer here = server.getPlayerList().getPlayer(player);
            if (here != null) TraitService.reconcile(here);
        }
    }

    // ------------------------------------------------------------------ inactivity

    public static void markSeen(ServerPlayer player) {
        UniqueTraitSavedData.get(player.server).markSeen(player.getUUID(), System.currentTimeMillis());
    }

    /** Releases unique traits whose holder has been offline longer than {@code uniqueTraitInactiveDays}. */
    public static void checkInactive(MinecraftServer server) {
        double days = KegareServerConfig.UNIQUE_TRAIT_INACTIVE_DAYS.get();
        if (days <= 0) return;
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(server);
        long now = System.currentTimeMillis();
        for (Map.Entry<ResourceLocation, UUID> entry : List.copyOf(saved.bearers().entrySet())) {
            UUID holder = entry.getValue();
            if (server.getPlayerList().getPlayer(holder) != null || saved.onlineOnNetwork(holder)) continue;
            Optional<Long> seen = saved.lastSeen(holder);
            if (seen.isEmpty()) {
                saved.markSeen(holder, now); // no record yet: start their clock now
                continue;
            }
            Trait trait = KegareRegistries.TRAIT.get(entry.getKey());
            if (trait != null && TransferRules.isInactive(seen.get(), now, days)) {
                Kegare.LOGGER.info("Releasing {} from inactive holder {}", entry.getKey(), TraitService.nameOf(server, holder));
                release(server, trait);
            }
        }
    }
}
