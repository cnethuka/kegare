package com.cnethuka.kegare.technique;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import com.cnethuka.kegare.registry.KegareRegistries;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Technique assignment, ability selection and cooldowns (server-authoritative). */
public final class TechniqueService {
    private TechniqueService() {}

    public static TechniqueData get(Player player) {
        return player.getData(KegareAttachments.TECHNIQUE);
    }

    public static Optional<Technique> current(Player player) {
        return get(player).techniqueId().map(KegareRegistries.TECHNIQUE::get);
    }

    /**
     * Assigns a technique ({@code null} clears it). Ends running casts, clears cooldowns,
     * resets the selected slot and fires the technique hooks.
     */
    public static void setTechnique(ServerPlayer player, @Nullable Technique technique) {
        Optional<Technique> old = current(player);
        CastManager.stopAll(player, EndReason.TECHNIQUE_CHANGED);
        get(player).setTechnique(technique == null ? null : technique.id());
        old.ifPresent(t -> t.onRemoved(player));
        if (technique != null) technique.onAssigned(player);
        PlayerSync.markDirty(player);
    }

    /** Moves the selected slot by {@code delta}, wrapping around. */
    public static void cycle(ServerPlayer player, int delta) {
        Optional<Technique> technique = current(player);
        if (technique.isEmpty()) return;
        int size = technique.get().abilities().size();
        TechniqueData data = get(player);
        data.setSelectedSlot(Mth.positiveModulo(data.selectedSlot() + delta, size));
        PlayerSync.markDirty(player);
    }

    public static int cooldownRemaining(ServerPlayer player, Ability ability) {
        TechniqueData.Cooldown cooldown = get(player).cooldowns().get(ability.id());
        if (cooldown == null) return 0;
        return (int) Math.max(0, cooldown.endTick() - player.level().getGameTime());
    }

    public static void startCooldown(ServerPlayer player, Ability ability) {
        int ticks = ability.cooldownTicks(player);
        if (ticks <= 0) return;
        long now = player.level().getGameTime();
        TechniqueData data = get(player);
        data.pruneCooldowns(now);
        data.putCooldown(ability.id(), new TechniqueData.Cooldown(now + ticks, ticks));
        PlayerSync.markDirty(player);
    }

    /** Admin/debug: wipe cooldowns. */
    public static void clearCooldowns(ServerPlayer player) {
        get(player).pruneCooldowns(Long.MAX_VALUE);
        PlayerSync.markDirty(player);
    }

    /**
     * Login check. Clamps the selected slot and prunes expired cooldowns.
     * Returns true if the stored technique ID no longer exists (it is cleared; the caller re-rolls).
     */
    public static boolean validateOnLogin(ServerPlayer player) {
        TechniqueData data = get(player);
        data.pruneCooldowns(player.level().getGameTime());
        Optional<ResourceLocation> id = data.techniqueId();
        if (id.isPresent() && !KegareRegistries.TECHNIQUE.containsKey(id.get())) {
            Kegare.LOGGER.warn("{} had unknown technique {}; clearing it", player.getGameProfile().getName(), id.get());
            data.setTechnique(null);
            PlayerSync.markDirty(player);
            return true;
        }
        current(player).ifPresent(t -> {
            if (data.selectedSlot() < 0 || data.selectedSlot() >= t.abilities().size()) data.setSelectedSlot(0);
        });
        return false;
    }
}
