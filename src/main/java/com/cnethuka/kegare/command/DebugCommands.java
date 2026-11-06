package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.fail;
import static com.cnethuka.kegare.command.CommandArgs.forEachPlayer;
import static com.cnethuka.kegare.command.CommandArgs.innate;
import static com.cnethuka.kegare.command.CommandArgs.name;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.senderOr;
import static com.cnethuka.kegare.command.CommandArgs.shortName;
import static com.cnethuka.kegare.command.CommandArgs.suggest;
import static com.cnethuka.kegare.command.CommandArgs.system;
import static com.cnethuka.kegare.command.CommandArgs.typedName;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsDebug;
import com.cnethuka.kegare.innate_technique.swap.SwapDebug;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.rmt.GreenOrbService;
import com.cnethuka.kegare.rmt.RmtIds;
import com.cnethuka.kegare.rmt.RmtState;
import com.cnethuka.kegare.roll.RollService;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /kegare debug ...}: what exists only for testing. {@code debug fx} is here too, in the dev client's own command
 * tree ({@code FxDevCommand}).
 */
final class DebugCommands {
    private static final Dynamic2CommandExceptionType UNKNOWN_SHARED = new Dynamic2CommandExceptionType(
            (typed, abilities) -> Component.translatable("commands.kegare.debug.shared.unknown", String.valueOf(typed), String.valueOf(abilities)));

    private DebugCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("debug")
                .then(Commands.literal("stun")
                        .then(player()
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 3600))
                                        .executes(c -> {
                                            double seconds = DoubleArgumentType.getDouble(c, "seconds");
                                            return forEachPlayer(c, (source, player) -> stun(source, player, seconds));
                                        }))))
                .then(Commands.literal("pity")
                        .then(player()
                                .then(Commands.argument("rolls", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> {
                                            int rolls = IntegerArgumentType.getInteger(c, "rolls");
                                            return forEachPlayer(c, (source, player) -> pity(source, player, rolls));
                                        }))))
                // Forces the result, for testing reveals. It still goes through the roll pipeline: pity, advancements, reveal.
                .then(Commands.literal("reroll")
                        .then(player()
                                .then(name("innate").suggests(CommandArgs.INNATES)
                                        .executes(c -> {
                                            InnateTechnique forced = innate(c, "innate");
                                            return forEachPlayer(c, (source, player) -> InnateCommands.reroll(source, player, forced));
                                        }))))
                .then(Commands.literal("shared")
                        .then(sharedAction("unlock", true))
                        .then(sharedAction("lock", false)))
                // The green orb, which finds Reverse Malice Technique: force one now, or see the roll cooldown.
                .then(Commands.literal("orb")
                        .then(senderOr(Commands.literal("spawn"), DebugCommands::orbSpawn))
                        .then(senderOr(Commands.literal("status"), DebugCommands::orbStatus)))
                // Stand-ins for a future immunity: these cancel SwapTargetEvent and StringsTargetEvent for this player.
                .then(Commands.literal("swap")
                        .then(Commands.literal("unswappable")
                                .then(player()
                                        .then(Commands.argument("on", BoolArgumentType.bool())
                                                .executes(c -> {
                                                    boolean on = BoolArgumentType.getBool(c, "on");
                                                    return forEachPlayer(c, (source, player) -> unswappable(source, player, on));
                                                })))))
                .then(Commands.literal("strings")
                        .then(Commands.literal("unmovable")
                                .then(player()
                                        .then(Commands.argument("on", BoolArgumentType.bool())
                                                .executes(c -> {
                                                    boolean on = BoolArgumentType.getBool(c, "on");
                                                    return forEachPlayer(c, (source, player) -> unmovable(source, player, on));
                                                }))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sharedAction(String action, boolean unlock) {
        return Commands.literal(action)
                .then(player()
                        .then(name("ability")
                                .suggests((c, b) -> suggest(b, SharedAbilities.all(), a -> shortName(a.id()), Ability::displayName))
                                .executes(c -> {
                                    Ability ability = sharedAbility(c);
                                    return forEachPlayer(c, (source, player) -> shared(source, player, ability, unlock));
                                })));
    }

    private static Ability sharedAbility(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String typed = typedName(c, "ability");
        String wanted = typed.toLowerCase(Locale.ROOT);
        return SharedAbilities.all().stream().filter(a -> shortName(a.id()).equals(wanted)).findFirst()
                .orElseThrow(() -> UNKNOWN_SHARED.create(typed, SharedAbilities.all().stream().map(a -> shortName(a.id())).collect(Collectors.joining(", "))));
    }

    private static int stun(CommandSourceStack source, ServerPlayer player, double seconds) {
        int ticks = (int) Math.round(seconds * 20);
        if (ticks <= 0) {
            CombatHelper.clearStun(player);
            say(source, "commands.kegare.debug.stun.cleared", player.getDisplayName());
        } else {
            CombatHelper.stun(player, ticks);
            say(source, "commands.kegare.debug.stun", player.getDisplayName(), String.format(Locale.ROOT, "%.1f", seconds));
        }
        return 1;
    }

    private static int pity(CommandSourceStack source, ServerPlayer player, int rolls) {
        RollService.setPity(player, rolls);
        say(source, "commands.kegare.debug.pity", player.getDisplayName(), rolls);
        return 1;
    }

    private static int shared(CommandSourceStack source, ServerPlayer player, Ability ability, boolean unlock) {
        boolean changed = unlock ? SharedAbilityService.unlock(player, ability) : SharedAbilityService.lock(player, ability);
        if (!changed) {
            if (!unlock) return fail(source, "commands.kegare.debug.shared.not_unlocked", player.getDisplayName(), ability.displayName());
            return fail(source, MaliceService.isSevered(player) ? "commands.kegare.debug.shared.severed" : "commands.kegare.debug.shared.already", player.getDisplayName(), ability.displayName());
        }
        say(source, unlock ? "commands.kegare.debug.shared.unlocked" : "commands.kegare.debug.shared.locked", ability.displayName(), player.getDisplayName());
        return 1;
    }

    private static int orbSpawn(CommandSourceStack source, ServerPlayer player) {
        return switch (GreenOrbService.force(player)) {
            case SPAWNED -> {
                say(source, "commands.kegare.debug.orb.spawned", player.getDisplayName());
                yield 1;
            }
            case ALREADY_UNLOCKED -> fail(source, "commands.kegare.debug.orb.already_unlocked", player.getDisplayName());
            case SEVERED -> fail(source, "commands.kegare.debug.orb.severed", player.getDisplayName());
            case NOT_BORN -> fail(source, "commands.kegare.debug.orb.not_born", player.getDisplayName());
        };
    }

    private static int orbStatus(CommandSourceStack source, ServerPlayer player) {
        Component unlocked = Component.translatable(SharedAbilityService.isUnlocked(player, RmtIds.ABILITY)
                ? "commands.kegare.debug.orb.status.unlocked" : "commands.kegare.debug.orb.status.locked");
        RmtState orb = GreenOrbService.state(player);
        Component waiting = orb.hasOrb()
                ? Component.translatable("commands.kegare.debug.orb.status.following", orb.followTicks(), orb.followTotal(),
                        coordinate(orb.orb().x), coordinate(orb.orb().y), coordinate(orb.orb().z))
                : Component.translatable("commands.kegare.debug.orb.status.no");
        say(source, "commands.kegare.debug.orb.status", player.getDisplayName(),
                CastManager.seconds(GreenOrbService.rollCooldownRemaining(player)), waiting, unlocked,
                Component.translatable(GreenOrbService.eligible(player) ? "commands.kegare.debug.orb.status.yes" : "commands.kegare.debug.orb.status.no"));
        return 1;
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int unswappable(CommandSourceStack source, ServerPlayer player, boolean on) {
        SwapDebug.setUnswappable(player.getUUID(), on);
        say(source, on ? "commands.kegare.debug.swap.on" : "commands.kegare.debug.swap.off", player.getDisplayName());
        return 1;
    }

    private static int unmovable(CommandSourceStack source, ServerPlayer player, boolean on) {
        StringsDebug.setUnmovable(player.getUUID(), on);
        say(source, on ? "commands.kegare.debug.strings.on" : "commands.kegare.debug.strings.off", player.getDisplayName());
        return 1;
    }
}
