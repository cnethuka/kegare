package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.fail;
import static com.cnethuka.kegare.command.CommandArgs.forEachPlayer;
import static com.cnethuka.kegare.command.CommandArgs.innate;
import static com.cnethuka.kegare.command.CommandArgs.innates;
import static com.cnethuka.kegare.command.CommandArgs.listEntry;
import static com.cnethuka.kegare.command.CommandArgs.name;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.rated;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.senderOr;
import static com.cnethuka.kegare.command.CommandArgs.shortName;
import static com.cnethuka.kegare.command.CommandArgs.system;

import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /kegare innate list|get|set|reroll}. */
final class InnateCommands {
    private InnateCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("innate")
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(senderOr(Commands.literal("get"), InnateCommands::get))
                .then(Commands.literal("set")
                        .then(player()
                                .then(name("innate").suggests(CommandArgs.INNATES)
                                        .executes(c -> {
                                            InnateTechnique technique = innate(c, "innate");
                                            return forEachPlayer(c, (source, player) -> set(source, player, technique));
                                        }))))
                .then(senderOr(Commands.literal("reroll"), (source, player) -> reroll(source, player, null))));
    }

    private static int list(CommandSourceStack source) {
        List<InnateTechnique> all = innates();
        say(source, "commands.kegare.innate.list");
        for (InnateTechnique technique : all) say(source, listEntry(rated(technique), shortName(technique.id())));
        return all.size();
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        Optional<InnateTechnique> technique = InnateTechniqueService.current(player);
        if (technique.isPresent()) say(source, "commands.kegare.innate.get", player.getDisplayName(), rated(technique.get()));
        else say(source, "commands.kegare.innate.get.none", player.getDisplayName());
        return technique.isPresent() ? 1 : 0;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, InnateTechnique technique) {
        if (MaliceService.isSevered(player)) return fail(source, "commands.kegare.severed", player.getDisplayName());
        InnateTechniqueService.setInnateTechnique(player, technique);
        say(source, "commands.kegare.innate.set", player.getDisplayName(), rated(technique));
        return 1;
    }

    /** Through the roll pipeline: pity, advancements, the reveal. A forced result is {@code /kegare debug reroll}. */
    static int reroll(CommandSourceStack source, ServerPlayer player, @Nullable InnateTechnique forced) {
        RollService.RerollResult result = RollService.reroll(player, RollReason.ADMIN, forced);
        if (result == RollService.RerollResult.SEVERED) return fail(source, "commands.kegare.severed", player.getDisplayName());
        if (result == RollService.RerollResult.NO_CANDIDATES) return fail(source, "message.kegare.reroll.no_candidates");
        Component name = InnateTechniqueService.current(player).map(CommandArgs::rated).orElse(Component.translatable("commands.kegare.info.none"));
        say(source, "commands.kegare.innate.reroll", player.getDisplayName(), name, RollService.get(player).rollsSinceLegendary());
        return 1;
    }
}
