package com.cnethuka.kegare.command;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.Trait;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * What every {@code /kegare} command shares: the permission, player arguments (selectors work, and a last {@code [player]}
 * defaults to the sender), short names for innate techniques and traits, and how results read in chat.
 */
final class CommandArgs {
    /** Everything but {@code /kegare help}. */
    static final int PERMISSION = 2;

    private static final String PLAYER = "player";

    private static final DynamicCommandExceptionType NO_PLAYER =
            new DynamicCommandExceptionType(typed -> Component.translatable("commands.kegare.no_player", String.valueOf(typed)));
    private static final DynamicCommandExceptionType UNKNOWN_INNATE =
            new DynamicCommandExceptionType(typed -> Component.translatable("commands.kegare.innate.unknown", String.valueOf(typed)));
    private static final DynamicCommandExceptionType UNKNOWN_TRAIT =
            new DynamicCommandExceptionType(typed -> Component.translatable("commands.kegare.trait.unknown", String.valueOf(typed)));

    static final SuggestionProvider<CommandSourceStack> INNATES =
            (c, builder) -> suggest(builder, innates(), t -> shortName(t.id()), CommandArgs::rated);
    static final SuggestionProvider<CommandSourceStack> TRAITS =
            (c, builder) -> suggest(builder, traits(), t -> shortName(t.id()), CommandArgs::rated);

    private CommandArgs() {}

    @FunctionalInterface
    interface PlayerCommand {
        int run(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException;
    }

    // ------------------------------------------------------------------ systems and players

    /** A branch of {@code /kegare} that takes {@link #PERMISSION}. */
    static LiteralArgumentBuilder<CommandSourceStack> system(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(PERMISSION));
    }

    /** {@code <player>} with more arguments after it. Read it with {@link #forEachPlayer}. */
    static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> player() {
        return Commands.argument(PLAYER, EntityArgument.players());
    }

    /** {@code <player>} as the last argument. */
    static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> player(PlayerCommand command) {
        return player().executes(c -> forEachPlayer(c, command));
    }

    /** {@code [player]}: the sender if it's left out. */
    static <T extends ArgumentBuilder<CommandSourceStack, T>> T senderOr(T builder, PlayerCommand command) {
        return builder.executes(c -> command.run(c.getSource(), c.getSource().getPlayerOrException()))
                .then(player(command));
    }

    /** Runs for everyone the argument selects, and adds up what each returned. */
    static int forEachPlayer(CommandContext<CommandSourceStack> c, PlayerCommand command) throws CommandSyntaxException {
        int total = 0;
        for (ServerPlayer player : players(c)) total += command.run(c.getSource(), player);
        return total;
    }

    private static Collection<ServerPlayer> players(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        try {
            return EntityArgument.getPlayers(c, PLAYER);
        } catch (CommandSyntaxException e) {
            if (e.getType() == EntityArgument.NO_PLAYERS_FOUND) throw NO_PLAYER.create(typed(c, PLAYER));
            throw e;
        }
    }

    private static String typed(CommandContext<CommandSourceStack> c, String argument) {
        for (ParsedCommandNode<CommandSourceStack> node : c.getNodes()) {
            if (node.getNode().getName().equals(argument)) return node.getRange().get(c.getInput());
        }
        return argument;
    }

    // ------------------------------------------------------------------ short names

    /** A name as the last argument. It takes the rest of the line, so a mistyped one gets our error and not a syntax one. */
    static RequiredArgumentBuilder<CommandSourceStack, String> name(String argument) {
        return Commands.argument(argument, StringArgumentType.greedyString());
    }

    static String typedName(CommandContext<CommandSourceStack> c, String argument) {
        return StringArgumentType.getString(c, argument).trim();
    }

    static String shortName(ResourceLocation id) {
        return id.getPath();
    }

    /** The entry with this short name. Should two mods use the same one, Kegare's wins. */
    private static <T> Optional<T> byShortName(Registry<T> registry, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return registry.keySet().stream()
                .filter(id -> id.getPath().equals(wanted))
                .sorted(Comparator.<ResourceLocation, Boolean>comparing(id -> !id.getNamespace().equals(Kegare.MODID)).thenComparing(ResourceLocation::toString))
                .findFirst()
                .map(registry::get);
    }

    /** Test techniques only exist here while the config lets them be rolled. */
    private static boolean available(InnateTechnique technique) {
        return !technique.isTestContent() || KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.get();
    }

    /** Lowest rarity first. */
    static List<InnateTechnique> innates() {
        return KegareRegistries.INNATE_TECHNIQUE.stream()
                .filter(CommandArgs::available)
                .sorted(Comparator.comparing(InnateTechnique::rarity).thenComparing(t -> shortName(t.id())))
                .toList();
    }

    static List<Trait> traits() {
        return KegareRegistries.TRAIT.stream().sorted(Comparator.comparing(t -> shortName(t.id()))).toList();
    }

    static InnateTechnique innate(CommandContext<CommandSourceStack> c, String argument) throws CommandSyntaxException {
        String typed = typedName(c, argument);
        return byShortName(KegareRegistries.INNATE_TECHNIQUE, typed).filter(CommandArgs::available).orElseThrow(() -> UNKNOWN_INNATE.create(typed));
    }

    static Trait trait(CommandContext<CommandSourceStack> c, String argument) throws CommandSyntaxException {
        String typed = typedName(c, argument);
        return byShortName(KegareRegistries.TRAIT, typed).orElseThrow(() -> UNKNOWN_TRAIT.create(typed));
    }

    /** Short names only, each with what it is on hover. The client only shows a tooltip the server sent, which this does. */
    static <T> CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Iterable<T> all, Function<T, String> name, Function<T, Component> hover) {
        String typed = builder.getRemainingLowerCase();
        for (T value : all) {
            String text = name.apply(value);
            if (SharedSuggestionProvider.matchesSubStr(typed, text)) builder.suggest(text, hover.apply(value));
        }
        return builder.buildFuture();
    }

    // ------------------------------------------------------------------ what it says

    /** Only ever to the sender: Grade, Assessment and traits are private, and the other operators don't need the noise. */
    static void say(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    static void say(CommandSourceStack source, String key, Object... args) {
        say(source, Component.translatable(key, args));
    }

    /** Tells the sender why nothing happened. */
    static int fail(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, args));
        return 0;
    }

    static Component rarityName(InnateTechniqueRarity rarity) {
        return Component.translatable(rarity.translationKey()).withColor(rarity.color());
    }

    /** "Rime (Rare)", in the rarity's color. */
    static Component rated(InnateTechnique technique) {
        return Component.translatable("commands.kegare.rated", technique.displayName(), rarityName(technique.rarity())).withColor(technique.rarity().color());
    }

    /** "Six Eyes (Unique)". Traits have no rarity, and being unique is what sets one apart. */
    static Component rated(Trait trait) {
        if (!trait.isUnique()) return trait.displayName();
        return Component.translatable("commands.kegare.rated", trait.displayName(), Component.translatable("commands.kegare.trait.kind.unique")).withColor(GuiDraw.GOLD & 0xFFFFFF);
    }

    /**
     * A tier's name, in the colors the Sorcerer screen and the reveal give it. GuiDraw's colors are compile-time constants,
     * so they're copied in here and a dedicated server never loads that client class.
     */
    static Component tierName(MaliceTier tier) {
        int color = switch (tier) {
            case VAST -> GuiDraw.GOLD;
            case ZERO -> GuiDraw.RED;
            default -> GuiDraw.MALICE;
        };
        return Component.translatable(tier.translationKey()).withColor(color & 0xFFFFFF);
    }

    /** A short name beside its display name, for the list commands. */
    static Component listEntry(Component name, String shortName) {
        return Component.translatable("commands.kegare.entry", name, Component.literal(shortName).withColor(GuiDraw.GRAY & 0xFFFFFF));
    }

    /** Malice is shown as whole numbers everywhere (floored, like the HUD); the stored value keeps its fraction. */
    static int whole(double malice) {
        return (int) Math.floor(malice);
    }
}
