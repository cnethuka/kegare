package com.cnethuka.kegare.progression;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Scores qualifying fights and decides whether a player has earned the Grades that take an Assessment. Promotion depends
 * on how well a player uses their technique: wins and kills add nothing, so Special Grade can be reached without ever
 * winning. Private, and it never lowers a Grade.
 */
public final class AssessmentService {
    private AssessmentService() {}

    public static AssessmentData get(Player player) {
        return player.getData(KegareAttachments.ASSESSMENT);
    }

    public static AssessmentScoring.Settings settings() {
        return new AssessmentScoring.Settings(
                new AssessmentScoring.Weights(ProgressionConfig.WEIGHT_TECHNIQUE.get(), ProgressionConfig.WEIGHT_EFFICIENCY.get(),
                        ProgressionConfig.WEIGHT_PERFORMANCE.get(), ProgressionConfig.WEIGHT_OPPONENT.get()),
                new AssessmentScoring.TechniqueWeights(ProgressionConfig.TECHNIQUE_DISTINCT.get(), ProgressionConfig.TECHNIQUE_SPECIALS.get(),
                        ProgressionConfig.TECHNIQUE_MIXING.get(), ProgressionConfig.TECHNIQUE_COMBOS.get()),
                ProgressionConfig.DISTINCT_FULL_SHARE.get(), ProgressionConfig.SPAM_FREE_SHARE.get(), ProgressionConfig.SPAM_PENALTY.get(),
                ProgressionConfig.COMBO_TARGET.get(), ProgressionConfig.ticks(ProgressionConfig.COMBO_WINDOW),
                ProgressionConfig.EFFICIENCY_FULL.get(), ProgressionConfig.PERFORMANCE_FULL_SHARE.get(),
                ProgressionConfig.OPPONENT_EVEN.get(), ProgressionConfig.OPPONENT_PER_ABOVE.get(),
                ProgressionConfig.OPPONENT_PER_BELOW.get(), ProgressionConfig.OPPONENT_FLOOR.get(), ProgressionConfig.SEVERED_FULL_MOVES.get());
    }

    /** Long enough, and both players dealt real damage. */
    public static boolean qualifies(Fight fight, UUID a, UUID b) {
        double min = ProgressionConfig.QUALIFY_MIN_DAMAGE.get();
        return fight.lengthTicks() >= ProgressionConfig.ticks(ProgressionConfig.QUALIFY_MIN_LENGTH)
                && fight.side(a).dealt() >= min && fight.side(b).dealt() >= min;
    }

    /** A fight's score, and the Technique use in it (a Severed player's melee variety), both 0-100. */
    public record Assessed(double score, double technique) {}

    /** The score of a finished fight for this player. */
    public static double score(FightReport report) {
        return assess(report).score();
    }

    /** {@link FightReport#outcome()} is deliberately not read. */
    public static Assessed assess(FightReport report) {
        ServerPlayer player = report.player();
        AssessmentScoring.Settings s = settings();
        Fight.Side side = report.side();
        double performance = AssessmentScoring.performance(report.dealt(), report.taken(), s);
        double opponent = AssessmentScoring.opponent(GradeService.grade(player), GradeService.grade(report.opponent()), s);
        if (MaliceService.isSevered(player)) {
            double variety = AssessmentScoring.meleeVariety(side.melee(), s);
            return new Assessed(AssessmentScoring.severedScore(variety, performance, opponent, s), 100.0 * variety);
        }
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        double techniqueUse = technique.map(t -> AssessmentScoring.techniqueUse(side.abilities(),
                TechniqueUnlocks.unlockedBase(player, t), TechniqueUnlocks.unlockedSpecials(player, t), TechniqueUnlocks.scoringKit(player, t), s)).orElse(0.0);
        double efficiency = AssessmentScoring.efficiency(report.dealt(), side.baseMalice(), s);
        double score = AssessmentScoring.score(techniqueUse, efficiency, performance, opponent, s);
        // For admins tuning the weights. Debug only: Assessment is private.
        if (Kegare.LOGGER.isDebugEnabled()) {
            Kegare.LOGGER.debug("Assessment of {} v {}: {} = technique {} (of {}) efficiency {} ({} dealt per {} base Malice) performance {} ({} taken) opponent {}; landed {}",
                    player.getScoreboardName(), report.opponent().getScoreboardName(), String.format("%.1f", score), String.format("%.2f", techniqueUse),
                    technique.map(t -> TechniqueUnlocks.scoringKit(player, t)).orElse(null), String.format("%.2f", efficiency),
                    String.format("%.1f", report.dealt()), String.format("%.0f", side.baseMalice()), String.format("%.2f", performance), String.format("%.1f", report.taken()), String.format("%.2f", opponent),
                    side.abilities().stream().map(use -> use.id().replace("kegare:", "")).toList());
        }
        return new Assessed(score, 100.0 * techniqueUse);
    }

    public static void onFightEnded(FightReport report) {
        if (!qualifies(report.fight(), report.player().getUUID(), report.opponent().getUUID())) return;
        if (!MaliceService.get(report.player()).isBorn()) return;
        Assessed assessed = assess(report);
        addFight(report.player(), assessed.score(), assessed.technique(), report.opponent().getUUID());
    }

    /** An admin's made-up fight, with no separate Technique use: it's taken to match the score. */
    public static void addFight(ServerPlayer player, double score, UUID opponent) {
        addFight(player, score, score, opponent);
    }

    /** Records a scored fight (admin: any numbers, any opponent) and promotes if that was the last thing needed. */
    public static void addFight(ServerPlayer player, double score, double technique, UUID opponent) {
        get(player).add(new AssessmentRules.Entry(clamp(score), clamp(technique), opponent, player.server.overworld().getGameTime()));
        GradeService.tryPromote(player);
        PlayerSync.markDirty(player);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    /** What a Grade's Assessment asks for; null for a Grade that takes none. Peak can't ask for more than the maximum level. */
    @Nullable
    public static AssessmentRules.Requirement requirement(Grade target) {
        if (!target.takesAssessment()) return null;
        return ProgressionConfig.requirement(target);
    }

    /** Where this player stands against the next Grade's Assessment; null if the next step takes none. */
    @Nullable
    public static AssessmentRules.Status status(ServerPlayer player) {
        return GradeService.grade(player).next().map(next -> status(player, next)).orElse(null);
    }

    @Nullable
    public static AssessmentRules.Status status(ServerPlayer player, Grade target) {
        AssessmentRules.Requirement requirement = requirement(target);
        if (requirement == null) return null;
        return AssessmentRules.evaluate(get(player).history, requirement, RefinementService.peak(player), MaliceService.isSevered(player));
    }

    public static boolean requirementMet(ServerPlayer player, Grade target) {
        AssessmentRules.Status status = status(player, target);
        return status != null && status.met();
    }

    /** The latest fights, oldest first. For admins only: Assessment is private. */
    public static java.util.List<AssessmentRules.Entry> recent(ServerPlayer player, int count) {
        java.util.List<AssessmentRules.Entry> history = get(player).history;
        return java.util.List.copyOf(history.subList(Math.max(0, history.size() - count), history.size()));
    }

    /** Admin: forget every fight. */
    public static void reset(ServerPlayer player) {
        get(player).history.clear();
        PlayerSync.markDirty(player);
    }
}
