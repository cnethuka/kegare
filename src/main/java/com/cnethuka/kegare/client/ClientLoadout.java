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
 * The local player's loadout as last synced: one entry per regular slot, {@code null} for an empty slot. The HUD
 * bar, the radial menu and Technique Mode's number keys all read this, never the innate technique's own list.
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

    public static int size() {
        SyncPayload data = ClientData.get();
        return data == null || TechniqueMode.current() == null ? 0 : data.loadout().size();
    }
}
