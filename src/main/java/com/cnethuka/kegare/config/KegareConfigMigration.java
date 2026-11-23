package com.cnethuka.kegare.config;

import static java.util.Map.entry;

import com.cnethuka.kegare.Kegare;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One-time fixes for a {@code kegare-server.toml} written by an older version. NeoForge keeps every value in an existing file, so a
 * changed default never reaches it (after the Malice rescale an old file kept Vast's base at 250 and every cost at a quarter).
 * <p>
 * The file's {@code configVersion} says which steps it has been through. A value still at its old default moves to the new one; a
 * value at neither was edited by hand, so it stays and the log says so.
 */
public final class KegareConfigMigration {
    /** The version these numbers are for. A file with no {@code configVersion} reads as 1. */
    public static final int CURRENT_VERSION = 8;

    /** Version 2, the Malice rescale: each value it changed, with its old default. The new value is always the spec's default. */
    static final Map<String, Number> RESCALE_OLD_DEFAULTS = Map.ofEntries(
            // Birth tiers.
            entry("malice.tiers.low.weight", 25),
            entry("malice.tiers.vast.weight", 8),
            entry("malice.tiers.low.base", 60),
            entry("malice.tiers.average.base", 100),
            entry("malice.tiers.high.base", 150),
            entry("malice.tiers.vast.base", 250),
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

    /** Version 3, Severed melee made stronger than standard: each value it changed, with its old default. */
    static final Map<String, Number> SEVERED_MELEE_OLD_DEFAULTS = Map.ofEntries(
            entry("melee.severed.dashCooldown", 4.0),
            entry("melee.severed.dashDistance", 3.5),
            entry("melee.severed.perfectWindow", 0.25),
            entry("melee.severed.guardMeleeReduction", 0.3),
            entry("melee.severed.guardMaxSeconds", 2.5),
            entry("melee.severed.recovery", 1.25)
            // (no strikeDamageBonus or strikeKnockback: those options are removed and NeoForge drops their keys)
    );

    /**
     * Version 4, the Impact System's Malice input (DESIGN §3.23): weights and tiers retuned round it, and battle damage's scale and
     * slams with them (strengths grew) so the same hits break the same blocks.
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

    /** One migration: a file below {@code version} goes through it. {@code renamed}: options that moved, by their old path. */
    record Step(int version, Map<String, Number> oldDefaults, Map<String, Renamed> renamed, String what) {
        Step(int version, Map<String, Number> oldDefaults, String what) {
            this(version, oldDefaults, Map.of(), what);
        }
    }

    /** An option that moved to {@code to}; {@code oldDefault} is what it defaulted to under its old name. */
    record Renamed(String to, double oldDefault) {}

    /** Version 6, efficiency's curve (DESIGN §3.32): square-root weights, not logarithmic. The old values would make RMT heal at x3.6, not x1.9. */
    static final Map<String, Number> EFFICIENCY_CURVE_OLD_DEFAULTS = Map.ofEntries(
            entry("six_eyes.efficiency.attunement", 0.8),
            entry("six_eyes.efficiency.rmtOutputWeight", 0.15));

    /** Version 7, healing others by touch (DESIGN §3.17): the healer's hand on them, not 3 blocks away. */
    static final Map<String, Number> HEAL_BY_TOUCH_OLD_DEFAULTS = Map.ofEntries(
            entry("reverse_malice_technique.others.range", 3.0));

    /**
     * Version 8, Reinforcement renamed Flow (DESIGN §3.16, §3.31), with Background Reinforcement and Sustained Flow (now Steady Output):
     * their options under the new names. NeoForge drops the old keys and writes the new ones at their defaults, so a value set by hand
     * under the old name is carried over; one still at its old default isn't, so the new default applies.
     */
    static final Map<String, Renamed> FLOW_RENAMES = Map.ofEntries(
            entry("reinforcement.onCost", new Renamed("flow.onCost", 40.0)),
            entry("reinforcement.drainPerSecond", new Renamed("flow.drainPerSecond", 10.0)),
            entry("reinforcement.rearmCooldown", new Renamed("flow.rearmCooldown", 1.5)),
            entry("reinforcement.reductionPhysical", new Renamed("flow.reductionPhysical", 0.25)),
            entry("reinforcement.reductionTechnique", new Renamed("flow.reductionTechnique", 0.2)),
            entry("reinforcement.knockbackResistance", new Renamed("flow.knockbackResistance", 0.4)),
            entry("background_tasks.background_reinforcement.backgroundStrength", new Renamed("background_tasks.background_flow.backgroundStrength", 0.4)),
            entry("background_tasks.background_reinforcement.drainPerSecond", new Renamed("background_tasks.background_flow.drainPerSecond", 4.0)),
            entry("background_tasks.sustained_flow.flowReduction", new Renamed("background_tasks.steady_output.upkeepReduction", 0.3)),
            entry("background_tasks.sustained_flow.drainPerSecond", new Renamed("background_tasks.steady_output.drainPerSecond", 1.5)));

    static final List<Step> STEPS = List.of(
            new Step(2, RESCALE_OLD_DEFAULTS, "the rescaled Malice numbers (new tiers, costs and regen)"),
            new Step(3, SEVERED_MELEE_OLD_DEFAULTS, "the stronger Severed melee numbers"),
            new Step(4, MALICE_INPUT_OLD_DEFAULTS, "the Impact System's Malice input (its weights, tiers and battle damage scale)"),
            new Step(5, BLUR_SPEED_OLD_DEFAULTS, "Blur's top speed, kept clearly faster than Accelerate"),
            new Step(6, EFFICIENCY_CURVE_OLD_DEFAULTS, "Six Eyes efficiency's new curve (its Attunement and RMT output weights)"),
            new Step(7, HEAL_BY_TOUCH_OLD_DEFAULTS, "healing others by touch (its range)"),
            new Step(8, Map.of(), FLOW_RENAMES, "Flow's new names (Reinforcement, Background Reinforcement and Sustained Flow renamed)"));

    /** Renamed options set by hand in the file as it was before NeoForge loaded and corrected it (it drops keys it no longer knows). */
    private static volatile Map<String, Double> beforeCorrection = Map.of();

    enum Action { NONE, MOVE_TO_NEW_DEFAULT, KEEP_HAND_EDITED }

    static Action decide(double value, double oldDefault, double newDefault) {
        if (value == newDefault) return Action.NONE;
        return value == oldDefault ? Action.MOVE_TO_NEW_DEFAULT : Action.KEEP_HAND_EDITED;
    }

    private KegareConfigMigration() {}

    /**
     * Reads {@code kegare-server.toml} as it is on disk, before NeoForge loads it (which drops the old names), for the options that
     * moved. Call from the mod's constructor: a server's config loads only when the server starts.
     */
    public static void readBeforeLoading(Path file) {
        if (!Files.isRegularFile(file)) return;
        try {
            beforeCorrection = handSetRenamed(new TomlParser().parse(file, FileNotFoundAction.THROW_ERROR));
        } catch (Exception e) {
            Kegare.LOGGER.warn("kegare-server.toml: couldn't read it for renamed options; they'll start from their defaults", e);
        }
    }

    /** Every renamed option {@code old} sets to something other than its old default, under its new path. */
    static Map<String, Double> handSetRenamed(UnmodifiableConfig old) {
        Map<String, Double> out = new HashMap<>();
        for (Step step : STEPS) {
            for (Map.Entry<String, Renamed> e : step.renamed().entrySet()) {
                if (old.get(e.getKey()) instanceof Number n && n.doubleValue() != e.getValue().oldDefault()) {
                    out.put(e.getValue().to(), n.doubleValue());
                }
            }
        }
        return out;
    }

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
        List<String> carried = new ArrayList<>();
        for (Renamed renamed : step.renamed().values()) {
            Double value = beforeCorrection.get(renamed.to());
            if (value == null) continue;
            @SuppressWarnings("unchecked")
            ModConfigSpec.ConfigValue<Double> to = (ModConfigSpec.ConfigValue<Double>) KegareServerConfig.SPEC.getValues().get(renamed.to());
            if (to == null) continue;
            to.set(value);
            carried.add(renamed.to() + " = " + value);
        }
        if (!carried.isEmpty()) Kegare.LOGGER.info("kegare-server.toml: carried {} hand-set values over to {}: {}", carried.size(), step.what(), carried);
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
