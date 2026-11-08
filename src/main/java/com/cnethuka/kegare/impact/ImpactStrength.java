package com.cnethuka.kegare.impact;

/**
 * A hit's strength, worked out from what it actually did (DESIGN §3.23), and the tier that strength falls in. The same number decides
 * how hard it looks and feels and how much of the world it breaks. Pure arithmetic: the numbers come from {@link ImpactConfig}.
 */
public final class ImpactStrength {
    private ImpactStrength() {}

    /**
     * Knockback that came in several pushes in one tick, as vanilla stacks them: each push halves what was there and adds its own
     * strength ({@code LivingEntity.knockback}). A hit's own 0.4 then a sprint's 0.5 is 0.7, not 0.9.
     */
    public static double stack(double sofar, double push) {
        return sofar * 0.5 + push;
    }

    /**
     * @param damageShare damage dealt as a share of the victim's max health (a Total Swap: the shares of health it moved)
     * @param knockback   the knockback it landed with ({@link #stack}ed)
     * @param launch      the upward speed it launched them at (blocks/tick), for hits that throw rather than push
     * @param charge      the charge it was cast at, 0 to 1
     */
    public static double of(double damageShare, double knockback, double launch, double charge) {
        return of(damageShare, knockback, launch, charge, ImpactConfig.DAMAGE_WEIGHT.get(), ImpactConfig.KNOCKBACK_WEIGHT.get(),
                ImpactConfig.BASE_KNOCKBACK.get(), ImpactConfig.LAUNCH_WEIGHT.get(), ImpactConfig.CHARGE_WEIGHT.get());
    }

    public static double of(double damageShare, double knockback, double launch, double charge, double damageWeight,
                            double knockbackWeight, double baseKnockback, double launchWeight, double chargeWeight) {
        return damageWeight * Math.max(0, damageShare)
                + knockbackWeight * Math.max(0, knockback - baseKnockback)
                + launchWeight * Math.max(0, launch)
                + chargeWeight * Math.max(0, Math.min(1, charge));
    }

    public static ImpactTier tier(double strength) {
        return tier(strength, ImpactConfig.MEDIUM.get(), ImpactConfig.HEAVY.get(), ImpactConfig.IMPACT_FRAME.get());
    }

    /** The highest tier whose threshold it reaches. Never {@link ImpactTier#TOP}: that one is asked for, never earned. */
    public static ImpactTier tier(double strength, double medium, double heavy, double impactFrame) {
        if (strength >= impactFrame) return ImpactTier.IMPACT_FRAME;
        if (strength >= heavy) return ImpactTier.HEAVY;
        if (strength >= medium) return ImpactTier.MEDIUM;
        return ImpactTier.LIGHT;
    }
}
