package com.cnethuka.kegare.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.rmt.GreenOrbService;
import com.cnethuka.kegare.rmt.RmtIds;
import com.cnethuka.kegare.loadout.LoadoutService;
import com.cnethuka.kegare.loadout.SharedAbilities;
import com.cnethuka.kegare.loadout.SharedAbilityService;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueData;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /kegare ...} admin/debug commands (permission level 2). */
@EventBusSubscriber(modid = Kegare.MODID)
public final class KegareCommand {
    private static final SuggestionProvider<CommandSourceStack> INNATE_TECHNIQUES =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(KegareRegistries.INNATE_TECHNIQUE.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> TRAITS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(KegareRegistries.TRAIT.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> SHARED_ABILITIES = (c, b) ->
            SharedSuggestionProvider.suggestResource(SharedAbilities.all().stream().map(Ability::id), b);
    private static final DynamicCommandExceptionType UNKNOWN_INNATE_TECHNIQUE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.kegare.unknown_innate_technique", String.valueOf(id)));
    private static final DynamicCommandExceptionType UNKNOWN_TRAIT =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.kegare.unknown_trait", String.valueOf(id)));
    private static final DynamicCommandExceptionType UNKNOWN_SHARED = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.kegare.shared.unknown", String.valueOf(id)));

    private KegareCommand() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("kegare")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("setmalice")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> setMalice(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                DoubleArgumentType.getDouble(c, "amount"))))))
                .then(Commands.literal("setcapacity")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("capacity", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> setCapacity(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "capacity"))))))
                .then(Commands.literal("setinnatetechnique")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("innate_technique", ResourceLocationArgument.id()).suggests(INNATE_TECHNIQUES)
                                        .executes(c -> setInnateTechnique(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                ResourceLocationArgument.getId(c, "innate_technique"))))))
                .then(Commands.literal("reroll")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> reroll(c.getSource(), EntityArgument.getPlayer(c, "player"), null))
                                // Debug: force the result (still goes through the roll pipeline: pity, advancements, reveal).
                                .then(Commands.argument("innate_technique", ResourceLocationArgument.id()).suggests(INNATE_TECHNIQUES)
                                        .executes(c -> reroll(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                ResourceLocationArgument.getId(c, "innate_technique"))))))
                .then(Commands.literal("setpity")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("rolls", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> setPity(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "rolls"))))))
                .then(Commands.literal("trait")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("trait", ResourceLocationArgument.id()).suggests(TRAITS)
                                                .executes(c -> traitAdd(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        ResourceLocationArgument.getId(c, "trait"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("trait", ResourceLocationArgument.id()).suggests(TRAITS)
                                                .executes(c -> traitRemove(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        ResourceLocationArgument.getId(c, "trait"))))))
                        // Frees a unique trait from its holder, online or offline. Inheritable traits then pass on.
                        .then(Commands.literal("release")
                                .then(Commands.argument("trait", ResourceLocationArgument.id()).suggests(TRAITS)
                                        .executes(c -> traitRelease(c.getSource(), ResourceLocationArgument.getId(c, "trait")))))
                        // Debug: who holds a unique trait, or whether it is passing on.
                        .then(Commands.literal("status")
                                .then(Commands.argument("trait", ResourceLocationArgument.id()).suggests(TRAITS)
                                        .executes(c -> traitStatus(c.getSource(), ResourceLocationArgument.getId(c, "trait"))))))
                .then(Commands.literal("rebirth")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> rebirth(c.getSource(), EntityArgument.getPlayer(c, "player"), OptionalInt.empty()))
                                // Debug: force the Malice capacity of the new birth (0 = Severed).
                                .then(Commands.argument("capacity", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> rebirth(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                OptionalInt.of(IntegerArgumentType.getInteger(c, "capacity")))))))
                // Shared abilities (Reverse Malice Technique...): unlock or lock again.
                .then(Commands.literal("shared")
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("ability", ResourceLocationArgument.id()).suggests(SHARED_ABILITIES)
                                                .executes(c -> sharedUnlock(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        ResourceLocationArgument.getId(c, "ability"), true)))))
                        .then(Commands.literal("lock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("ability", ResourceLocationArgument.id()).suggests(SHARED_ABILITIES)
                                                .executes(c -> sharedUnlock(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        ResourceLocationArgument.getId(c, "ability"), false))))))
                // The green orb (finds Reverse Malice Technique): force one now, or see the roll cooldown.
                .then(Commands.literal("orb")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> orbSpawn(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> orbStatus(c.getSource(), EntityArgument.getPlayer(c, "player"))))))
                // Loadout back to the innate technique's default layout.
                .then(Commands.literal("loadout")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> loadoutReset(c.getSource(), EntityArgument.getPlayer(c, "player"))))))
                // Debug for Swap: make a player unswappable (cancels SwapTargetEvent; a stand-in for a future immunity).
                .then(Commands.literal("swap")
                        .then(Commands.literal("unswappable")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("on", BoolArgumentType.bool())
                                                .executes(c -> swapUnswappable(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        BoolArgumentType.getBool(c, "on")))))))
                // Debug for Strings: make a player unmovable (cancels StringsTargetEvent; a stand-in for a future immunity).
                .then(Commands.literal("strings")
                        .then(Commands.literal("unmovable")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("on", BoolArgumentType.bool())
                                                .executes(c -> stringsUnmovable(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                        BoolArgumentType.getBool(c, "on")))))))
                .then(Commands.literal("stun")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 3600))
                                        .executes(c -> stun(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                DoubleArgumentType.getDouble(c, "seconds"))))));
        ProgressionCommands.register(root);
        dispatcher.register(root);
    }

    private static int stringsUnmovable(CommandSourceStack source, ServerPlayer player, boolean on) {
        com.cnethuka.kegare.innate_technique.puppet_manipulation.StringsDebug.setUnmovable(player.getUUID(), on);
        source.sendSuccess(() -> Component.translatable(on ? "commands.kegare.strings.unmovable.on" : "commands.kegare.strings.unmovable.off",
                player.getDisplayName()), true);
        return 1;
    }

    // ------------------------------------------------------------------ info

    private static int info(CommandSourceStack source, ServerPlayer player) {
        MaliceData malice = MaliceService.get(player);
        InnateTechniqueData innateTechniqueData = InnateTechniqueService.get(player);
        RollData roll = RollService.get(player);
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("commands.kegare.info.header", player.getDisplayName()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("commands.kegare.info.malice",
                whole(malice.current()), malice.capacity(), Component.translatable(malice.tier().translationKey())));
        if (malice.isSevered()) lines.add(Component.translatable("commands.kegare.info.severed").withStyle(ChatFormatting.RED));

        Optional<InnateTechnique> innateTechnique = InnateTechniqueService.current(player);
        if (innateTechnique.isPresent()) {
            InnateTechnique t = innateTechnique.get();
            Ability selected = LoadoutService.abilityAt(player, innateTechniqueData.selectedSlot());
            lines.add(Component.translatable("commands.kegare.info.innate_technique", t.displayName(),
                    Component.translatable(t.rarity().translationKey()), innateTechniqueData.selectedSlot() + 1, LoadoutService.size(player),
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
            traits.add(trait != null ? trait.displayName() : Component.literal(id.toString()));
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

        for (Component line : lines) source.sendSuccess(() -> line, false);
        return 1;
    }

    // ------------------------------------------------------------------ mutations

    private static int setMalice(CommandSourceStack source, ServerPlayer player, double amount) {
        MaliceService.setCurrent(player, amount);
        MaliceData malice = MaliceService.get(player);
        source.sendSuccess(() -> Component.translatable("commands.kegare.setmalice.success", player.getDisplayName(), whole(malice.current()), malice.capacity()), true);
        return 1;
    }

    private static int setCapacity(CommandSourceStack source, ServerPlayer player, int capacity) {
        MaliceService.adminSetCapacity(player, capacity);
        MaliceData malice = MaliceService.get(player);
        if (malice.isSevered()) InnateTechniqueService.setInnateTechnique(player, null); // Severed have no innate technique
        source.sendSuccess(() -> Component.translatable("commands.kegare.setcapacity.success", player.getDisplayName(), malice.capacity(),
                Component.translatable(malice.tier().translationKey())), true);
        if (!malice.isSevered() && InnateTechniqueService.current(player).isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.kegare.setcapacity.hint_reroll"), false);
        }
        return 1;
    }

    private static int setInnateTechnique(CommandSourceStack source, ServerPlayer player, ResourceLocation id) throws CommandSyntaxException {
        InnateTechnique innateTechnique = KegareRegistries.INNATE_TECHNIQUE.get(id);
        if (innateTechnique == null) throw UNKNOWN_INNATE_TECHNIQUE.create(id);
        if (MaliceService.isSevered(player)) {
            source.sendFailure(Component.translatable("commands.kegare.severed", player.getDisplayName()));
            return 0;
        }
        InnateTechniqueService.setInnateTechnique(player, innateTechnique);
        source.sendSuccess(() -> Component.translatable("commands.kegare.setinnatetechnique.success", player.getDisplayName(), innateTechnique.displayName()), true);
        return 1;
    }

    private static int reroll(CommandSourceStack source, ServerPlayer player, @Nullable ResourceLocation forcedId) throws CommandSyntaxException {
        InnateTechnique forced = null;
        if (forcedId != null) {
            forced = KegareRegistries.INNATE_TECHNIQUE.get(forcedId);
            if (forced == null) throw UNKNOWN_INNATE_TECHNIQUE.create(forcedId);
        }
        RollService.RerollResult result = RollService.reroll(player, RollReason.ADMIN, forced);
        if (result == RollService.RerollResult.SEVERED) {
            source.sendFailure(Component.translatable("commands.kegare.severed", player.getDisplayName()));
            return 0;
        }
        if (result == RollService.RerollResult.NO_CANDIDATES) {
            source.sendFailure(Component.translatable("message.kegare.reroll.no_candidates"));
            return 0;
        }
        Component name = InnateTechniqueService.current(player).map(InnateTechnique::displayName).map(c -> (Component) c).orElse(Component.translatable("commands.kegare.info.none"));
        source.sendSuccess(() -> Component.translatable("commands.kegare.reroll.success", player.getDisplayName(), name,
                RollService.get(player).rollsSinceLegendary()), true);
        return 1;
    }

    private static int setPity(CommandSourceStack source, ServerPlayer player, int rolls) {
        RollService.setPity(player, rolls);
        source.sendSuccess(() -> Component.translatable("commands.kegare.setpity.success", player.getDisplayName(), rolls), true);
        return 1;
    }

    private static int swapUnswappable(CommandSourceStack source, ServerPlayer player, boolean on) {
        com.cnethuka.kegare.innate_technique.swap.SwapDebug.setUnswappable(player.getUUID(), on);
        source.sendSuccess(() -> Component.translatable(on ? "commands.kegare.swap.unswappable.on" : "commands.kegare.swap.unswappable.off",
                player.getDisplayName()), true);
        return 1;
    }

    private static int sharedUnlock(CommandSourceStack source, ServerPlayer player, ResourceLocation id, boolean unlock) throws CommandSyntaxException {
        Ability ability = SharedAbilities.get(id).orElseThrow(() -> UNKNOWN_SHARED.create(id));
        boolean changed = unlock ? SharedAbilityService.unlock(player, ability) : SharedAbilityService.lock(player, ability);
        if (!changed) {
            source.sendFailure(Component.translatable(unlock ? (MaliceService.isSevered(player) ? "commands.kegare.shared.severed" : "commands.kegare.shared.already")
                    : "commands.kegare.shared.not_unlocked", player.getDisplayName(), ability.displayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(unlock ? "commands.kegare.shared.unlocked" : "commands.kegare.shared.locked",
                ability.displayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int orbSpawn(CommandSourceStack source, ServerPlayer player) {
        GreenOrbService.ForceResult result = GreenOrbService.force(player);
        if (result != GreenOrbService.ForceResult.SPAWNED) {
            source.sendFailure(Component.translatable("commands.kegare.orb." + result.name().toLowerCase(java.util.Locale.ROOT), player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.kegare.orb.spawned", player.getDisplayName()), true);
        return 1;
    }

    private static int orbStatus(CommandSourceStack source, ServerPlayer player) {
        Component unlocked = Component.translatable(SharedAbilityService.isUnlocked(player, RmtIds.ABILITY)
                ? "commands.kegare.orb.status.unlocked" : "commands.kegare.orb.status.locked");
        Component waiting = Component.translatable(GreenOrbService.state(player).hasOrb() ? "commands.kegare.orb.status.yes" : "commands.kegare.orb.status.no");
        source.sendSuccess(() -> Component.translatable("commands.kegare.orb.status", player.getDisplayName(),
                CastManager.seconds(GreenOrbService.rollCooldownRemaining(player)), waiting, unlocked,
                Component.translatable(GreenOrbService.eligible(player) ? "commands.kegare.orb.status.yes" : "commands.kegare.orb.status.no")), false);
        return 1;
    }

    private static int loadoutReset(CommandSourceStack source, ServerPlayer player) {
        LoadoutService.reset(player);
        source.sendSuccess(() -> Component.translatable("commands.kegare.loadout.reset", player.getDisplayName()), true);
        return 1;
    }

    private static int traitAdd(CommandSourceStack source, ServerPlayer player, ResourceLocation id) throws CommandSyntaxException {
        Trait trait = KegareRegistries.TRAIT.get(id);
        if (trait == null) throw UNKNOWN_TRAIT.create(id);
        switch (TraitService.add(player, trait, Trait.AddReason.ADMIN)) {
            case ADDED -> source.sendSuccess(() -> Component.translatable("commands.kegare.trait.added", trait.displayName(), player.getDisplayName()), true);
            case PENDING_SUCCESSION -> { // not reachable for admin grants (they override succession), kept for completeness
                source.sendFailure(Component.translatable("commands.kegare.trait.status.waiting", trait.displayName()));
                return 0;
            }
            case ALREADY_HAS -> {
                source.sendFailure(Component.translatable("commands.kegare.trait.already_has", player.getDisplayName(), trait.displayName()));
                return 0;
            }
            case UNIQUE_TAKEN -> {
                String holder = TraitService.bearer(source.getServer(), trait).map(u -> TraitService.nameOf(source.getServer(), u)).orElse("?");
                source.sendFailure(Component.translatable("commands.kegare.trait.unique_taken", trait.displayName(), holder));
                return 0;
            }
            case REQUIRES_MALICE -> {
                source.sendFailure(Component.translatable("commands.kegare.trait.requires_malice", trait.displayName(), player.getDisplayName()));
                return 0;
            }
        }
        return 1;
    }

    private static int traitRelease(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        Trait trait = KegareRegistries.TRAIT.get(id);
        if (trait == null) throw UNKNOWN_TRAIT.create(id);
        if (!trait.isUnique()) {
            source.sendFailure(Component.translatable("commands.kegare.trait.not_unique", trait.displayName()));
            return 0;
        }
        Optional<java.util.UUID> holder = TraitSuccession.release(source.getServer(), trait);
        if (holder.isEmpty()) {
            source.sendFailure(Component.translatable("commands.kegare.trait.unclaimed", trait.displayName()));
            return 0;
        }
        String name = TraitService.nameOf(source.getServer(), holder.get());
        boolean online = source.getServer().getPlayerList().getPlayer(holder.get()) != null;
        source.sendSuccess(() -> Component.translatable(online ? "commands.kegare.trait.released" : "commands.kegare.trait.released_offline",
                trait.displayName(), name), true);
        if (trait.isInheritable()) {
            source.sendSuccess(() -> Component.translatable("commands.kegare.trait.passing_on", trait.displayName(),
                    CastManager.seconds(trait.successionDelayTicks())), false);
        }
        return 1;
    }

    private static int traitStatus(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        Trait trait = KegareRegistries.TRAIT.get(id);
        if (trait == null) throw UNKNOWN_TRAIT.create(id);
        UniqueTraitSavedData saved = UniqueTraitSavedData.get(source.getServer());
        Optional<java.util.UUID> holder = saved.bearer(id);
        Optional<UniqueTraitSavedData.Pending> pending = saved.pending(id);
        Component line;
        if (holder.isPresent()) {
            line = Component.translatable("commands.kegare.trait.status.held", trait.displayName(), TraitService.nameOf(source.getServer(), holder.get()));
        } else if (pending.isPresent()) {
            long remaining = Math.max(0, pending.get().dueTick() - source.getServer().overworld().getGameTime());
            line = remaining > 0
                    ? Component.translatable("commands.kegare.trait.status.passing", trait.displayName(), CastManager.seconds((int) remaining))
                    : Component.translatable("commands.kegare.trait.status.waiting", trait.displayName());
        } else {
            line = Component.translatable("commands.kegare.trait.status.unclaimed", trait.displayName());
        }
        source.sendSuccess(() -> line, false);
        return 1;
    }

    private static int traitRemove(CommandSourceStack source, ServerPlayer player, ResourceLocation id) throws CommandSyntaxException {
        Trait trait = KegareRegistries.TRAIT.get(id);
        if (trait == null) throw UNKNOWN_TRAIT.create(id);
        if (!TraitService.remove(player, trait, Trait.RemoveReason.ADMIN)) {
            source.sendFailure(Component.translatable("commands.kegare.trait.not_present", player.getDisplayName(), trait.displayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.kegare.trait.removed", trait.displayName(), player.getDisplayName()), true);
        return 1;
    }

    private static int rebirth(CommandSourceStack source, ServerPlayer player, OptionalInt capacity) {
        BirthService.rebirth(player, capacity);
        source.sendSuccess(() -> Component.translatable("commands.kegare.rebirth.success", player.getDisplayName()), true);
        return 1;
    }

    private static int stun(CommandSourceStack source, ServerPlayer player, double seconds) {
        int ticks = (int) Math.round(seconds * 20);
        if (ticks <= 0) CombatHelper.clearStun(player);
        else CombatHelper.stun(player, ticks);
        source.sendSuccess(() -> Component.translatable("commands.kegare.stun.success", player.getDisplayName(), fmt(seconds)), true);
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Malice is shown as whole numbers everywhere (floored, like the HUD); the stored value keeps its fraction. */
    private static int whole(double malice) {
        return (int) Math.floor(malice);
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
