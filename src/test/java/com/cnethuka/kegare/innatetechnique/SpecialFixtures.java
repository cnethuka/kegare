package com.cnethuka.kegare.innatetechnique;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.trait.KegareTraits;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Framework features no real ability uses yet (DESIGN §3.3): a special that needs a toggle switched on, and a charged special that needs
 * a trait. Shared abilities, so no technique has to be registered for them: put in for one test and taken out after, so nothing else
 * sees them. Nothing here exists outside the tests.
 */
final class SpecialFixtures {
    static final double TOGGLE_COST = 20, STEP_COST = 10, STEP_SPECIAL_COST = 32, LIFT_COST = 20, LIFT_SPECIAL_COST = 48;
    static final int STEP_COOLDOWN = 60, LIFT_COOLDOWN = 80, LIFT_SPECIAL_CHARGE = 20;

    private static ResourceLocation id(String path) {
        return Kegare.id("fixture_" + path);
    }

    /** A toggle with no special. */
    static final BaseAbility TOGGLE = new BaseAbility(id("toggle"), CastType.TOGGLE) {
        @Override public double cost(ServerPlayer p) { return TOGGLE_COST; }
        @Override public int cooldownTicks(ServerPlayer p) { return 40; }
        @Override public void execute(AbilityContext ctx) {}
    };

    /** An instant whose special needs {@link #TOGGLE} on. */
    static final BaseAbility STEP = new BaseAbility(id("step"), CastType.INSTANT) {
        @Override public Optional<Ability> special() { return Optional.of(STEP_SPECIAL); }
        @Override public double cost(ServerPlayer p) { return STEP_COST; }
        @Override public int cooldownTicks(ServerPlayer p) { return STEP_COOLDOWN; }
        @Override public void execute(AbilityContext ctx) {}
    };

    static final SpecialAbility STEP_SPECIAL = new SpecialAbility(id("step_special"), CastType.INSTANT, () -> STEP, Requirement.toggleActive(TOGGLE)) {
        @Override public double cost(ServerPlayer p) { return STEP_SPECIAL_COST; }
        @Override public void execute(AbilityContext ctx) {}
    };

    /** A channel whose charged special needs the Six Eyes. */
    static final BaseAbility LIFT = new BaseAbility(id("lift"), CastType.CHANNELED) {
        @Override public Optional<Ability> special() { return Optional.of(LIFT_SPECIAL); }
        @Override public double cost(ServerPlayer p) { return LIFT_COST; }
        @Override public int cooldownTicks(ServerPlayer p) { return LIFT_COOLDOWN; }
        @Override public int maxChannelTicks(ServerPlayer p) { return 100; }
        @Override public void execute(AbilityContext ctx) {}
    };

    static final SpecialAbility LIFT_SPECIAL = new SpecialAbility(id("lift_special"), CastType.CHARGED, () -> LIFT, Requirement.trait(KegareTraits.SIX_EYES)) {
        @Override public double cost(ServerPlayer p) { return LIFT_SPECIAL_COST; }
        @Override public int minChargeTicks(ServerPlayer p) { return 5; }
        @Override public int maxChargeTicks(ServerPlayer p) { return LIFT_SPECIAL_CHARGE; }
        @Override public void execute(AbilityContext ctx) {}
    };

    static final List<Ability> ALL = List.of(TOGGLE, STEP, LIFT);

    private SpecialFixtures() {}

    static void install() {
        for (Ability ability : ALL) if (!SharedAbilities.isShared(ability.id())) SharedAbilities.register(ability);
    }

    @SuppressWarnings("unchecked")
    static void uninstall() {
        try {
            Field field = SharedAbilities.class.getDeclaredField("ALL");
            field.setAccessible(true);
            Map<ResourceLocation, Ability> all = (Map<ResourceLocation, Ability>) field.get(null);
            for (Ability ability : ALL) all.remove(ability.id());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
