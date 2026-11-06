package com.cnethuka.kegare.command;

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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /kegare grade|assessment|refinement|insight|progression}. Grade and Assessment are private, so what these say
 * goes only to whoever ran them: never to the other operators, and never to the player.
 */
final class ProgressionCommands {
    private static final DynamicCommandExceptionType UNKNOWN_GRADE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.kegare.grade.unknown", id));
    private static final DynamicCommandExceptionType UNKNOWN_CATEGORY =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.kegare.insight.unknown", id));

    private ProgressionCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("grade")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> gradeGet(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("grade", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Grade.values()).map(Grade::id), b))
                                        .executes(c -> gradeSet(c.getSource(), EntityArgument.getPlayer(c, "player"), grade(StringArgumentType.getString(c, "grade")))))))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                        .executes(c -> gradeAddXp(c.getSource(), EntityArgument.getPlayer(c, "player"), DoubleArgumentType.getDouble(c, "amount")))))));
        root.then(Commands.literal("assessment")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> assessmentGet(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("addfight")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("score", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .then(Commands.argument("opponent", StringArgumentType.word())
                                                .executes(c -> assessmentAddFight(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        DoubleArgumentType.getDouble(c, "score"), DoubleArgumentType.getDouble(c, "score"), StringArgumentType.getString(c, "opponent")))
                                                .then(Commands.argument("techniqueUse", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                        .executes(c -> assessmentAddFight(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                                DoubleArgumentType.getDouble(c, "score"), DoubleArgumentType.getDouble(c, "techniqueUse"), StringArgumentType.getString(c, "opponent")))))))));
        root.then(Commands.literal("refinement")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> refinementGet(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(c -> refinementSet(c.getSource(), EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "level")))))));
        root.then(Commands.literal("insight")
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> insightGet(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(InsightCategory.values()).map(InsightCategory::id), b))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(c -> insightAdd(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        category(StringArgumentType.getString(c, "category")), IntegerArgumentType.getInteger(c, "amount"))))))));
        root.then(Commands.literal("progression")
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> progressionReset(c.getSource(), EntityArgument.getPlayer(c, "player"))))));
    }

    private static Grade grade(String id) throws CommandSyntaxException {
        return Grade.byId(id.toLowerCase(Locale.ROOT)).orElseThrow(() -> UNKNOWN_GRADE.create(id));
    }

    private static InsightCategory category(String id) throws CommandSyntaxException {
        return Arrays.stream(InsightCategory.values()).filter(c -> c.id().equalsIgnoreCase(id)).findFirst().orElseThrow(() -> UNKNOWN_CATEGORY.create(id));
    }

    private static Component name(Grade grade) {
        return Component.translatable(grade.translationKey());
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int gradeGet(CommandSourceStack source, ServerPlayer player) {
        Grade grade = GradeService.grade(player);
        Component next = grade.next().map(ProgressionCommands::name).orElse(Component.translatable("commands.kegare.grade.top"));
        String needed = number(grade.next().map(GradeService::xpCost).orElse(0.0));
        source.sendSuccess(() -> Component.translatable("commands.kegare.grade.get", player.getDisplayName(), name(grade), number(GradeService.get(player).xp()), needed, next), false);
        return grade.ordinal();
    }

    private static int gradeSet(CommandSourceStack source, ServerPlayer player, Grade grade) {
        GradeService.set(player, grade);
        source.sendSuccess(() -> Component.translatable("commands.kegare.grade.set", player.getDisplayName(), name(grade)), false);
        return 1;
    }

    private static int gradeAddXp(CommandSourceStack source, ServerPlayer player, double amount) {
        GradeService.addXp(player, amount);
        source.sendSuccess(() -> Component.translatable("commands.kegare.grade.addxp", number(amount), player.getDisplayName(), name(GradeService.grade(player)), number(GradeService.get(player).xp())), false);
        return 1;
    }

    private static int assessmentGet(CommandSourceStack source, ServerPlayer player) {
        var fights = AssessmentService.recent(player, 10);
        String recent = fights.stream().map(fight -> number(fight.score())).collect(Collectors.joining(", "));
        String technique = fights.stream().map(fight -> number(fight.technique())).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("commands.kegare.assessment.recent", player.getDisplayName(), recent.isEmpty() ? "-" : recent), false);
        source.sendSuccess(() -> Component.translatable("commands.kegare.assessment.recent_technique", technique.isEmpty() ? "-" : technique), false);
        AssessmentRules.Status status = AssessmentService.status(player);
        if (status == null) {
            source.sendSuccess(() -> Component.translatable("commands.kegare.assessment.none"), false);
        } else {
            Component target = GradeService.grade(player).next().map(ProgressionCommands::name).orElse(Component.empty());
            source.sendSuccess(() -> Component.translatable("commands.kegare.assessment.status", target,
                    number(status.average()), number(status.requirement().average()), number(status.technique()), number(status.requirement().technique()),
                    status.fights(), status.requirement().fights(),
                    status.opponents(), status.requirement().opponents(), status.peakRefinement(), status.requirement().peakRefinement(),
                    status.met() ? Component.translatable("commands.kegare.assessment.met") : Component.translatable("commands.kegare.assessment.unmet")), false);
        }
        return 1;
    }

    /** With no Technique use given, it's taken to match the score. */
    private static int assessmentAddFight(CommandSourceStack source, ServerPlayer player, double score, double technique, String opponent) {
        // An online player counts as that player, so these mix with real fights; any other name is a made-up opponent.
        ServerPlayer online = player.server.getPlayerList().getPlayerByName(opponent);
        UUID id = online != null ? online.getUUID() : UUID.nameUUIDFromBytes(("kegare:opponent:" + opponent.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        AssessmentService.addFight(player, score, technique, id);
        source.sendSuccess(() -> Component.translatable("commands.kegare.assessment.addfight", number(score), number(technique), opponent, player.getDisplayName(), name(GradeService.grade(player))), false);
        return 1;
    }

    private static int refinementGet(CommandSourceStack source, ServerPlayer player) {
        RefinementData data = RefinementService.get(player);
        source.sendSuccess(() -> Component.translatable("commands.kegare.refinement.get", player.getDisplayName(), data.level(), RefinementService.maxLevel(),
                number(data.xp()), number(RefinementService.xpForLevel(data.level() + 1)), data.peak()), false);
        return data.level();
    }

    private static int refinementSet(CommandSourceStack source, ServerPlayer player, int level) {
        RefinementService.setLevel(player, level);
        source.sendSuccess(() -> Component.translatable("commands.kegare.refinement.set", player.getDisplayName(), RefinementService.level(player)), false);
        return 1;
    }

    private static int insightGet(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.translatable("commands.kegare.insight.get", player.getDisplayName(),
                Insight.get(player, InsightCategory.DOMINION), Insight.get(player, InsightCategory.WARD), Insight.get(player, InsightCategory.OUTPUT)), false);
        return 1;
    }

    private static int insightAdd(CommandSourceStack source, ServerPlayer player, InsightCategory category, int amount) {
        Insight.add(player, category, amount);
        source.sendSuccess(() -> Component.translatable("commands.kegare.insight.add", amount, Component.translatable(category.translationKey()), player.getDisplayName(), Insight.get(player, category)), false);
        return 1;
    }

    private static int progressionReset(CommandSourceStack source, ServerPlayer player) {
        Progression.reset(player);
        source.sendSuccess(() -> Component.translatable("commands.kegare.progression.reset", player.getDisplayName()), false);
        return 1;
    }
}
