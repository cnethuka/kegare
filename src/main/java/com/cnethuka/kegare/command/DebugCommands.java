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
import java.util.List;
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
    private static final Dynamic2CommandExceptionType IMPACT_UNKNOWN = new Dynamic2CommandExceptionType(
            (typed, choices) -> Component.translatable("commands.kegare.debug.impact.unknown", String.valueOf(typed), String.valueOf(choices)));
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
                // The Impact System (DESIGN §3.23): play a tier on someone's own screen, to tune how it feels without a fight.
                .then(Commands.literal("impact")
                        .then(Commands.literal("log").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            boolean on = BoolArgumentType.getBool(c, "on");
                            com.cnethuka.kegare.impact.Impacts.setLog(on);
                            say(c.getSource(), on ? "commands.kegare.debug.impact.log.on" : "commands.kegare.debug.impact.log.off");
                            return 1;
                        })))
                        .then(impactTiers()))
                // The screen shader hook: a whole-screen effect for a set time (grayscale, for Standstill to come).
                .then(Commands.literal("screen")
                        .then(player()
                                .then(Commands.argument("effect", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
                                                com.cnethuka.kegare.impact.ScreenEffects.ALL, b))
                                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 600))
                                                .executes(c -> {
                                                    var effect = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "effect");
                                                    double seconds = DoubleArgumentType.getDouble(c, "seconds");
                                                    return forEachPlayer(c, (source, player) -> screen(source, player, effect, seconds));
                                                })))))
                // Battle damage's pending rebuilds (RESTORING mode): start them all now instead of after the fight's delay.
                .then(Commands.literal("battle").then(Commands.literal("restore").executes(c -> battleRestore(c.getSource()))))
                // Reverse Malice Technique (DESIGN §3.17): injury fatigue, knowing how to heal others, and a forced moment.
                .then(Commands.literal("rmt")
                        .then(Commands.literal("fatigue").then(player()
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 1.0)).executes(c -> {
                                    double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "amount");
                                    return forEachPlayer(c, (source, player) -> rmtFatigue(source, player, amount));
                                }))))
                        .then(Commands.literal("others")
                                .then(Commands.literal("unlock").then(player((source, player) -> rmtOthers(source, player, true))))
                                .then(Commands.literal("lock").then(player((source, player) -> rmtOthers(source, player, false)))))
                        .then(Commands.literal("moment").then(player()
                                .then(Commands.argument("target", net.minecraft.commands.arguments.EntityArgument.player()).executes(c -> {
                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "target");
                                    return forEachPlayer(c, (source, player) -> rmtMoment(source, player, target));
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

    private static final List<String> ROLES = List.of("attacker", "victim", "witness"), KINDS = List.of("melee", "technique");

    private static int rmtFatigue(CommandSourceStack source, ServerPlayer player, double amount) {
        com.cnethuka.kegare.rmt.RmtFatigue.set(player, amount);
        say(source, "commands.kegare.debug.rmt.fatigue", player.getDisplayName(), Math.round(com.cnethuka.kegare.rmt.RmtFatigue.of(player) * 100) + "%",
                String.format(java.util.Locale.ROOT, "%.2f", com.cnethuka.kegare.rmt.RmtOutput.of(player)));
        return 1;
    }

    private static int rmtOthers(CommandSourceStack source, ServerPlayer player, boolean unlock) {
        net.minecraft.resources.ResourceLocation id = com.cnethuka.kegare.rmt.RmtIds.ON_ANOTHER_UNLOCK;
        boolean changed = unlock ? com.cnethuka.kegare.progression.Unlocks.grant(player, id) : com.cnethuka.kegare.progression.Unlocks.revoke(player, id);
        if (!changed) return fail(source, unlock ? "commands.kegare.debug.rmt.others.already" : "commands.kegare.debug.rmt.others.not_known", player.getDisplayName());
        say(source, unlock ? "commands.kegare.debug.rmt.others.unlocked" : "commands.kegare.debug.rmt.others.locked", player.getDisplayName());
        return 1;
    }

    private static int rmtMoment(CommandSourceStack source, ServerPlayer player, ServerPlayer target) {
        if (player == target) return fail(source, "commands.kegare.debug.rmt.moment.self", player.getDisplayName());
        com.cnethuka.kegare.rmt.RmtOnAnother.startMoment(player, target);
        String seconds = com.cnethuka.kegare.config.ConfigText.number(com.cnethuka.kegare.rmt.RmtConfig.MOMENT_SECONDS);
        boolean canLearn = com.cnethuka.kegare.progression.Unlocks.eligible(player, com.cnethuka.kegare.rmt.RmtIds.ON_ANOTHER_UNLOCK);
        say(source, canLearn ? "commands.kegare.debug.rmt.moment" : "commands.kegare.debug.rmt.moment.not_eligible",
                target.getDisplayName(), player.getDisplayName(), seconds);
        return 1;
    }

    /** {@code impact <player> <tier> <role> <kind>}: a tier as that player sees it in that role, a plain hit's white or a technique's color. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> impactTiers() {
        List<String> tiers = java.util.Arrays.stream(com.cnethuka.kegare.impact.ImpactTier.values()).map(com.cnethuka.kegare.impact.ImpactTier::serializedName).toList();
        return player().then(Commands.argument("tier", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(tiers, b))
                .then(Commands.argument("role", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ROLES, b))
                        .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(KINDS, b))
                                .executes(c -> {
                                    String tier = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "tier");
                                    String role = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "role");
                                    String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind");
                                    var found = java.util.Arrays.stream(com.cnethuka.kegare.impact.ImpactTier.values())
                                            .filter(t -> t.serializedName().equals(tier)).findFirst();
                                    if (found.isEmpty()) throw IMPACT_UNKNOWN.create(tier, String.join(", ", tiers));
                                    if (!ROLES.contains(role)) throw IMPACT_UNKNOWN.create(role, String.join(", ", ROLES));
                                    if (!KINDS.contains(kind)) throw IMPACT_UNKNOWN.create(kind, String.join(", ", KINDS));
                                    return forEachPlayer(c, (source, p) -> impact(source, p, found.get(), role, kind.equals("technique")));
                                }))));
    }

    /** A hit of this tier as only this player sees it: nothing lands, nobody else sees a thing. */
    private static int impact(CommandSourceStack source, ServerPlayer player, com.cnethuka.kegare.impact.ImpactTier tier, String role, boolean technique) {
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 contact, direction = look;
        int attacker = -1, victim = -1;
        switch (role) {
            case "attacker" -> {
                contact = eye.add(look.scale(2.0));
                attacker = player.getId();
            }
            case "victim" -> {
                contact = eye.add(0, -0.45, 0).add(look.scale(0.3));
                direction = look.scale(-1);
                victim = player.getId();
            }
            default -> contact = eye.add(look.scale(4.0));
        }
        com.cnethuka.kegare.melee.MeleeTell.Look own = com.cnethuka.kegare.melee.MeleeTell.of(player);
        int color = technique && own.kind() == com.cnethuka.kegare.melee.MeleeTell.Kind.GLOW ? own.color() & 0xFFFFFF : 0;
        int parts = com.cnethuka.kegare.impact.ImpactPayload.FLASH | com.cnethuka.kegare.impact.ImpactPayload.HIT_STOP
                | com.cnethuka.kegare.impact.ImpactPayload.SHAKE
                | (tier.ordinal() >= com.cnethuka.kegare.impact.ImpactTier.IMPACT_FRAME.ordinal() ? com.cnethuka.kegare.impact.ImpactPayload.FRAME : 0);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.cnethuka.kegare.impact.ImpactPayload(tier,
                technique ? com.cnethuka.kegare.impact.ImpactKind.TECHNIQUE : com.cnethuka.kegare.impact.ImpactKind.MELEE, parts, contact, direction,
                color, attacker, victim, 0f));
        say(source, "commands.kegare.debug.impact", tier.serializedName(), player.getDisplayName(), role);
        return 1;
    }

    private static int battleRestore(CommandSourceStack source) {
        int blocks = 0;
        for (net.minecraft.server.level.ServerLevel level : source.getServer().getAllLevels()) {
            var scars = com.cnethuka.kegare.battledamage.BattleScars.get(level);
            blocks += scars.pending();
            scars.restoreNow(level);
        }
        say(source, "commands.kegare.debug.battle.restore", Integer.toString(blocks));
        return blocks;
    }

    private static int screen(CommandSourceStack source, ServerPlayer player, net.minecraft.resources.ResourceLocation effect, double seconds) {
        int ticks = (int) Math.round(seconds * 20);
        com.cnethuka.kegare.impact.ScreenEffects.play(player, effect, ticks);
        if (ticks <= 0) say(source, "commands.kegare.debug.screen.stopped", effect.toString(), player.getDisplayName());
        else say(source, "commands.kegare.debug.screen", effect.toString(), player.getDisplayName(), String.format(Locale.ROOT, "%.1f", seconds));
        return 1;
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
