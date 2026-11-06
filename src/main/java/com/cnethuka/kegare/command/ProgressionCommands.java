package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.forEachPlayer;
import static com.cnethuka.kegare.command.CommandArgs.listEntry;
import static com.cnethuka.kegare.command.CommandArgs.name;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.senderOr;
import static com.cnethuka.kegare.command.CommandArgs.suggest;
import static com.cnethuka.kegare.command.CommandArgs.system;
import static com.cnethuka.kegare.command.CommandArgs.typedName;

import com.cnethuka.kegare.progression.AssessmentRules;
import com.cnethuka.kegare.progression.AssessmentService;
import com.cnethuka.kegare.progression.Grade;
import com.cnethuka.kegare.progression.GradeService;
import com.cnethuka.kegare.progression.Insight;
import com.cnethuka.kegare.progression.InsightCategory;
import com.cnethuka.kegare.progression.Progression;
import com.cnethuka.kegare.progression.RefinementData;
import com.cnethuka.kegare.progression.RefinementService;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /kegare grade|assessment|refinement|insight|progression}. Grade and Assessment are private, so what these say
 * goes only to whoever ran them: never to the other operators, and never to the player.
 */
final class ProgressionCommands {
    private static final DynamicCommandExceptionType UNKNOWN_GRADE =
            new DynamicCommandExceptionType(typed -> Component.translatable("commands.kegare.grade.unknown", String.valueOf(typed)));
    private static final Dynamic2CommandExceptionType UNKNOWN_CATEGORY = new Dynamic2CommandExceptionType(
            (typed, categories) -> Component.translatable("commands.kegare.insight.unknown", String.valueOf(typed), String.valueOf(categories)));

    private ProgressionCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("grade")
                .then(Commands.literal("list").executes(c -> gradeList(c.getSource())))
                .then(senderOr(Commands.literal("get"), ProgressionCommands::gradeGet))
                .then(Commands.literal("set")
                        .then(player()
                                .then(name("grade")
                                        .suggests((c, b) -> suggest(b, List.of(Grade.values()), Grade::id, ProgressionCommands::gradeName))
                                        .executes(c -> {
                                            Grade grade = grade(c);
                                            return forEachPlayer(c, (source, player) -> gradeSet(source, player, grade));
                                        }))))
                .then(Commands.literal("add")
                        .then(player()
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .executes(c -> {
                                            double amount = DoubleArgumentType.getDouble(c, "amount");
                                            return forEachPlayer(c, (source, player) -> gradeAdd(source, player, amount));
                                        })))));
        root.then(system("assessment")
                .then(senderOr(Commands.literal("get"), ProgressionCommands::assessmentGet))
                .then(Commands.literal("add")
                        .then(player()
                                .then(Commands.argument("score", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .then(Commands.argument("opponent", StringArgumentType.word())
                                                .executes(c -> {
                                                    double score = DoubleArgumentType.getDouble(c, "score");
                                                    return assessmentAdd(c, score, score);
                                                })
                                                .then(Commands.argument("techniqueUse", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                        .executes(c -> assessmentAdd(c, DoubleArgumentType.getDouble(c, "score"), DoubleArgumentType.getDouble(c, "techniqueUse")))))))));
        root.then(system("refinement")
                .then(senderOr(Commands.literal("get"), ProgressionCommands::refinementGet))
                .then(Commands.literal("set")
                        .then(player()
                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(c -> {
                                            int level = IntegerArgumentType.getInteger(c, "level");
                                            return forEachPlayer(c, (source, player) -> refinementSet(source, player, level));
                                        })))));
        root.then(system("insight")
                .then(senderOr(Commands.literal("get"), ProgressionCommands::insightGet))
                .then(Commands.literal("add")
                        .then(player()
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests((c, b) -> suggest(b, List.of(InsightCategory.values()), InsightCategory::id, category -> Component.translatable(category.translationKey())))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(c -> {
                                                    InsightCategory category = category(StringArgumentType.getString(c, "category"));
                                                    int amount = IntegerArgumentType.getInteger(c, "amount");
                                                    return forEachPlayer(c, (source, player) -> insightAdd(source, player, category, amount));
                                                }))))));
        // The whole wipe takes a name: leaving it out shouldn't be able to wipe the sender by accident.
        root.then(system("progression")
                .then(Commands.literal("reset")
                        .then(player(ProgressionCommands::progressionReset))));
    }

    private static Grade grade(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String typed = typedName(c, "grade");
        return Grade.byId(typed.toLowerCase(Locale.ROOT)).orElseThrow(() -> UNKNOWN_GRADE.create(typed));
    }

    private static InsightCategory category(String typed) throws CommandSyntaxException {
        return Arrays.stream(InsightCategory.values()).filter(c -> c.id().equalsIgnoreCase(typed)).findFirst()
                .orElseThrow(() -> UNKNOWN_CATEGORY.create(typed, Arrays.stream(InsightCategory.values()).map(InsightCategory::id).collect(Collectors.joining(", "))));
    }

    private static Component gradeName(Grade grade) {
        return Component.translatable(grade.translationKey());
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int gradeList(CommandSourceStack source) {
        say(source, "commands.kegare.grade.list");
        for (Grade grade : Grade.values()) say(source, listEntry(gradeName(grade), grade.id()));
        return Grade.values().length;
    }

    private static int gradeGet(CommandSourceStack source, ServerPlayer player) {
        Grade grade = GradeService.grade(player);
        String xp = number(GradeService.get(player).xp());
        grade.next().ifPresentOrElse(
                next -> say(source, "commands.kegare.grade.get", player.getDisplayName(), gradeName(grade), xp, number(GradeService.xpCost(next)), gradeName(next)),
                () -> say(source, "commands.kegare.grade.get.top", player.getDisplayName(), gradeName(grade), xp));
        return grade.ordinal();
    }

    private static int gradeSet(CommandSourceStack source, ServerPlayer player, Grade grade) {
        GradeService.set(player, grade);
        say(source, "commands.kegare.grade.set", player.getDisplayName(), gradeName(grade));
        return 1;
    }

    private static int gradeAdd(CommandSourceStack source, ServerPlayer player, double amount) {
        GradeService.addXp(player, amount);
        say(source, "commands.kegare.grade.add", number(amount), player.getDisplayName(), gradeName(GradeService.grade(player)), number(GradeService.get(player).xp()));
        return 1;
    }

    private static int assessmentGet(CommandSourceStack source, ServerPlayer player) {
        var fights = AssessmentService.recent(player, 10);
        String recent = fights.stream().map(fight -> number(fight.score())).collect(Collectors.joining(", "));
        String technique = fights.stream().map(fight -> number(fight.technique())).collect(Collectors.joining(", "));
        say(source, "commands.kegare.assessment.recent", player.getDisplayName(), recent.isEmpty() ? "-" : recent);
        say(source, "commands.kegare.assessment.recent_technique", technique.isEmpty() ? "-" : technique);
        AssessmentRules.Status status = AssessmentService.status(player);
        if (status == null) {
            say(source, "commands.kegare.assessment.none");
        } else {
            Component target = GradeService.grade(player).next().map(ProgressionCommands::gradeName).orElse(Component.empty());
            say(source, "commands.kegare.assessment.status", target,
                    number(status.average()), number(status.requirement().average()), number(status.technique()), number(status.requirement().technique()),
                    status.fights(), status.requirement().fights(),
                    status.opponents(), status.requirement().opponents(), status.peakRefinement(), status.requirement().peakRefinement(),
                    status.met() ? Component.translatable("commands.kegare.assessment.met") : Component.translatable("commands.kegare.assessment.unmet"));
        }
        return 1;
    }

    /** With no Technique use given, it's taken to match the score. */
    private static int assessmentAdd(CommandContext<CommandSourceStack> c, double score, double technique) throws CommandSyntaxException {
        String opponent = StringArgumentType.getString(c, "opponent");
        return forEachPlayer(c, (source, player) -> {
            // An online player counts as that player, so these mix with real fights; any other name is a made-up opponent.
            ServerPlayer online = player.server.getPlayerList().getPlayerByName(opponent);
            UUID id = online != null ? online.getUUID() : UUID.nameUUIDFromBytes(("kegare:opponent:" + opponent.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            AssessmentService.addFight(player, score, technique, id);
            say(source, "commands.kegare.assessment.add", number(score), number(technique), opponent, player.getDisplayName(), gradeName(GradeService.grade(player)));
            return 1;
        });
    }

    private static int refinementGet(CommandSourceStack source, ServerPlayer player) {
        RefinementData data = RefinementService.get(player);
        say(source, "commands.kegare.refinement.get", player.getDisplayName(), data.level(), RefinementService.maxLevel(),
                number(data.xp()), number(RefinementService.xpForLevel(data.level() + 1)), data.peak());
        return data.level();
    }

    private static int refinementSet(CommandSourceStack source, ServerPlayer player, int level) {
        RefinementService.setLevel(player, level);
        say(source, "commands.kegare.refinement.set", player.getDisplayName(), RefinementService.level(player));
        return 1;
    }

    private static int insightGet(CommandSourceStack source, ServerPlayer player) {
        say(source, "commands.kegare.insight.get", player.getDisplayName(),
                Insight.get(player, InsightCategory.DOMINION), Insight.get(player, InsightCategory.WARD), Insight.get(player, InsightCategory.OUTPUT));
        return 1;
    }

    private static int insightAdd(CommandSourceStack source, ServerPlayer player, InsightCategory category, int amount) {
        Insight.add(player, category, amount);
        say(source, "commands.kegare.insight.add", amount, Component.translatable(category.translationKey()), player.getDisplayName(), Insight.get(player, category));
        return 1;
    }

    private static int progressionReset(CommandSourceStack source, ServerPlayer player) {
        Progression.reset(player);
        say(source, "commands.kegare.progression.reset", player.getDisplayName());
        return 1;
    }
}
