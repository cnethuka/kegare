package com.cnethuka.kegare.loadout;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Unlocking and locking shared abilities (server-authoritative). */
public final class SharedAbilityService {
    private SharedAbilityService() {}

    public static SharedAbilityData get(Player player) {
        return player.getData(KegareAttachments.SHARED_ABILITIES);
    }

    public static boolean isUnlocked(Player player, ResourceLocation id) {
        if (SharedAbilities.forEveryone(id)) return MaliceService.get(player).isBorn() && !MaliceService.isSevered(player);
        return get(player).isUnlocked(id);
    }

    /** Everything they have: what they unlocked, and what every Malice user has. */
    public static java.util.List<ResourceLocation> unlockedIds(Player player) {
        java.util.List<ResourceLocation> out = new java.util.ArrayList<>();
        for (Ability a : SharedAbilities.all()) if (isUnlocked(player, a.id())) out.add(a.id());
        return out;
    }

    /**
     * Unlocks a shared ability. Severed players can't (every shared ability so far runs on Malice). Returns whether
     * it was newly unlocked.
     */
    public static boolean unlock(ServerPlayer player, Ability ability) {
        if (!SharedAbilities.isShared(ability.id()) || SharedAbilities.forEveryone(ability.id()) || MaliceService.isSevered(player)
                || !MaliceService.get(player).isBorn()) return false;
        boolean added = get(player).unlocked.add(ability.id());
        if (added) {
            PlayerSync.markDirty(player);
            for (java.util.function.BiConsumer<ServerPlayer, Ability> listener : UNLOCK_LISTENERS) listener.accept(player, ability);
        }
        return added;
    }

    private static final java.util.List<java.util.function.BiConsumer<ServerPlayer, Ability>> UNLOCK_LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Told when a player newly unlocks a shared ability, however it happened. */
    public static void addUnlockListener(java.util.function.BiConsumer<ServerPlayer, Ability> listener) {
        UNLOCK_LISTENERS.add(listener);
    }

    /** Admin/debug: locks it again, takes it off the loadout and ends it if it is running. */
    public static boolean lock(ServerPlayer player, Ability ability) {
        boolean removed = get(player).unlocked.remove(ability.id());
        if (!removed) return false;
        CastManager.switchOff(player, ability, EndReason.CANCELLED);
        if (CastManager.state(player).casting() != null && CastManager.state(player).casting().ability() == ability) {
            CastManager.interrupt(player);
        }
        LoadoutService.removeEverywhere(player, ability.id());
        PlayerSync.markDirty(player);
        return true;
    }
}
