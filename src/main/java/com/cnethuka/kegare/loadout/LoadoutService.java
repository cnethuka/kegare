package com.cnethuka.kegare.loadout;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The player's loadout: what each regular ability slot holds (server-authoritative; the client only sends edit
 * requests). Rules:
 * <ul>
 *   <li>one slot per ability of the current innate technique, filled with them in order by default;</li>
 *   <li>a slot holds one of those abilities, an unlocked shared ability, or nothing;</li>
 *   <li>nothing appears twice (placing an ability that is already slotted moves it);</li>
 *   <li>the ultimate (V) is not a loadout slot and can never change;</li>
 *   <li>no edits in combat or mid-cast; a toggle taken off the bar switches off;</li>
 *   <li>changing the innate technique (reroll, admin, rebirth) resets it to the new default; shared unlocks stay.</li>
 * </ul>
 */
public final class LoadoutService {
    public enum Action { SET, SWAP, CLEAR, RESET }

    private LoadoutService() {}

    public static LoadoutData get(Player player) {
        return player.getData(KegareAttachments.LOADOUT);
    }

    public static List<Optional<ResourceLocation>> slots(Player player) {
        return get(player).slots();
    }

    public static int size(Player player) {
        return get(player).slots.size();
    }

    /** What's in a slot, if it is usable right now (a current technique ability or an unlocked shared one). */
    @Nullable
    public static Ability abilityAt(Player player, int slot) {
        LoadoutData data = get(player);
        if (slot < 0 || slot >= data.slots.size()) return null;
        return data.slots.get(slot).map(id -> resolve(player, id)).orElse(null);
    }

    /** True if the loadout holds this ability (or the ability whose special this is). */
    public static boolean holds(Player player, ResourceLocation abilityId) {
        for (Optional<ResourceLocation> slot : get(player).slots) if (slot.isPresent() && slot.get().equals(abilityId)) return true;
        return false;
    }

    @Nullable
    private static Ability resolve(Player player, ResourceLocation id) {
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        if (technique.isPresent()) {
            for (Ability a : technique.get().abilities()) if (a.id().equals(id)) return a;
        }
        Optional<Ability> shared = SharedAbilities.get(id);
        return shared.isPresent() && SharedAbilityService.isUnlocked(player, id) ? shared.get() : null;
    }

    /** Regular slots on every bar (number keys 1-9). The ultimate (V) comes after them and never changes. */
    public static final int SLOTS = 9;

    /**
     * The default layout: the innate technique's abilities in order, then empty slots up to {@link #SLOTS} (room for
     * shared abilities and whatever comes later). Runs whenever the innate technique changes.
     */
    public static void reset(ServerPlayer player) {
        LoadoutData data = get(player);
        List<Optional<ResourceLocation>> before = List.copyOf(data.slots);
        data.slots.clear();
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        technique.ifPresent(t -> {
            for (Ability a : t.abilities()) if (data.slots.size() < SLOTS) data.slots.add(Optional.of(a.id()));
            while (data.slots.size() < SLOTS) data.slots.add(Optional.empty());
        });
        data.builtFor = technique.map(InnateTechnique::id).orElse(null);
        data.known.clear();
        technique.ifPresent(t -> t.abilities().forEach(a -> data.known.add(a.id())));
        switchOffRemoved(player, before);
        InnateTechniqueService.clampSelection(player);
        PlayerSync.markDirty(player);
    }

    /**
     * Login repair: a layout built for another innate technique is rebuilt. One of the wrong size (bars from before
     * there were nine slots had one per ability) keeps its entries and is padded or trimmed to {@link #SLOTS}.
     * Entries that no longer resolve (locked or removed abilities) and duplicates are emptied.
     */
    public static void validate(ServerPlayer player) {
        LoadoutData data = get(player);
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        ResourceLocation techniqueId = technique.map(InnateTechnique::id).orElse(null);
        if (!Objects.equals(data.builtFor, techniqueId)) {
            reset(player);
            return;
        }
        boolean changed = false;
        int size = technique.isPresent() ? SLOTS : 0;
        if (data.slots.size() != size) {
            while (data.slots.size() < size) data.slots.add(Optional.empty());
            while (data.slots.size() > size) data.slots.remove(data.slots.size() - 1);
            InnateTechniqueService.clampSelection(player);
            changed = true;
        }
        Set<ResourceLocation> seen = new HashSet<>();
        for (int i = 0; i < data.slots.size(); i++) {
            Optional<ResourceLocation> id = data.slots.get(i);
            if (id.isPresent() && (resolve(player, id.get()) == null || !seen.add(id.get()))) {
                data.slots.set(i, Optional.empty());
                changed = true;
            }
        }
        // Abilities added to the technique since this bar was built: into their default slot if it's free, else the first free one.
        if (technique.isPresent()) {
            List<Ability> abilities = technique.get().abilities();
            for (int i = 0; i < abilities.size(); i++) {
                ResourceLocation id = abilities.get(i).id();
                if (!data.known.add(id) || data.slots.contains(Optional.of(id))) continue;
                int slot = i < data.slots.size() && data.slots.get(i).isEmpty() ? i : data.slots.indexOf(Optional.<ResourceLocation>empty());
                if (slot < 0) continue;
                data.slots.set(slot, Optional.of(id));
                changed = true;
            }
        }
        if (changed) PlayerSync.markDirty(player);
    }

    /**
     * One edit request from the client. {@code slot}/{@code other} index the regular slots; the index right after
     * the last one is where the ultimate sits on screen, and is always refused.
     */
    public static LoadoutResult edit(ServerPlayer player, Action action, int slot, int other, Optional<ResourceLocation> abilityId) {
        if (!MaliceService.get(player).isBorn() || MaliceService.isSevered(player)) return LoadoutResult.SEVERED;
        if (InnateTechniqueService.current(player).isEmpty()) return LoadoutResult.NO_INNATE_TECHNIQUE;
        LoadoutData data = get(player);
        int size = data.slots.size();
        if (action != Action.RESET) {
            if (slot == size || (action == Action.SWAP && other == size)) return LoadoutResult.ULTIMATE_FIXED;
            if (slot < 0 || slot > size || (action == Action.SWAP && (other < 0 || other > size))) return LoadoutResult.BAD_SLOT;
        }
        if (CombatHelper.isInCombat(player)) return LoadoutResult.IN_COMBAT;
        if (CastManager.state(player).casting() != null) return LoadoutResult.CASTING;

        List<Optional<ResourceLocation>> before = List.copyOf(data.slots);
        switch (action) {
            case RESET -> {
                reset(player);
                return LoadoutResult.OK;
            }
            case CLEAR -> data.slots.set(slot, Optional.empty());
            case SWAP -> {
                Optional<ResourceLocation> a = data.slots.get(slot);
                data.slots.set(slot, data.slots.get(other));
                data.slots.set(other, a);
            }
            case SET -> {
                if (abilityId.isEmpty()) return LoadoutResult.UNAVAILABLE;
                ResourceLocation id = abilityId.get();
                boolean technique = InnateTechniqueService.current(player).get().abilities().stream().anyMatch(a -> a.id().equals(id));
                if (!technique) {
                    if (!SharedAbilities.isShared(id)) return LoadoutResult.UNAVAILABLE;
                    if (!SharedAbilityService.isUnlocked(player, id)) return LoadoutResult.LOCKED;
                }
                int existing = data.slots.indexOf(Optional.of(id));
                if (existing == slot) return LoadoutResult.OK;
                if (existing >= 0) { // already slotted: move it (what was here goes where it was), never duplicate
                    data.slots.set(existing, data.slots.get(slot));
                }
                data.slots.set(slot, Optional.of(id));
            }
        }
        switchOffRemoved(player, before);
        PlayerSync.markDirty(player);
        return LoadoutResult.OK;
    }

    /** Takes an ability off every slot (e.g. a shared ability being locked again). */
    public static void removeEverywhere(ServerPlayer player, ResourceLocation id) {
        LoadoutData data = get(player);
        for (int i = 0; i < data.slots.size(); i++) {
            if (data.slots.get(i).map(id::equals).orElse(false)) data.slots.set(i, Optional.empty());
        }
        PlayerSync.markDirty(player);
    }

    /** A toggle (or toggle special) that left the bar switches off: what's not on the bar can't keep running. */
    private static void switchOffRemoved(ServerPlayer player, List<Optional<ResourceLocation>> before) {
        for (Optional<ResourceLocation> old : before) {
            if (old.isEmpty() || holds(player, old.get())) continue;
            Ability ability = lookup(player, old.get());
            if (ability == null) continue;
            CastManager.switchOff(player, ability, EndReason.CANCELLED);
            ability.special().ifPresent(special -> CastManager.switchOff(player, special, EndReason.CANCELLED));
        }
    }

    /** Any ability by ID: the current technique's (with specials) or a shared one (locked or not). */
    @Nullable
    private static Ability lookup(Player player, ResourceLocation id) {
        Optional<Ability> fromTechnique = InnateTechniqueService.current(player).flatMap(t -> t.findAbility(id));
        return fromTechnique.orElseGet(() -> SharedAbilities.find(id).orElse(null));
    }
}
