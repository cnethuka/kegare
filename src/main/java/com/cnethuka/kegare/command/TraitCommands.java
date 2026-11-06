package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.fail;
import static com.cnethuka.kegare.command.CommandArgs.forEachPlayer;
import static com.cnethuka.kegare.command.CommandArgs.listEntry;
import static com.cnethuka.kegare.command.CommandArgs.name;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.rated;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.senderOr;
import static com.cnethuka.kegare.command.CommandArgs.shortName;
import static com.cnethuka.kegare.command.CommandArgs.system;
import static com.cnethuka.kegare.command.CommandArgs.trait;
import static com.cnethuka.kegare.command.CommandArgs.traits;

import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code /kegare trait list|get|give|remove|release|status}. */
final class TraitCommands {
    private TraitCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("trait")
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(senderOr(Commands.literal("get"), TraitCommands::get))
                .then(Commands.literal("give")
                        .then(player()
                                .then(name("trait").suggests(CommandArgs.TRAITS)
                                        .executes(c -> {
                                            Trait trait = trait(c, "trait");
                                            return forEachPlayer(c, (source, player) -> give(source, player, trait));
                                        }))))
                .then(Commands.literal("remove")
                        .then(player()
                                .then(name("trait").suggests(CommandArgs.TRAITS)
                                        .executes(c -> {
                                            Trait trait = trait(c, "trait");
                                            return forEachPlayer(c, (source, player) -> remove(source, player, trait));
                                        }))))
                // Frees a unique trait from its holder, online or offline. Inheritable traits then pass on.
                .then(Commands.literal("release")
                        .then(name("trait").suggests(CommandArgs.TRAITS)
                                .executes(c -> release(c.getSource(), trait(c, "trait")))))
                // Who holds a unique trait, or whether it is passing on.
                .then(Commands.literal("status")
                        .then(name("trait").suggests(CommandArgs.TRAITS)
                                .executes(c -> status(c.getSource(), trait(c, "trait"))))));
    }

    private static int list(CommandSourceStack source) {
        List<Trait> all = traits();
        say(source, "commands.kegare.trait.list");
        for (Trait trait : all) say(source, listEntry(rated(trait), shortName(trait.id())));
        return all.size();
    }

    private static int get(CommandSourceStack source, ServerPlayer player) {
        List<Component> names = new ArrayList<>();
        for (ResourceLocation id : TraitService.get(player).view()) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            names.add(trait != null ? rated(trait) : Component.literal(shortName(id)));
        }
        if (names.isEmpty()) say(source, "commands.kegare.trait.get.none", player.getDisplayName());
        else say(source, "commands.kegare.trait.get", player.getDisplayName(), ComponentUtils.formatList(names, Component.literal(", ")));
        return names.size();
    }

    private static int give(CommandSourceStack source, ServerPlayer player, Trait trait) {
        return switch (TraitService.add(player, trait, Trait.AddReason.ADMIN)) {
            case ADDED -> {
                say(source, "commands.kegare.trait.give", trait.displayName(), player.getDisplayName());
                yield 1;
            }
            // Not reachable for an admin, who overrides succession. Kept so a new result can't be missed.
            case PENDING_SUCCESSION -> fail(source, "commands.kegare.trait.status.waiting", trait.displayName());
            case ALREADY_HAS -> fail(source, "commands.kegare.trait.give.already", player.getDisplayName(), trait.displayName());
            case UNIQUE_TAKEN -> {
                Component holder = TraitService.bearer(source.getServer(), trait).map(u -> holderName(source, u)).orElse(Component.translatable("commands.kegare.trait.holder.unnamed"));
                yield fail(source, "commands.kegare.trait.give.unique_taken", trait.displayName(), holder);
            }
            case REQUIRES_MALICE -> fail(source, "commands.kegare.trait.give.requires_malice", trait.displayName(), player.getDisplayName());
        };
    }

    private static int remove(CommandSourceStack source, ServerPlayer player, Trait trait) {
        if (!TraitService.remove(player, trait, Trait.RemoveReason.ADMIN)) {
            return fail(source, "commands.kegare.trait.remove.not_present", player.getDisplayName(), trait.displayName());
        }
        say(source, "commands.kegare.trait.remove", trait.displayName(), player.getDisplayName());
        return 1;
    }

    private static int release(CommandSourceStack source, Trait trait) {
        if (!trait.isUnique()) return fail(source, "commands.kegare.trait.release.not_unique", trait.displayName());
        Optional<UUID> holder = TraitSuccession.release(source.getServer(), trait);
        if (holder.isEmpty()) return fail(source, "commands.kegare.trait.release.unclaimed", trait.displayName());
        Component name = holderName(source, holder.get());
        boolean online = source.getServer().getPlayerList().getPlayer(holder.get()) != null;
        say(source, online ? "commands.kegare.trait.release" : "commands.kegare.trait.release.offline", trait.displayName(), name);
        if (trait.isInheritable()) {
            say(source, "commands.kegare.trait.release.passing_on", trait.displayName(), CastManager.seconds(trait.successionDelayTicks()));
        }
        return 1;
    }

    /** A holder who's been gone long enough to leave the name cache leaves only a UUID, and that isn't for showing. */
    private static Component holderName(CommandSourceStack source, UUID holder) {
        String name = TraitService.nameOf(source.getServer(), holder);
        return name.equals(holder.toString()) ? Component.translatable("commands.kegare.trait.holder.unnamed") : Component.literal(name);
    }

    private static int status(CommandSourceStack source, Trait trait) {
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(source.getServer());
        Optional<UUID> holder = saved.bearer(trait.id());
        Optional<UniqueTraitSavedData.Pending> pending = saved.pending(trait.id());
        if (holder.isPresent()) {
            say(source, "commands.kegare.trait.status.held", trait.displayName(), holderName(source, holder.get()));
        } else if (pending.isPresent()) {
            long remaining = Math.max(0, pending.get().dueTick() - source.getServer().overworld().getGameTime());
            if (remaining > 0) say(source, "commands.kegare.trait.status.passing", trait.displayName(), CastManager.seconds((int) remaining));
            else say(source, "commands.kegare.trait.status.waiting", trait.displayName());
        } else {
            say(source, "commands.kegare.trait.status.unclaimed", trait.displayName());
        }
        return 1;
    }
}
