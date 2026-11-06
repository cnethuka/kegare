package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Grade XP and promotions. XP comes mostly from PvP, a little from mobs, and Severed players earn it like anyone.
 * What's left after a promotion carries over, but it never goes past what the next step costs, so XP saved up while an
 * Assessment holds you back can't then skip the step after it. Promotions are private: only the toast tells the player,
 * nothing goes to chat.
 */
public final class GradeService {
    /** Told about every promotion, after it happened. */
    @FunctionalInterface
    public interface PromotionListener {
        void onPromoted(ServerPlayer player, Grade grade);
    }

    private static final List<PromotionListener> LISTENERS = new CopyOnWriteArrayList<>();

    private GradeService() {}

    public static void addPromotionListener(PromotionListener listener) {
        LISTENERS.add(listener);
    }

    public static GradeData get(Player player) {
        return player.getData(KegareAttachments.GRADE);
    }

    public static Grade grade(Player player) {
        return get(player).grade;
    }

    /** The XP the step into {@code target} costs (0 if it takes none). */
    public static double xpCost(Grade target) {
        return target.takesXp() ? ProgressionConfig.XP_TO_GRADE[target.ordinal()].get() : 0.0;
    }

    private static GradeMath.Settings settings() {
        return new GradeMath.Settings(ProgressionConfig.XP_PER_DAMAGE.get(), ProgressionConfig.XP_WIN.get(),
                ProgressionConfig.XP_LOSS.get(), ProgressionConfig.XP_SURVIVE.get(), ProgressionConfig.FIGHT_DECAY.get(),
                ProgressionConfig.FIGHT_DECAY_FLOOR.get(), ProgressionConfig.WEAKER_PER_GRADE.get(),
                ProgressionConfig.WEAKER_FLOOR.get(), ProgressionConfig.WIN_BONUS_PER_GRADE.get(),
                ProgressionConfig.WIN_BONUS_MAX.get(), ProgressionConfig.SURVIVE_MIN_DAMAGE.get());
    }

    /** A fight ended: XP for it, less the more often you've met this opponent lately or the further below you they are. */
    public static void onFightEnded(FightReport report) {
        ServerPlayer player = report.player();
        GradeData data = get(player);
        long now = player.server.overworld().getGameTime();
        long window = ProgressionConfig.ticks(ProgressionConfig.ROLLING_WINDOW);
        // A fight nothing happened in (a landing that never led to damage) isn't a meeting.
        if (report.dealt() <= 0 && report.taken() <= 0) return;
        int earlier = data.fights.count(report.opponent().getUUID(), now, window);
        double xp = GradeMath.fightXp(settings(), report.outcome(), report.dealt(), report.taken(), earlier,
                grade(report.opponent()).stepsAbove(data.grade));
        data.fights.record(report.opponent().getUUID(), now, window);
        addXp(player, xp);
    }

    /** Damage to a mob: a small amount, so solo players progress slowly, and capped per hour so a farm can't replace fighting. */
    public static void onMobDamage(ServerPlayer player, double damage) {
        GradeData data = get(player);
        long now = player.server.overworld().getGameTime();
        if (now - data.mobWindowStart >= ProgressionConfig.ticks(ProgressionConfig.ROLLING_WINDOW)) {
            data.mobWindowStart = now;
            data.mobWindowXp = 0.0;
        }
        double xp = Math.min(damage * ProgressionConfig.XP_PER_MOB_DAMAGE.get(), ProgressionConfig.MOB_XP_PER_WINDOW.get() - data.mobWindowXp);
        if (xp <= 0) return;
        data.mobWindowXp += xp;
        addXp(player, xp);
    }

    /** Adds XP (and promotes if that was the last thing needed). XP does nothing at Grade 1: Special Grade never takes any. */
    public static void addXp(ServerPlayer player, double xp) {
        if (xp <= 0) return;
        GradeData data = get(player);
        Optional<Grade> next = data.grade.next();
        if (next.isEmpty() || !next.get().takesXp()) return;
        data.xp += xp;
        tryPromote(player);
        PlayerSync.markDirty(player);
    }

    /** Promotes as far as XP and Assessment allow. Call whenever either may have changed. */
    public static void tryPromote(ServerPlayer player) {
        GradeData data = get(player);
        for (Optional<Grade> next = data.grade.next(); next.isPresent() && canPromote(player, next.get()); next = data.grade.next()) {
            promote(player, next.get());
        }
        // Nothing beyond the step you're on: XP does nothing at Grade 1 and above, and can't be banked past an Assessment.
        data.xp = Math.min(data.xp, data.grade.next().map(GradeService::xpCost).orElse(0.0));
    }

    private static boolean canPromote(ServerPlayer player, Grade target) {
        if (target.takesXp() && get(player).xp + 1.0e-9 < xpCost(target)) return false;
        return !target.takesAssessment() || requirementMet(player, target);
    }

    /** Part 3 fills this in. */
    private static boolean requirementMet(ServerPlayer player, Grade target) {
        return false;
    }

    private static void promote(ServerPlayer player, Grade target) {
        GradeData data = get(player);
        data.xp = Math.max(0.0, data.xp - xpCost(target));
        data.grade = target;
        PlayerSync.markDirty(player);
        for (PromotionListener listener : LISTENERS) listener.onPromoted(player, target);
    }

    /** Admin: any Grade, up or down (the only thing that can ever lower one). */
    public static void set(ServerPlayer player, Grade grade) {
        GradeData data = get(player);
        boolean up = grade.ordinal() > data.grade.ordinal();
        data.grade = grade;
        data.xp = 0.0;
        PlayerSync.markDirty(player);
        if (up) for (PromotionListener listener : LISTENERS) listener.onPromoted(player, grade);
    }

    /** Admin: back to a fresh Grade 4 with no history. */
    public static void reset(ServerPlayer player) {
        GradeData data = get(player);
        data.grade = Grade.GRADE_4;
        data.xp = 0.0;
        data.fights.clear();
        data.mobWindowStart = 0L;
        data.mobWindowXp = 0.0;
        PlayerSync.markDirty(player);
    }
}
