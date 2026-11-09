package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.listEntry;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.typedName;

import com.cnethuka.kegare.client.gui.GuiDraw;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * {@code /kegare help [system]}, the one command anyone can use. It lists only what the sender may run. Every command is in
 * {@link #ENTRIES}, and a test holds that list to the real command tree.
 */
final class HelpCommand {
    /**
     * @param usage after {@code /kegare }; the first word is its system
     * @param description lang key
     * @param permission the level it takes
     * @param devClient lives in the dev client's own command tree, so it isn't on a server and isn't in a released game
     */
    record Entry(String usage, String description, int permission, boolean devClient) {
        String system() {
            int space = usage.indexOf(' ');
            return space < 0 ? usage : usage.substring(0, space);
        }

        /** The usage without its system. */
        String rest() {
            return usage.substring(system().length()).trim();
        }

        /** What clicking it puts in the chat box: the command up to its first argument. */
        String typedSoFar() {
            int bracket = usage.indexOf('<');
            int optional = usage.indexOf('[');
            int first = bracket < 0 ? optional : optional < 0 ? bracket : Math.min(bracket, optional);
            return "/kegare " + (first < 0 ? usage : usage.substring(0, first));
        }

        boolean visibleTo(CommandSourceStack source) {
            return source.hasPermission(permission) && (!devClient || !FMLEnvironment.production);
        }
    }

    private static Entry entry(String usage, String description) {
        return new Entry(usage, description, CommandArgs.PERMISSION, false);
    }

    private static Entry devClient(String usage, String description) {
        return new Entry(usage, description, CommandArgs.PERMISSION, true);
    }

    static final List<Entry> ENTRIES = List.of(
            new Entry("help [system]", "commands.kegare.help.help", 0, false),
            entry("info [player]", "commands.kegare.help.info"),
            entry("innate list", "commands.kegare.help.innate.list"),
            entry("innate get [player]", "commands.kegare.help.innate.get"),
            entry("innate set <player> <innate>", "commands.kegare.help.innate.set"),
            entry("innate reroll [player]", "commands.kegare.help.innate.reroll"),
            entry("trait list", "commands.kegare.help.trait.list"),
            entry("trait get [player]", "commands.kegare.help.trait.get"),
            entry("trait give <player> <trait>", "commands.kegare.help.trait.give"),
            entry("trait remove <player> <trait>", "commands.kegare.help.trait.remove"),
            entry("trait release <trait>", "commands.kegare.help.trait.release"),
            entry("trait status <trait>", "commands.kegare.help.trait.status"),
            entry("malice get [player]", "commands.kegare.help.malice.get"),
            entry("malice set <player> <amount>", "commands.kegare.help.malice.set"),
            entry("malice fill [player]", "commands.kegare.help.malice.fill"),
            entry("malice capacity <player> <amount>", "commands.kegare.help.malice.capacity"),
            entry("rebirth <player> [capacity]", "commands.kegare.help.rebirth"),
            entry("loadout reset [player]", "commands.kegare.help.loadout.reset"),
            entry("cooldown reset [player]", "commands.kegare.help.cooldown.reset"),
            entry("cooldown reset <player> <ability>", "commands.kegare.help.cooldown.reset_one"),
            entry("grade list", "commands.kegare.help.grade.list"),
            entry("grade get [player]", "commands.kegare.help.grade.get"),
            entry("grade set <player> <grade>", "commands.kegare.help.grade.set"),
            entry("grade add <player> <amount>", "commands.kegare.help.grade.add"),
            entry("assessment get [player]", "commands.kegare.help.assessment.get"),
            entry("assessment add <player> <score> <opponent> [techniqueUse]", "commands.kegare.help.assessment.add"),
            entry("refinement get [player]", "commands.kegare.help.refinement.get"),
            entry("refinement set <player> <level>", "commands.kegare.help.refinement.set"),
            entry("insight get [player]", "commands.kegare.help.insight.get"),
            entry("insight add <player> <category> <amount>", "commands.kegare.help.insight.add"),
            entry("progression reset <player>", "commands.kegare.help.progression.reset"),
            entry("debug stun <player> <seconds>", "commands.kegare.help.debug.stun"),
            entry("debug pity <player> <rolls>", "commands.kegare.help.debug.pity"),
            entry("debug reroll <player> <innate>", "commands.kegare.help.debug.reroll"),
            entry("debug shared unlock <player> <ability>", "commands.kegare.help.debug.shared.unlock"),
            entry("debug shared lock <player> <ability>", "commands.kegare.help.debug.shared.lock"),
            entry("debug task unlock <player> <task>", "commands.kegare.help.debug.task.unlock"),
            entry("debug task lock <player> <task>", "commands.kegare.help.debug.task.lock"),
            entry("debug orb spawn [player]", "commands.kegare.help.debug.orb.spawn"),
            entry("debug orb status [player]", "commands.kegare.help.debug.orb.status"),
            entry("debug swap unswappable <player> <on>", "commands.kegare.help.debug.swap"),
            entry("debug impact <player> <tier> <role> <kind>", "commands.kegare.help.debug.impact"),
            entry("debug impact log <on>", "commands.kegare.help.debug.impact.log"),
            entry("debug screen <player> <effect> <seconds>", "commands.kegare.help.debug.screen"),
            entry("debug battle restore", "commands.kegare.help.debug.battle.restore"),
            entry("debug spark log on", "commands.kegare.help.debug.spark.log"),
            entry("debug spark log off", "commands.kegare.help.debug.spark.log.off"),
            entry("debug spark force <player>", "commands.kegare.help.debug.spark.force"),
            entry("debug spark zone <player> <seconds>", "commands.kegare.help.debug.spark.zone"),
            entry("debug rmt fatigue <player> <amount>", "commands.kegare.help.debug.rmt.fatigue"),
            entry("debug rmt others unlock <player>", "commands.kegare.help.debug.rmt.others.unlock"),
            entry("debug rmt others lock <player>", "commands.kegare.help.debug.rmt.others.lock"),
            entry("debug rmt moment <player> <target>", "commands.kegare.help.debug.rmt.moment"),
            entry("debug strings unmovable <player> <on>", "commands.kegare.help.debug.strings"),
            devClient("debug fx export", "commands.kegare.help.debug.fx.export"),
            devClient("debug fx play <name> [where]", "commands.kegare.help.debug.fx.play"));

    private static final DynamicCommandExceptionType UNKNOWN_SYSTEM =
            new DynamicCommandExceptionType(typed -> Component.translatable("commands.kegare.help.unknown", String.valueOf(typed)));

    private HelpCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("help")
                .executes(c -> overview(c.getSource()))
                .then(CommandArgs.name("system")
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(bySystem(c.getSource()).keySet(), b))
                        .executes(c -> {
                            String typed = typedName(c, "system");
                            List<Entry> entries = bySystem(c.getSource()).get(typed.toLowerCase(Locale.ROOT));
                            if (entries == null) throw UNKNOWN_SYSTEM.create(typed);
                            return system(c.getSource(), entries);
                        })));
    }

    /** What this sender can run, by system, in the order of {@link #ENTRIES}. */
    private static Map<String, List<Entry>> bySystem(CommandSourceStack source) {
        Map<String, List<Entry>> systems = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (entry.visibleTo(source)) systems.computeIfAbsent(entry.system(), s -> new ArrayList<>()).add(entry);
        }
        return systems;
    }

    /** One line per system, because every command at once would run off the top of the chat. */
    private static int overview(CommandSourceStack source) {
        say(source, Component.translatable("commands.kegare.help.header").withColor(GuiDraw.GOLD & 0xFFFFFF));
        Map<String, List<Entry>> systems = bySystem(source);
        systems.forEach((system, entries) -> {
            MutableComponent line = (MutableComponent) listEntry(usage("/kegare " + system), actions(entries));
            say(source, line.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/kegare help " + system))));
        });
        return systems.size();
    }

    private static int system(CommandSourceStack source, List<Entry> entries) {
        say(source, Component.translatable("commands.kegare.help.header.system", entries.get(0).system()).withColor(GuiDraw.GOLD & 0xFFFFFF));
        for (Entry entry : entries) {
            Component line = Component.translatable("commands.kegare.entry", usage("/kegare " + entry.usage()), Component.translatable(entry.description()).withColor(GuiDraw.GRAY & 0xFFFFFF))
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, entry.typedSoFar())));
            say(source, line);
        }
        return entries.size();
    }

    private static Component usage(String text) {
        return Component.literal(text).withColor(GuiDraw.AQUA & 0xFFFFFF);
    }

    /** "list, get, set, reroll"; a system with one command shows its arguments instead. */
    private static String actions(List<Entry> entries) {
        if (entries.size() == 1) return entries.get(0).rest();
        Set<String> actions = new LinkedHashSet<>();
        for (Entry entry : entries) actions.add(entry.rest().split(" ")[0]);
        return String.join(", ", actions);
    }
}
