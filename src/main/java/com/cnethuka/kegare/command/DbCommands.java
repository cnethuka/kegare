package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.fail;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.system;

import com.cnethuka.kegare.database.KegareDatabase;
import com.cnethuka.kegare.database.WorldMigration;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /kegare db}: ops, and the console (DESIGN §3.33). Says how the database is doing, and moves a server on and off it. Never shows the password or the url. */
final class DbCommands {
    private DbCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("db")
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("migrate").executes(c -> migrate(c.getSource(), false))
                        .then(Commands.literal("dry").executes(c -> migrate(c.getSource(), true))))
                .then(Commands.literal("export").executes(c -> export(c.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        KegareDatabase database = KegareDatabase.current();
        if (database == null) {
            say(source, "commands.kegare.db.status.off");
            return 1;
        }
        KegareDatabase.Status status = database.status();
        say(source, "commands.kegare.db.status.on", status.serverId());
        if (status.connected()) say(source, "commands.kegare.db.status.connected", status.schemaVersion());
        else say(source, "commands.kegare.db.status.disconnected");
        say(source, "commands.kegare.db.status.queue", status.queue(), status.statsWaiting());
        if (status.connected()) say(source, "commands.kegare.db.status.leases", status.leasesHere(), status.leasesEverywhere());
        else say(source, "commands.kegare.db.status.leases_here", status.leasesHere());
        return 1;
    }

    /**
     * The result of a job still running when the command returned. A player sees it, and it always goes to the server log: that
     * is the console's output, and all RCON gets, since RCON only hears what a command says before it returns.
     */
    private static void report(CommandSourceStack source, String key, Object... args) {
        Component message = Component.translatable(key, args);
        if (source.isPlayer()) say(source, message);
        source.getServer().sendSystemMessage(message);
    }

    private static int migrate(CommandSourceStack source, boolean dry) {
        KegareDatabase database = KegareDatabase.current();
        if (database == null) return fail(source, "commands.kegare.db.off");
        var server = source.getServer();
        say(source, dry ? "commands.kegare.db.migrate.started_dry" : "commands.kegare.db.migrate.started");
        WorldMigration.migrate(database, WorldMigration.Target.of(server), dry).whenComplete((result, failure) -> server.execute(() -> {
            if (failure != null) {
                report(source, "commands.kegare.db.unreachable");
                return;
            }
            report(source, dry ? "commands.kegare.db.migrate.players_dry" : "commands.kegare.db.migrate.players", result.files(), result.imported(), result.rows(), result.alreadyThere(), result.noData());
            report(source, dry ? "commands.kegare.db.migrate.traits_dry" : "commands.kegare.db.migrate.traits", result.bearers(), result.pending(), result.revoked(), result.seen());
            if (result.conflicts() > 0) report(source, "commands.kegare.db.migrate.conflicts", result.conflicts());
            if (!result.failures().isEmpty()) report(source, "commands.kegare.db.migrate.failed", result.failures().size(), String.join(", ", result.failures()));
        }));
        return 1;
    }

    private static int export(CommandSourceStack source) {
        KegareDatabase database = KegareDatabase.current();
        if (database == null) return fail(source, "commands.kegare.db.off");
        var server = source.getServer();
        say(source, "commands.kegare.db.export.started");
        WorldMigration.export(database, WorldMigration.Target.of(server)).whenComplete((result, failure) -> server.execute(() -> {
            if (failure != null) {
                report(source, "commands.kegare.db.unreachable");
                return;
            }
            report(source, "commands.kegare.db.export.players", result.players(), result.written(), result.created(), result.online());
            report(source, "commands.kegare.db.export.traits", result.bearers(), result.pending(), result.revoked());
            if (!result.failures().isEmpty()) report(source, "commands.kegare.db.export.failed", result.failures().size(), String.join(", ", result.failures()));
            report(source, "commands.kegare.db.export.next");
        }));
        return 1;
    }
}
