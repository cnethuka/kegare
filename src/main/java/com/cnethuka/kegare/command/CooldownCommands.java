package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.forEachPlayer;
import static com.cnethuka.kegare.command.CommandArgs.name;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.shortName;
import static com.cnethuka.kegare.command.CommandArgs.suggest;
import static com.cnethuka.kegare.command.CommandArgs.system;
import static com.cnethuka.kegare.command.CommandArgs.typedName;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.Burnout;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.melee.MeleeLayer;
import com.cnethuka.kegare.network.PlayerSync;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.severed.Severed;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /kegare cooldown reset [player] [ability]} (DESIGN §3.8): every cooldown at once (the innate technique's abilities, specials
 * and ultimate, shared abilities like RMT, Dash and Closing Line) and Burnout, or only the one named. Told to the client at once, so
 * the panel and the HUD's cues show it ready.
 */
final class CooldownCommands {
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(typed -> Component.translatable("commands.kegare.cooldown.unknown", String.valueOf(typed)));

    /** Something with a cooldown, by the short name the command takes. */
    private record Target(String name, Component display, Consumer<ServerPlayer> clear) {}

    private CooldownCommands() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(system("cooldown").then(Commands.literal("reset")
                .executes(c -> resetAll(c.getSource(), c.getSource().getPlayerOrException()))
                .then(player().executes(c -> forEachPlayer(c, CooldownCommands::resetAll))
                        .then(name("ability")
                                .suggests((c, b) -> suggest(b, targets().values(), Target::name, Target::display))
                                .executes(c -> {
                                    Target target = target(c);
                                    return forEachPlayer(c, (source, player) -> resetOne(source, player, target));
                                })))));
    }

    /** Every ability of every technique (specials and ultimates too) and shared ability, then Dash and Closing Line. */
    private static Map<String, Target> targets() {
        Map<String, Target> out = new LinkedHashMap<>();
        for (InnateTechnique technique : KegareRegistries.INNATE_TECHNIQUE) {
            for (Ability a : technique.everyAbility()) out.putIfAbsent(shortName(a.id()), ability(a));
            technique.ultimate().ifPresent(a -> out.putIfAbsent(shortName(a.id()), ability(a)));
        }
        for (Ability a : SharedAbilities.all()) out.putIfAbsent(shortName(a.id()), ability(a));
        out.put("dash", new Target("dash", Component.translatable("hud.kegare.melee.dash"), MeleeLayer::clearDashCooldown));
        out.put("closing_line", new Target("closing_line", Component.translatable("hud.kegare.severed.closing_line"), Severed::clearClosingLineCooldown));
        return out;
    }

    /** A special shares its base ability's cooldown, so naming either clears the one they share. */
    private static Target ability(Ability a) {
        return new Target(shortName(a.id()), a.displayName(), player -> InnateTechniqueService.clearCooldown(player, a.cooldownKey()));
    }

    private static Target target(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String typed = typedName(c, "ability");
        Target target = targets().get(typed.toLowerCase(Locale.ROOT));
        if (target == null) throw UNKNOWN.create(typed);
        return target;
    }

    private static int resetAll(CommandSourceStack source, ServerPlayer player) {
        InnateTechniqueService.clearCooldowns(player); // abilities, specials, the ultimate and shared abilities share one store
        Burnout.clear(player);
        MeleeLayer.clearDashCooldown(player);
        Severed.clearClosingLineCooldown(player);
        PlayerSync.syncNow(player);
        say(source, "commands.kegare.cooldown.reset", player.getDisplayName());
        return 1;
    }

    private static int resetOne(CommandSourceStack source, ServerPlayer player, Target target) {
        target.clear().accept(player);
        PlayerSync.syncNow(player);
        say(source, "commands.kegare.cooldown.reset_one", target.display(), player.getDisplayName());
        return 1;
    }
}
