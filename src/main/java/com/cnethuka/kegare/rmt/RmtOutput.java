package com.cnethuka.kegare.rmt;

import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.RefinementService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;

/**
 * How strongly a player's Reverse Malice Technique heals (DESIGN §3.17): {@code healPerSecond} times this. In canon, how fast reverse
 * cursed energy heals rides on the user's output, so this is a deliberate exception to §3.21's rule that progression only unlocks
 * things: output = 1 + a Grade bonus (0 at Grade 4, {@code output.gradeBonus} at Special Grade, evenly up the ladder) + a Peak
 * Refinement bonus (0 at 0, {@code output.peakBonus} at the top level). Peak, not Refinement: Refinement resets on a reroll, and RMT
 * belongs to the player, not the technique. Then injuries drag it down ({@link RmtFatigue}), and later systems can change it
 * ({@link #addModifier}: Six Eyes, Black Spark's zone).
 */
public final class RmtOutput {
    /** Something that changes a player's output, after Grade, Peak and injuries: given what it is so far, returns what it is now. */
    @FunctionalInterface
    public interface Modifier {
        double modify(ServerPlayer player, double output);
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();

    private RmtOutput() {}

    /** For other systems (Six Eyes, Black Spark's zone). Applied in the order added. */
    public static void addModifier(Modifier modifier) {
        MODIFIERS.add(modifier);
    }

    /** For tests. */
    static void removeModifier(Modifier modifier) {
        MODIFIERS.remove(modifier);
    }

    public static double gradeBonus(Grade grade) {
        int top = Grade.values().length - 1;
        return RmtConfig.OUTPUT_GRADE_BONUS.get() * grade.ordinal() / top;
    }

    public static double peakBonus(int peak) {
        int top = Math.max(1, RefinementService.maxLevel());
        return RmtConfig.OUTPUT_PEAK_BONUS.get() * Math.min(peak, top) / top;
    }

    /** From Grade and Peak Refinement alone: what injuries pull down from. */
    public static double base(ServerPlayer player) {
        return 1.0 + gradeBonus(GradeService.grade(player)) + peakBonus(RefinementService.peak(player));
    }

    /** What their healing runs at right now: Grade and Peak, less what injuries take ({@link RmtFatigue}), then any modifiers. */
    public static double of(ServerPlayer player) {
        double output = base(player) * (1.0 - RmtFatigue.of(player));
        for (Modifier modifier : MODIFIERS) output = modifier.modify(player, output);
        return Math.max(0.0, output);
    }
}
