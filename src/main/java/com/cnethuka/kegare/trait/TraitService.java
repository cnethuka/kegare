package com.cnethuka.kegare.trait;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;

/** Grants/removes traits and enforces unique traits (one bearer per world) and Malice requirements. */
public final class TraitService {
    public enum AddResult { ADDED, ALREADY_HAS, UNIQUE_TAKEN, PENDING_SUCCESSION, REQUIRES_MALICE }

    private TraitService() {}

    public static TraitData get(Player player) {
        return player.getData(KegareAttachments.TRAITS);
    }

    public static boolean has(Player player, Trait trait) {
        return get(player).has(trait.id());
    }

    /**
     * Grants a trait. Severed players can't hold Malice-based traits. Unique traits: a birth roll can't take one that
     * is held or pending succession; ADMIN and INHERITED grants override a pending succession but never steal
     * from a current bearer.
     */
    public static AddResult add(ServerPlayer player, Trait trait, Trait.AddReason reason) {
        TraitData data = get(player);
        ResourceLocation id = trait.id();
        if (data.has(id)) return AddResult.ALREADY_HAS;
        if (!TraitRules.canHold(trait.requiresMalice(), MaliceService.isSevered(player))) return AddResult.REQUIRES_MALICE;
        if (trait.isUnique()) {
            UniqueTraitSavedData saved = UniqueTraitSavedData.get(player.server);
            boolean claimed = reason == Trait.AddReason.BIRTH
                    ? saved.tryClaim(id, player.getUUID())
                    : saved.forceClaim(id, player.getUUID());
            if (!claimed) {
                return saved.bearer(id).isPresent() ? AddResult.UNIQUE_TAKEN : AddResult.PENDING_SUCCESSION;
            }
        }
        data.add(id);
        trait.onAdded(player, reason);
        PlayerSync.markDirty(player);
        return AddResult.ADDED;
    }

    public static boolean remove(ServerPlayer player, Trait trait, Trait.RemoveReason reason) {
        return removeId(player, trait.id(), reason);
    }

    /** Removes every trait (rebirth). Unique traits are released for others to roll. */
    public static void removeAll(ServerPlayer player, Trait.RemoveReason reason) {
        for (ResourceLocation id : List.copyOf(get(player).view())) removeId(player, id, reason);
    }

    private static boolean removeId(ServerPlayer player, ResourceLocation id, Trait.RemoveReason reason) {
        if (!get(player).remove(id)) return false;
        UniqueTraitSavedData.get(player.server).release(id, player.getUUID());
        Trait trait = KegareRegistries.TRAIT.get(id);
        if (trait != null) trait.onRemoved(player, reason);
        PlayerSync.markDirty(player);
        return true;
    }

    /**
     * Birth roll: each trait rolls its own chance. Severed players skip Malice-based traits entirely; a unique trait
     * that is held or pending succession can't be rolled.
     */
    public static List<Trait> rollBirthTraits(ServerPlayer player) {
        boolean severed = MaliceService.isSevered(player);
        List<Trait> granted = new ArrayList<>();
        for (Trait trait : KegareRegistries.TRAIT) {
            double chance = trait.birthChance();
            if (!TraitRules.canRollAtBirth(chance, trait.requiresMalice(), severed) || !trait.canRollFor(player)) continue;
            if (player.getRandom().nextDouble() >= chance) continue;
            if (add(player, trait, Trait.AddReason.BIRTH) == AddResult.ADDED) granted.add(trait);
        }
        return granted;
    }

    /**
     * Run on login. Makes the player's traits agree with the world record:
     * applies offline revocations, drops unknown IDs, restores a lost claim (world record wiped),
     * strips unique traits held by someone else or in succession, and releases stale claims.
     */
    public static void reconcile(ServerPlayer player) {
        TraitData data = get(player);
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(player.server);
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();

        for (ResourceLocation id : saved.takeRevocations(uuid)) {
            if (data.has(id)) {
                Kegare.LOGGER.info("Removing revoked trait {} from {}", id, name);
                removeId(player, id, Trait.RemoveReason.REVOKED);
                Trait trait = KegareRegistries.TRAIT.get(id);
                if (trait != null) player.sendSystemMessage(Component.translatable("message.kegare.trait.lost").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        for (ResourceLocation id : List.copyOf(data.view())) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait == null) {
                Kegare.LOGGER.warn("Dropping unknown trait {} from {}", id, name);
                data.remove(id);
                PlayerSync.markDirty(player);
            } else if (trait.isUnique() && !saved.tryClaim(id, uuid)) {
                Kegare.LOGGER.warn("Removing unique trait {} from {}: held by {} or in succession", id, name,
                        saved.bearer(id).map(UUID::toString).orElse("nobody"));
                removeId(player, id, Trait.RemoveReason.CONFLICT);
            }
        }
        for (ResourceLocation id : saved.heldBy(uuid)) {
            if (!data.has(id)) saved.release(id, uuid);
        }
    }

    public static Optional<UUID> bearer(MinecraftServer server, Trait trait) {
        return UniqueTraitSavedData.get(server).bearer(trait.id());
    }

    /** Best-effort name for a UUID (online player, then profile cache, then the raw UUID). */
    public static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }
        return uuid.toString();
    }
}
