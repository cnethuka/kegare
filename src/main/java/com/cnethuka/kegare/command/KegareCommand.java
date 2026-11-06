package com.cnethuka.kegare.command;

import static com.cnethuka.kegare.command.CommandArgs.name;
import static com.cnethuka.kegare.command.CommandArgs.player;
import static com.cnethuka.kegare.command.CommandArgs.rarityName;
import static com.cnethuka.kegare.command.CommandArgs.say;
import static com.cnethuka.kegare.command.CommandArgs.senderOr;
import static com.cnethuka.kegare.command.CommandArgs.shortName;
import static com.cnethuka.kegare.command.CommandArgs.system;
import static com.cnethuka.kegare.command.CommandArgs.tierName;
import static com.cnethuka.kegare.command.CommandArgs.whole;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /kegare <system> <action> [player] [value]}. Everything takes permission level 2 but {@code help}: each system asks
 * for it itself, because the root has to stay open for that one command. All of this runs on the server. See DESIGN §3.8.
 */
@EventBusSubscriber(modid = Kegare.MODID)
public final class KegareCommand {
    private KegareCommand() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("kegare");
        HelpCommand.register(root);
        root.then(senderOr(system("info"), KegareCommand::info));
        InnateCommands.register(root);
        TraitCommands.register(root);
        MaliceCommands.register(root);
        root.then(system("rebirth")
                .then(player((source, player) -> rebirth(source, player, OptionalInt.empty()))
                        // Forces the Malice capacity of the new birth (0 = Severed).
                        .then(Commands.argument("capacity", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(c -> {
                                    OptionalInt capacity = OptionalInt.of(IntegerArgumentType.getInteger(c, "capacity"));
                                    return CommandArgs.forEachPlayer(c, (source, player) -> rebirth(source, player, capacity));
                                }))));
        // Back to the innate technique's default layout.
        root.then(system("loadout")
                .then(senderOr(Commands.literal("reset"), KegareCommand::loadoutReset)));
        ProgressionCommands.register(root);
        DebugCommands.register(root);
        dispatcher.register(root);
    }

    private static int info(CommandSourceStack source, ServerPlayer player) {
        MaliceData malice = MaliceService.get(player);
        InnateTechniqueData innateTechniqueData = InnateTechniqueService.get(player);
        RollData roll = RollService.get(player);
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("commands.kegare.info.header", player.getDisplayName()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("commands.kegare.info.malice", whole(malice.current()), malice.capacity(), tierName(malice.tier())));
        if (malice.isSevered()) lines.add(Component.translatable("commands.kegare.info.severed").withStyle(ChatFormatting.RED));

        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (innateTechnique.isPresent()) {
            InnateTechnique t = innateTechnique.get();
            Ability selected = LoadoutService.abilityAt(player, innateTechniqueData.selectedSlot());
            lines.add(Component.translatable("commands.kegare.info.innate_technique", t.displayName(),
                    rarityName(t.rarity()), innateTechniqueData.selectedSlot() + 1, LoadoutService.size(player),
                    selected == null ? Component.translatable("commands.kegare.info.none") : selected.displayName()));
            List<Component> slots = new ArrayList<>();
            int size = LoadoutService.size(player);
            int last = -1; // trailing empty slots are summed up, not listed
            for (int i = 0; i < size; i++) if (LoadoutService.abilityAt(player, i) != null) last = i;
            for (int i = 0; i <= last; i++) {
                Ability a = LoadoutService.abilityAt(player, i);
                slots.add(Component.translatable("commands.kegare.info.loadout_entry", i + 1,
                        a == null ? Component.translatable("commands.kegare.info.none") : a.displayName()));
            }
            if (last < size - 1) {
                slots.add(Component.translatable("commands.kegare.info.loadout_empty_rest", last + 2, size));
            }
            lines.add(Component.translatable("commands.kegare.info.loadout", join(slots)));
        } else {
            lines.add(Component.translatable("commands.kegare.info.no_innate_technique"));
        }

        List<Component> shared = new ArrayList<>();
        for (ResourceLocation id : SharedAbilityService.get(player).unlocked()) SharedAbilities.get(id).ifPresent(a -> shared.add(a.displayName()));
        lines.add(Component.translatable("commands.kegare.info.shared", join(shared)));

        List<Component> cooldowns = new ArrayList<>();
        innateTechnique.ifPresent(t -> innateTechniqueData.cooldowns().keySet().forEach(id -> AbilityLookup.find(t, id).ifPresent(a -> {
            int remaining = InnateTechniqueService.cooldownRemaining(player, a);
            if (remaining > 0) cooldowns.add(Component.translatable("commands.kegare.info.cooldown_entry", a.displayName(), CastManager.seconds(remaining)));
        })));
        lines.add(Component.translatable("commands.kegare.info.cooldowns", join(cooldowns)));

        List<Component> active = new ArrayList<>();
        CastManager.state(player).toggles().values().forEach(a -> active.add(a.ability().displayName()));
        if (CastManager.state(player).casting() != null) active.add(CastManager.state(player).casting().ability().displayName());
        lines.add(Component.translatable("commands.kegare.info.active", join(active)));

        List<Component> traits = new ArrayList<>();
        for (ResourceLocation id : TraitService.get(player).view()) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            traits.add(trait != null ? trait.displayName() : Component.literal(shortName(id)));
        }
        lines.add(Component.translatable("commands.kegare.info.traits", join(traits)));

        lines.add(Component.translatable("commands.kegare.info.pity", roll.rollsSinceLegendary(),
                KegareServerConfig.SOFT_PITY_START.get(), KegareServerConfig.HARD_PITY.get(), roll.totalRolls()));

        Component combat = CombatHelper.isInCombat(player)
                ? Component.translatable("commands.kegare.info.seconds", CastManager.seconds(CombatHelper.combatTicksRemaining(player)))
                : Component.translatable("commands.kegare.info.no");
        Component stun = CombatHelper.isStunned(player)
                ? Component.translatable("commands.kegare.info.seconds", CastManager.seconds(CombatHelper.stunTicksRemaining(player)))
                : Component.translatable("commands.kegare.info.no");
        lines.add(Component.translatable("commands.kegare.info.combat", combat, stun));

        for (Component line : lines) say(source, line);
        return 1;
    }

    private static int rebirth(CommandSourceStack source, ServerPlayer player, OptionalInt capacity) {
        BirthService.rebirth(player, capacity);
        say(source, "commands.kegare.rebirth", player.getDisplayName());
        return 1;
    }

    private static int loadoutReset(CommandSourceStack source, ServerPlayer player) {
        LoadoutService.reset(player);
        say(source, "commands.kegare.loadout.reset", player.getDisplayName());
        return 1;
    }

    private static Component join(List<Component> parts) {
        if (parts.isEmpty()) return Component.translatable("commands.kegare.info.none");
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }
}
