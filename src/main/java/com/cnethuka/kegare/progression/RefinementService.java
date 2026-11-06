package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Refinement: per technique, levels 0-10 (config), earned only by landing the current technique's abilities on real
 * targets ({@link Landings}). It only unlocks things: nothing here changes damage, cost or cooldown. Severed players have
 * none, since nothing can land for them.
 */
public final class RefinementService {
    /** Told whenever a player's level changes, up or down. */
    @FunctionalInterface
    public interface LevelListener {
        void onLevelChanged(ServerPlayer player, int from, int to);
    }

    private static final List<LevelListener> LISTENERS = new CopyOnWriteArrayList<>();

    private RefinementService() {}

    public static void addLevelListener(LevelListener listener) {
        LISTENERS.add(listener);
    }

    public static RefinementData get(Player player) {
        return player.getData(KegareAttachments.REFINEMENT);
    }

    public static int level(Player player) {
        return get(player).level;
    }

    public static int peak(Player player) {
        return get(player).peak;
    }

    public static int maxLevel() {
        return ProgressionConfig.REFINEMENT_MAX_LEVEL.get();
    }

    /** The XP from {@code level - 1} to {@code level}. */
    public static double xpForLevel(int level) {
        return RefinementMath.xpForLevel(level, ProgressionConfig.REFINEMENT_XP_BASE.get(), ProgressionConfig.REFINEMENT_XP_EXPONENT.get());
    }

    private static RefinementMath.Settings settings() {
        return new RefinementMath.Settings(ProgressionConfig.REFINEMENT_BASE_XP.get(), ProgressionConfig.REFINEMENT_SPECIAL_XP.get(),
                ProgressionConfig.REFINEMENT_PLAYER_MULTIPLIER.get(), ProgressionConfig.REFINEMENT_DECAY.get(), ProgressionConfig.REFINEMENT_DECAY_FLOOR.get());
    }

    /** From the shared hit path: {@code ability} landed on {@code target}, a real target. */
    static void onLanded(ServerPlayer caster, LivingEntity target, Ability ability) {
        RefinementData data = get(caster);
        long now = caster.server.overworld().getGameTime();
        long window = ProgressionConfig.ticks(ProgressionConfig.ROLLING_WINDOW);
        int earlier = data.hits.count(target.getUUID(), now, window);
        data.hits.record(target.getUUID(), now, window);
        addXp(caster, RefinementMath.landingXp(settings(), ability.isSpecial(), target instanceof Player, earlier,
                ProgressionConfig.gradeMultiplier(GradeService.grade(caster))));
    }

    public static void addXp(ServerPlayer player, double xp) {
        if (xp <= 0) return;
        RefinementData data = get(player);
        int max = maxLevel();
        if (data.level >= max) return;
        int from = data.level;
        data.xp += xp;
        while (data.level < max && data.xp + 1.0e-9 >= xpForLevel(data.level + 1)) {
            data.xp -= xpForLevel(data.level + 1);
            data.level++;
        }
        if (data.level >= max) data.xp = 0.0;
        PlayerSync.markDirty(player);
        if (data.level != from) changed(player, from, data.level);
    }

    /** Admin: any level from 0 to the maximum. Lowering it takes newly locked abilities off the loadout. */
    public static void setLevel(ServerPlayer player, int level) {
        RefinementData data = get(player);
        int from = data.level;
        data.level = Math.max(0, Math.min(maxLevel(), level));
        data.xp = 0.0;
        PlayerSync.markDirty(player);
        if (data.level != from) changed(player, from, data.level);
    }

    /** The technique changed: Refinement starts over, Peak Refinement doesn't. Runs before the new loadout is built. */
    public static void onTechniqueChanged(ServerPlayer player) {
        RefinementData data = get(player);
        data.level = 0;
        data.xp = 0.0;
        data.hits.clear();
        PlayerSync.markDirty(player);
        Unlocks.onTechniqueChanged(player);
    }

    private static void changed(ServerPlayer player, int from, int to) {
        RefinementData data = get(player);
        data.peak = Math.max(data.peak, to);
        for (LevelListener listener : LISTENERS) listener.onLevelChanged(player, from, to);
        GradeService.tryPromote(player); // a new peak may be the last thing an Assessment was waiting on
    }

    /** Admin: back to nothing, Peak included. */
    public static void reset(ServerPlayer player) {
        RefinementData data = get(player);
        int from = data.level;
        data.level = 0;
        data.xp = 0.0;
        data.peak = 0;
        data.hits.clear();
        PlayerSync.markDirty(player);
        if (from != 0) for (LevelListener listener : LISTENERS) listener.onLevelChanged(player, from, 0);
    }
}
