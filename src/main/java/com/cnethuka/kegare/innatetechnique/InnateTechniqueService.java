package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Innate technique assignment, ability selection and cooldowns (server-authoritative). */
public final class InnateTechniqueService {
    private InnateTechniqueService() {}

    public static InnateTechniqueData get(Player player) {
        return player.getData(KegareAttachments.INNATE_TECHNIQUE);
    }

    public static Optional<InnateTechnique> current(Player player) {
        return get(player).innateTechniqueId().map(KegareRegistries.INNATE_TECHNIQUE::get);
    }

    /**
     * Assigns an innate technique ({@code null} clears it). Ends running casts, clears cooldowns,
     * resets the selected slot and fires the innate technique hooks.
     */
    public static void setInnateTechnique(ServerPlayer player, @Nullable InnateTechnique innateTechnique) {
        Optional<InnateTechnique> old = current(player);
        CastManager.stopAll(player, EndReason.INNATE_TECHNIQUE_CHANGED);
        get(player).setInnateTechnique(innateTechnique == null ? null : innateTechnique.id());
        com.cnethuka.kegare.progression.RefinementService.onTechniqueChanged(player); // before the new loadout: it's built for level 0
        LoadoutService.reset(player); // the new technique's default layout; shared unlocks are kept
        old.ifPresent(t -> t.onRemoved(player));
        if (innateTechnique != null) innateTechnique.onAssigned(player);
        PlayerSync.markDirty(player);
    }

    /** Moves the selected slot by {@code delta}, wrapping around. */
    public static void cycle(ServerPlayer player, int delta) {
        if (current(player).isEmpty()) return;
        int size = LoadoutService.size(player);
        if (size == 0) return;
        InnateTechniqueData data = get(player);
        data.setSelectedSlot(Mth.positiveModulo(data.selectedSlot() + delta, size));
        PlayerSync.markDirty(player);
    }

    /** Selects a loadout slot (Technique Mode's number keys, just before they cast). Out-of-range requests are ignored. */
    public static void select(ServerPlayer player, int slot) {
        if (current(player).isEmpty() || slot < 0 || slot >= LoadoutService.size(player)) return;
        get(player).setSelectedSlot(slot);
        PlayerSync.markDirty(player);
    }

    /** Keeps the selected slot inside the loadout. */
    public static void clampSelection(ServerPlayer player) {
        InnateTechniqueData data = get(player);
        if (data.selectedSlot() < 0 || data.selectedSlot() >= Math.max(1, LoadoutService.size(player))) data.setSelectedSlot(0);
    }

    public static int cooldownRemaining(ServerPlayer player, Ability ability) {
        InnateTechniqueData.Cooldown cooldown = get(player).cooldowns().get(ability.cooldownKey());
        if (cooldown == null) return 0;
        return (int) Math.max(0, cooldown.endTick() - player.level().getGameTime());
    }

    public static void startCooldown(ServerPlayer player, Ability ability) {
        int ticks = ability.cooldownTicks(player);
        if (ticks <= 0) return;
        long now = player.level().getGameTime();
        InnateTechniqueData data = get(player);
        data.pruneCooldowns(now);
        data.putCooldown(ability.cooldownKey(), new InnateTechniqueData.Cooldown(now + ticks, ticks));
        PlayerSync.markDirty(player);
    }

    /**
     * Moves a running cooldown's end by {@code ticks} (negative: sooner). Nothing if it isn't running; past now, it's over. Used by the
     * abilities that run the clock (Time Manipulation, DESIGN §3.26).
     */
    public static void shiftCooldown(ServerPlayer player, ResourceLocation cooldownKey, long ticks, boolean sync) {
        InnateTechniqueData data = get(player);
        InnateTechniqueData.Cooldown cooldown = data.cooldowns().get(cooldownKey);
        long now = player.level().getGameTime();
        if (cooldown == null || cooldown.endTick() <= now || ticks == 0) return;
        long end = cooldown.endTick() + ticks;
        if (end <= now) data.removeCooldown(cooldownKey);
        else data.putCooldown(cooldownKey, new InnateTechniqueData.Cooldown(end, cooldown.duration()));
        if (sync) PlayerSync.markDirty(player);
    }

    /** Admin/debug: wipe cooldowns. */
    /** Ends one cooldown now (an ability's, or its special's: they share the key). */
    public static void clearCooldown(ServerPlayer player, ResourceLocation cooldownKey) {
        get(player).removeCooldown(cooldownKey);
        PlayerSync.markDirty(player);
    }

    public static void clearCooldowns(ServerPlayer player) {
        get(player).pruneCooldowns(Long.MAX_VALUE);
        PlayerSync.markDirty(player);
    }

    /**
     * Login check. Clamps the selected slot and prunes expired cooldowns.
     * Returns true if the stored innate technique ID no longer exists (it is cleared; the caller re-rolls).
     */
    public static boolean validateOnLogin(ServerPlayer player) {
        InnateTechniqueData data = get(player);
        data.pruneCooldowns(player.level().getGameTime());
        Optional<ResourceLocation> id = data.innateTechniqueId();
        if (id.isPresent() && !KegareRegistries.INNATE_TECHNIQUE.containsKey(id.get())) {
            Kegare.LOGGER.warn("{} had unknown innate technique {}; clearing it", player.getGameProfile().getName(), id.get());
            data.setInnateTechnique(null);
            PlayerSync.markDirty(player);
            return true;
        }
        LoadoutService.validate(player);
        clampSelection(player);
        return false;
    }
}
