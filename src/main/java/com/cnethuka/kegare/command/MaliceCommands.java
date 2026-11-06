package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.forEachPlayer;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.senderOr;
import static com.cnethuka.kegare.command.CommandArgs.system;
import static com.cnethuka.kegare.command.CommandArgs.tierName;
import static com.cnethuka.kegare.command.CommandArgs.whole;

import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** {@code /kegare malice get|set|fill|capacity}. */
final class MaliceCommands {
    private MaliceCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("malice")
                .then(senderOr(Commands.literal("get"), MaliceCommands::get))
                .then(Commands.literal("set")
                        .then(player()
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> {
                                            double amount = DoubleArgumentType.getDouble(c, "amount");
                                            return forEachPlayer(c, (source, player) -> set(source, player, amount));
                                        }))))
                .then(senderOr(Commands.literal("fill"), MaliceCommands::fill))
                .then(Commands.literal("capacity")
                        .then(player()
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> {
                                            int amount = IntegerArgumentType.getInteger(c, "amount");
                                            return forEachPlayer(c, (source, player) -> capacity(source, player, amount));
                                        })))));
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        MaliceData malice = MaliceService.get(player);
        if (malice.isSevered()) say(source, "commands.kegare.malice.get.severed", player.getDisplayName());
        else say(source, "commands.kegare.malice.get", player.getDisplayName(), whole(malice.current()), malice.capacity(), tierName(malice.tier()));
        return whole(malice.current());
    }

    private static int set(CommandSourceStack source, ServerPlayer player, double amount) {
        MaliceService.setCurrent(player, amount);
        MaliceData malice = MaliceService.get(player);
        say(source, "commands.kegare.malice.set", player.getDisplayName(), whole(malice.current()), malice.capacity());
        return 1;
    }

    private static int fill(CommandSourceStack source, ServerPlayer player) {
        MaliceService.refill(player);
        MaliceData malice = MaliceService.get(player);
        say(source, "commands.kegare.malice.fill", player.getDisplayName(), whole(malice.current()), malice.capacity());
        return 1;
    }

    private static int capacity(CommandSourceStack source, ServerPlayer player, int capacity) {
        MaliceService.adminSetCapacity(player, capacity);
        MaliceData malice = MaliceService.get(player);
        if (malice.isSevered()) InnateTechniqueService.setInnateTechnique(player, null); // Severed have no innate technique
        say(source, "commands.kegare.malice.capacity", player.getDisplayName(), malice.capacity(), tierName(malice.tier()));
        if (!malice.isSevered() && InnateTechniqueService.current(player).isEmpty()) say(source, "commands.kegare.malice.capacity.hint");
        return 1;
    }
}
