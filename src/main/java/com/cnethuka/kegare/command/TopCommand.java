package com.cnethuka.kegare.command;

import com.cnethuka.kegare.database.KegareDatabase;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** {@code /kegare top <stat>}: anyone. Public stats only (DESIGN §3.33): nothing private can be asked for, since these are all it knows. */
final class TopCommand {
    private TopCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("top").then(Commands.argument("stat", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(KegareDatabase.PublicStat.names(), builder))
                .executes(context -> top(context.getSource(), StringArgumentType.getString(context, "stat")))));
    }

    private static int top(CommandSourceStack source, String name) {
        KegareDatabase.PublicStat stat = KegareDatabase.PublicStat.byName(name);
        if (stat == null) return CommandArgs.fail(source, "commands.kegare.top.unknown", name, String.join(", ", KegareDatabase.PublicStat.names()));
        KegareDatabase database = KegareDatabase.current();
        if (database == null) return CommandArgs.fail(source, "commands.kegare.top.off");
        var server = source.getServer();
        database.top(stat).whenComplete((rows, failure) -> server.execute(() -> show(source, stat, rows, failure)));
        return 1;
    }

    private static void show(CommandSourceStack source, KegareDatabase.PublicStat stat, List<KegareDatabase.TopRow> rows, Throwable failure) {
        if (failure != null || rows == null) {
            CommandArgs.fail(source, "commands.kegare.top.unreachable");
            return;
        }
        CommandArgs.say(source, Component.translatable("commands.kegare.top.header", Component.translatable(stat.langKey())).withStyle(ChatFormatting.GOLD));
        if (rows.isEmpty()) CommandArgs.say(source, Component.translatable("commands.kegare.top.empty").withStyle(ChatFormatting.GRAY));
        int rank = 1;
        for (KegareDatabase.TopRow row : rows) CommandArgs.say(source, "commands.kegare.top.row", rank++, row.name(), Math.round(row.value()));
    }
}
