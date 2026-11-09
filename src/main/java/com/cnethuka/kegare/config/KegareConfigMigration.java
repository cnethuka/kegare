package com.cnethuka.kegare.config;

import static java.util.Map.entry;

import com.cnethuka.kegare.Kegare;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One-time fixes for a {@code kegare-server.toml} written by an older version. NeoForge keeps every value in an existing file, so a changed
 * default never reaches it: after the Malice rescale, an old file went on saying Vast's base is 250, with every cost a quarter of what it should be.
 * <p>
 * The file's {@code configVersion} says what it has been through. A value still at its old default moves to the new one. A value that's neither
 * was changed by hand, so it stays and the log says so.
 */
public final class KegareConfigMigration {
    /** The version these numbers are for. A file with no {@code configVersion} reads as 1, from before there was one. */
    public static final int CURRENT_VERSION = 5;

    /** Version 2, the Malice rescale: every value it changed, and its default before that. The new value is always the spec's default. */
    static final Map<String, Number> RESCALE_OLD_DEFAULTS = Map.ofEntries(
            // Birth tiers.
            entry("malice.tiers.low.weight", 25),
            entry("malice.tiers.vast.weight", 8),
            entry("malice.tiers.low.base", 60),
            entry("malice.tiers.average.base", 100),
            entry("malice.tiers.high.base", 150),
            entry("malice.tiers.vast.base", 250),
            // The test techniques: costs and upkeep, x4.
            entry("test_abilities.aura.cost", 5.0),
            entry("test_abilities.aura.upkeepPerSecond", 3.0),
            entry("test_abilities.blink.cost", 8.0),
            entry("test_abilities.burst.cost", 15.0),
            entry("test_abilities.dash.cost", 10.0),
            entry("test_abilities.hover.cost", 5.0),
            entry("test_abilities.hover.upkeepPerSecond", 8.0),
            entry("test_abilities.shockwave.cost", 60.0),
            entry("test_abilities.updraft.cost", 12.0),
            // Rime: costs and upkeep, x4.
            entry("rime.frost_field.cost", 8.0),
            entry("rime.frost_field.upkeepPerSecond", 12.0),
            entry("rime.frost_shard.cost", 10.0),
            entry("rime.glacial_lance.cost", 25.0),
            entry("rime.glacial_spire.cost", 30.0),
            entry("rime.ice_path.cost", 8.0),
            entry("rime.ice_path.upkeepPerSecond", 10.0),
            entry("rime.rime_shell.cost", 10.0),
            entry("rime.rime_shell.upkeepPerSecond", 4.0),
            entry("rime.shatter_shell.cost", 8.0),
            entry("rime.shatter_shell.costWithoutShell", 22.0),
            entry("rime.splinter.cost", 14.0),
            // Swap: costs and upkeep, x4.
            entry("swap.anchor.recallCost", 10.0),
            entry("swap.anchor.throwCost", 8.0),
            entry("swap.awareness.cost", 20.0),
            entry("swap.exchange.cost", 15.0),
            entry("swap.mimic.cost", 24.0),
            entry("swap.momentum_exchange.cost", 18.0),
            entry("swap.positions.cost", 12.0),
            entry("swap.send_off.cost", 18.0),
            entry("swap.switchback.cost", 22.0),
            entry("swap.total_swap.cost", 60.0),
            // Puppet Manipulation: costs and upkeep, x4.
            entry("puppet_manipulation.chain_burst.cost", 16.0),
            entry("puppet_manipulation.curio_burst.cost", 8.0),
            entry("puppet_manipulation.directive.cost", 5.0),
            entry("puppet_manipulation.focus.cost", 10.0),
            entry("puppet_manipulation.possess.backlash.malice", 20.0),
            entry("puppet_manipulation.possess.cost", 15.0),
            entry("puppet_manipulation.stand_in.cost", 20.0),
            entry("puppet_manipulation.strings.fireCost", 5.0),
            entry("puppet_manipulation.strings.leashCost", 25.0),
            entry("puppet_manipulation.strings.reelCost", 12.0),
            entry("puppet_manipulation.strings.tieCost", 20.0),
            entry("puppet_manipulation.strings.zipCost", 10.0),
            entry("puppet_manipulation.understudy.cost", 10.0),
            // Reverse Malice Technique: costs and upkeep, x4.
            entry("reverse_malice_technique.cost", 10.0),
            entry("reverse_malice_technique.upkeepPerSecond", 24.0)
    );

    /** Version 3, Severed melee made stronger than standard: every value it changed, and its default before that. */
    static final Map<String, Number> SEVERED_MELEE_OLD_DEFAULTS = Map.ofEntries(
            entry("melee.severed.dashCooldown", 4.0),
            entry("melee.severed.dashDistance", 3.5),
            entry("melee.severed.perfectWindow", 0.25),
            entry("melee.severed.guardMeleeReduction", 0.3),
            entry("melee.severed.guardMaxSeconds", 2.5),
            entry("melee.severed.recovery", 1.25)
            // (strikeDamageBonus and strikeKnockback were here too: the Severed Reinforced Strike is gone, and NeoForge drops its keys)
    );

    /**
     * Version 4, the Impact System's Malice input (DESIGN §3.23): the weights and tiers re-tuned round it, and battle damage's scale and
     * slams with them (strengths grew), so what breaks stays as it was.
     */
    static final Map<String, Number> MALICE_INPUT_OLD_DEFAULTS = Map.ofEntries(
            entry("impact.strength.damageWeight", 1.5),
            entry("impact.strength.knockbackWeight", 1.0),
            entry("impact.strength.chargeWeight", 0.8),
            entry("impact.tiers.medium", 0.6),
            entry("impact.tiers.heavy", 1.1),
            entry("impact.tiers.impactFrame", 2.1),
            entry("battle_damage.strengthScale", 5.0),
            entry("battle_damage.radiusPerStrength", 1.2),
            entry("battle_damage.slams.strengthPerSpeed", 1.0)
    );

    /** Version 5, Blur kept clearly faster than Accelerate's new clock (DESIGN §3.27): its top speed. */
    static final Map<String, Number> BLUR_SPEED_OLD_DEFAULTS = Map.ofEntries(
            entry("severed.blur.speed", 0.56)
    );

    /** One migration: a file below {@code version} goes through it. */
    record Step(int version, Map<String, Number> oldDefaults, String what) {}

    static final List<Step> STEPS = List.of(
            new Step(2, RESCALE_OLD_DEFAULTS, "the rescaled Malice numbers (new tiers, costs and regen)"),
            new Step(3, SEVERED_MELEE_OLD_DEFAULTS, "the stronger Severed melee numbers"),
            new Step(4, MALICE_INPUT_OLD_DEFAULTS, "the Impact System's Malice input (its weights, tiers and battle damage scale)"),
            new Step(5, BLUR_SPEED_OLD_DEFAULTS, "Blur's top speed, kept clearly faster than Accelerate"));

    enum Action { NONE, MOVE_TO_NEW_DEFAULT, KEEP_HAND_EDITED }

    static Action decide(double value, double oldDefault, double newDefault) {
        if (value == newDefault) return Action.NONE;
        return value == oldDefault ? Action.MOVE_TO_NEW_DEFAULT : Action.KEEP_HAND_EDITED;
    }

    private KegareConfigMigration() {}

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() != KegareServerConfig.SPEC) return;
        if (config.getFullPath() == null) return; // a copy sent by a server, which has migrated its own file
        if (KegareServerConfig.CONFIG_VERSION.get() >= CURRENT_VERSION) return;

        int version = KegareServerConfig.CONFIG_VERSION.get();
        for (Step step : STEPS) {
            if (version < step.version()) apply(step);
        }
        KegareServerConfig.CONFIG_VERSION.set(CURRENT_VERSION);
        KegareServerConfig.SPEC.save();
    }

    private static void apply(Step step) {
        int moved = 0;
        List<String> kept = new ArrayList<>();
        for (Map.Entry<String, Number> old : step.oldDefaults().entrySet()) {
            ModConfigSpec.ConfigValue<?> value = KegareServerConfig.SPEC.getValues().get(old.getKey());
            if (value == null) continue; // an option since removed: nothing to move
            double current = ((Number) value.get()).doubleValue();
            double newDefault = ((Number) value.getDefault()).doubleValue();
            switch (decide(current, old.getValue().doubleValue(), newDefault)) {
                case MOVE_TO_NEW_DEFAULT -> {
                    resetToDefault(value);
                    moved++;
                }
                case KEEP_HAND_EDITED -> kept.add(old.getKey() + " = " + value.get());
                case NONE -> {}
            }
        }
        if (moved > 0) Kegare.LOGGER.info("kegare-server.toml: moved {} values to {}", moved, step.what());
        if (!kept.isEmpty()) {
            kept.sort(null);
            Kegare.LOGGER.warn("kegare-server.toml: left {} hand-edited values alone. Check them against {}: {}", kept.size(), step.what(), kept);
        }
    }

    private static <T> void resetToDefault(ModConfigSpec.ConfigValue<T> value) {
        value.set(value.getDefault());
    }
}
