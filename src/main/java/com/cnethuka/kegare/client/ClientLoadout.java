package com.cnethuka.kegare.client;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.network.SyncPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The local player's loadout as last synced: one entry per regular slot, {@code null} for an empty slot. The combat
 * panel, the Sorcerer screen and Technique Mode's ability picking all read this, never the innate technique's own list.
 */
public final class ClientLoadout {
    private ClientLoadout() {}

    public static List<Ability> abilities() {
        SyncPayload data = ClientData.get();
        InnateTechnique technique = TechniqueMode.current();
        if (data == null || technique == null) return List.of();
        List<Ability> out = new ArrayList<>(data.loadout().size());
        for (Optional<ResourceLocation> slot : data.loadout()) out.add(slot.flatMap(id -> AbilityLookup.find(technique, id)).orElse(null));
        return Collections.unmodifiableList(out);
    }

    /** The ability or its special is a TOGGLE that is switched on. */
    public static boolean toggledOn(SyncPayload data, Ability ability) {
        return data.activeToggles().contains(ability.id())
                || ability.special().map(special -> data.activeToggles().contains(special.id())).orElse(false);
    }

    /** The Refinement level this part of their technique needs, or -1 if it isn't locked. */
    public static int lockedLevel(SyncPayload data, Ability ability) {
        return data.progression().lockedLevel(ability.id());
    }

    public static boolean isLocked(SyncPayload data, Ability ability) {
        return lockedLevel(data, ability) >= 0;
    }

    public static int size() {
        SyncPayload data = ClientData.get();
        return data == null || TechniqueMode.current() == null ? 0 : data.loadout().size();
    }
}
