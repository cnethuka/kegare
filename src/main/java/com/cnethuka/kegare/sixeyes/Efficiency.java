package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.rmt.RmtOutput;
import net.minecraft.server.level.ServerPlayer;

/**
 * Near-zero waste (DESIGN §3.32): every Malice cost a bearer pays (casts, upkeep, toggles, Background Tasks, Reinforcement, RMT) is
 * multiplied by 1 / (1 + x), where x = {@code base} + {@code grade} x their Grade step (0 at Grade 4, 6 at Special Grade) + {@code attunement}
 * x sqrt(Attunement / {@code attunementScale}). x climbs without end and with no flat point, so the cost keeps falling towards free and never
 * gets there, with no cap in config (the weights are). Every cast still costs at least 1 Malice. Eyewear and Strain never touch it.
 * <p>
 * RMT's output (§3.17) follows the same curve, x (1 + {@code rmtOutputWeight} x x), past the normal 1.6x with no fixed cap, after injuries have
 * taken their share.
 */
public final class Efficiency {
    private Efficiency() {}

    static void init() {
        MaliceService.addCostModifier((player, amount) -> SixEyes.isBearer(player) ? amount * costFactor(player) : amount);
        RmtOutput.addModifier((player, output) -> SixEyes.isBearer(player) ? output * (1.0 + SixEyesConfig.RMT_OUTPUT_WEIGHT.get() * x(player)) : output);
    }

    /** The curve's input for this bearer. */
    public static double x(ServerPlayer player) {
        return x(GradeService.grade(player).ordinal(), Attunement.get(player));
    }

    /** The curve's input for a Grade step and an Attunement. */
    public static double x(int gradeStep, double attunement) {
        return SixEyesConfig.EFFICIENCY_BASE.get() + SixEyesConfig.EFFICIENCY_GRADE.get() * gradeStep
                + SixEyesConfig.EFFICIENCY_ATTUNEMENT.get() * Math.sqrt(Math.max(0, attunement) / SixEyesConfig.ATTUNEMENT_SCALE.get());
    }

    /** What a cost is multiplied by: above 0 always, however far they've come. */
    public static double costFactor(ServerPlayer player) {
        return costFactor(x(player));
    }

    public static double costFactor(double x) {
        return 1.0 / (1.0 + x);
    }

    /** RMT's output multiplier for this bearer. */
    public static double rmtFactor(ServerPlayer player) {
        return 1.0 + SixEyesConfig.RMT_OUTPUT_WEIGHT.get() * x(player);
    }
}
