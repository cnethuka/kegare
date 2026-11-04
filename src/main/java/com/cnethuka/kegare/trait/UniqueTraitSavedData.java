package com.cnethuka.kegare.trait;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-level record for unique traits ({@code data/kegare_unique_traits.dat} in the overworld). It is the source
 * of truth: player copies are fixed on login ({@link TraitService#reconcile}).
 * <ul>
 *   <li>{@code bearers}: unique trait → current bearer.</li>
 *   <li>{@code pending}: inheritable traits between bearers (succession in progress or waiting for an heir).
 *       A pending trait can't be claimed by a birth roll.</li>
 *   <li>{@code revoked}: traits taken from an offline player; stripped from them on their next login.</li>
 *   <li>{@code lastSeen}: last login/logout time (epoch millis) per player, for the inactivity rule.</li>
 * </ul>
 */
public final class UniqueTraitSavedData extends SavedData {
    /**
     * A trait between bearers.
     *
     * @param killer  the player who caused the loss, if any (KILLER transfer mode)
     * @param dueTick overworld game time when it may pass on
     */
    public record Pending(UUID previousBearer, Optional<UUID> killer, long dueTick) {}

    private static final String NAME = "kegare_unique_traits";
    private static final SavedData.Factory<UniqueTraitSavedData> FACTORY =
            new SavedData.Factory<>(UniqueTraitSavedData::new, UniqueTraitSavedData::load);

    private final Map<ResourceLocation, UUID> bearers = new HashMap<>();
    private final Map<ResourceLocation, Pending> pending = new LinkedHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> revoked = new HashMap<>();
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    public static UniqueTraitSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // ------------------------------------------------------------------ bearers

    public Optional<UUID> bearer(ResourceLocation trait) {
        return Optional.ofNullable(bearers.get(trait));
    }

    public Map<ResourceLocation, UUID> bearers() {
        return Collections.unmodifiableMap(bearers);
    }

    /** Normal claim (birth roll, login restore). Fails if someone else holds it or it is pending succession. */
    public boolean tryClaim(ResourceLocation trait, UUID player) {
        UUID current = bearers.get(trait);
        if (current != null) return current.equals(player);
        if (pending.containsKey(trait)) return false;
        bearers.put(trait, player);
        setDirty();
        return true;
    }

    /** Admin grant or succession: overrides a pending succession, but never steals from a current bearer. */
    public boolean forceClaim(ResourceLocation trait, UUID player) {
        UUID current = bearers.get(trait);
        if (current != null && !current.equals(player)) return false;
        pending.remove(trait);
        bearers.put(trait, player);
        setDirty();
        return true;
    }

    /** Releases the trait if {@code player} holds it. */
    public void release(ResourceLocation trait, UUID player) {
        if (player.equals(bearers.get(trait))) {
            bearers.remove(trait);
            setDirty();
        }
    }

    public Set<ResourceLocation> heldBy(UUID player) {
        Set<ResourceLocation> out = new HashSet<>();
        bearers.forEach((trait, uuid) -> {
            if (uuid.equals(player)) out.add(trait);
        });
        return out;
    }

    // ------------------------------------------------------------------ succession

    public Optional<Pending> pending(ResourceLocation trait) {
        return Optional.ofNullable(pending.get(trait));
    }

    public Map<ResourceLocation, Pending> pendingView() {
        return Collections.unmodifiableMap(pending);
    }

    public void startSuccession(ResourceLocation trait, Pending entry) {
        pending.put(trait, entry);
        setDirty();
    }

    // ------------------------------------------------------------------ offline revocation

    public void revoke(UUID player, ResourceLocation trait) {
        revoked.computeIfAbsent(player, u -> new HashSet<>()).add(trait);
        setDirty();
    }

    /** Returns and clears the traits revoked from {@code player} while they were offline. */
    public Set<ResourceLocation> takeRevocations(UUID player) {
        Set<ResourceLocation> out = revoked.remove(player);
        if (out == null) return Set.of();
        setDirty();
        return out;
    }

    // ------------------------------------------------------------------ activity

    public Optional<Long> lastSeen(UUID player) {
        return Optional.ofNullable(lastSeen.get(player));
    }

    public void markSeen(UUID player, long epochMillis) {
        lastSeen.put(player, epochMillis);
        setDirty();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag bearerList = new ListTag();
        bearers.forEach((trait, uuid) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("trait", trait.toString());
            entry.putUUID("bearer", uuid);
            bearerList.add(entry);
        });
        tag.put("bearers", bearerList);

        ListTag pendingList = new ListTag();
        pending.forEach((trait, p) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("trait", trait.toString());
            entry.putUUID("previous", p.previousBearer());
            p.killer().ifPresent(k -> entry.putUUID("killer", k));
            entry.putLong("due", p.dueTick());
            pendingList.add(entry);
        });
        tag.put("pending", pendingList);

        ListTag revokedList = new ListTag();
        revoked.forEach((uuid, traits) -> traits.forEach(trait -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", uuid);
            entry.putString("trait", trait.toString());
            revokedList.add(entry);
        }));
        tag.put("revoked", revokedList);

        ListTag seenList = new ListTag();
        lastSeen.forEach((uuid, millis) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", uuid);
            entry.putLong("millis", millis);
            seenList.add(entry);
        });
        tag.put("last_seen", seenList);
        return tag;
    }

    private static UniqueTraitSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        UniqueTraitSavedData data = new UniqueTraitSavedData();
        for (CompoundTag e : compounds(tag, "bearers")) {
            ResourceLocation trait = ResourceLocation.tryParse(e.getString("trait"));
            if (trait != null && e.hasUUID("bearer")) data.bearers.put(trait, e.getUUID("bearer"));
        }
        for (CompoundTag e : compounds(tag, "pending")) {
            ResourceLocation trait = ResourceLocation.tryParse(e.getString("trait"));
            if (trait == null || !e.hasUUID("previous")) continue;
            Optional<UUID> killer = e.hasUUID("killer") ? Optional.of(e.getUUID("killer")) : Optional.empty();
            data.pending.put(trait, new Pending(e.getUUID("previous"), killer, e.getLong("due")));
        }
        for (CompoundTag e : compounds(tag, "revoked")) {
            ResourceLocation trait = ResourceLocation.tryParse(e.getString("trait"));
            if (trait != null && e.hasUUID("player")) data.revoked.computeIfAbsent(e.getUUID("player"), u -> new HashSet<>()).add(trait);
        }
        for (CompoundTag e : compounds(tag, "last_seen")) {
            if (e.hasUUID("player")) data.lastSeen.put(e.getUUID("player"), e.getLong("millis"));
        }
        return data;
    }

    private static Iterable<CompoundTag> compounds(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        java.util.List<CompoundTag> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i));
        return out;
    }
}
