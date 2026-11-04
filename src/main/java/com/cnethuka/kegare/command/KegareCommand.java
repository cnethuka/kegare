package com.cnethuka.kegare.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.ce.CeData;
import com.cnethuka.kegare.ce.CeService;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.player.BirthService;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.roll.RollData;
import com.cnethuka.kegare.roll.RollReason;
import com.cnethuka.kegare.roll.RollService;
import com.cnethuka.kegare.technique.Ability;
import com.cnethuka.kegare.technique.CastManager;
import com.cnethuka.kegare.technique.Technique;
import com.cnethuka.kegare.technique.TechniqueData;
import com.cnethuka.kegare.technique.TechniqueService;
import com.cnethuka.kegare.trait.Trait;
import com.cnethuka.kegare.trait.TraitService;
import com.cnethuka.kegare.trait.TraitSuccession;
import com.cnethuka.kegare.trait.UniqueTraitSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private static final SuggestionProvider<CommandSourceStack> TECHNIQUES =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(KegareRegistries.TECHNIQUE.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> TRAITS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(KegareRegistries.TRAIT.keySet(), builder);
    private static final DynamicCommandExceptionType UNKNOWN_TECHNIQUE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.kegare.unknown_technique", String.valueOf(id)));
    private static final DynamicCommandExceptionType UNKNOWN_TRAIT =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.kegare.unknown_trait", String.valueOf(id)));

    private KegareCommand() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kegare")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("setce")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> setCe(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                DoubleArgumentType.getDouble(c, "amount"))))))
                .then(Commands.literal("setcapacity")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("capacity", IntegerArgumentType.integer(0, 1_000_000))
                                        .executes(c -> setCapacity(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                IntegerArgumentType.getInteger(c, "capacity"))))))
                .then(Commands.literal("settechnique")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("technique", ResourceLocationArgument.id()).suggests(TECHNIQUES)
                                        .executes(c -> setTechnique(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                ResourceLocationArgument.getId(c, "technique"))))))
                .then(Commands.literal("reroll")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> reroll(c.getSource(), EntityArgument.getPlayer(c, "player")))))
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
                                .executes(c -> rebirth(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("stun")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0, 3600))
                                        .executes(c -> stun(c.getSource(), EntityArgument.getPlayer(c, "player"),
                                                DoubleArgumentType.getDouble(c, "seconds")))))));
    }

    // ------------------------------------------------------------------ info

    private static int info(CommandSourceStack source, ServerPlayer player) {
        CeData ce = CeService.get(player);
        TechniqueData techData = TechniqueService.get(player);
        RollData roll = RollService.get(player);
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("commands.kegare.info.header", player.getDisplayName()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("commands.kegare.info.ce",
                whole(ce.current()), ce.capacity(), Component.translatable(ce.tier().translationKey())));
        if (ce.isSevered()) lines.add(Component.translatable("commands.kegare.info.severed").withStyle(ChatFormatting.RED));

        Optional<Technique> technique = TechniqueService.current(player);
        if (technique.isPresent()) {
            Technique t = technique.get();
            Ability selected = t.ability(techData.selectedSlot());
            lines.add(Component.translatable("commands.kegare.info.technique", t.displayName(),
                    Component.translatable(t.rarity().translationKey()), techData.selectedSlot() + 1, t.abilities().size(),
                    selected == null ? Component.literal("-") : selected.displayName()));
        } else {
            lines.add(Component.translatable("commands.kegare.info.no_technique"));
        }

        List<Component> cooldowns = new ArrayList<>();
        technique.ifPresent(t -> techData.cooldowns().keySet().forEach(id -> t.findAbility(id).ifPresent(a -> {
            int remaining = TechniqueService.cooldownRemaining(player, a);
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

    private static int setCe(CommandSourceStack source, ServerPlayer player, double amount) {
        CeService.setCurrent(player, amount);
        CeData ce = CeService.get(player);
        source.sendSuccess(() -> Component.translatable("commands.kegare.setce.success", player.getDisplayName(), whole(ce.current()), ce.capacity()), true);
        return 1;
    }

    private static int setCapacity(CommandSourceStack source, ServerPlayer player, int capacity) {
        CeService.adminSetCapacity(player, capacity);
        CeData ce = CeService.get(player);
        if (ce.isSevered()) TechniqueService.setTechnique(player, null); // Severed have no technique
        source.sendSuccess(() -> Component.translatable("commands.kegare.setcapacity.success", player.getDisplayName(), ce.capacity(),
                Component.translatable(ce.tier().translationKey())), true);
        if (!ce.isSevered() && TechniqueService.current(player).isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.kegare.setcapacity.hint_reroll"), false);
        }
        return 1;
    }

    private static int setTechnique(CommandSourceStack source, ServerPlayer player, ResourceLocation id) throws CommandSyntaxException {
        Technique technique = KegareRegistries.TECHNIQUE.get(id);
        if (technique == null) throw UNKNOWN_TECHNIQUE.create(id);
        if (CeService.isSevered(player)) {
            source.sendFailure(Component.translatable("commands.kegare.severed", player.getDisplayName()));
            return 0;
        }
        TechniqueService.setTechnique(player, technique);
        source.sendSuccess(() -> Component.translatable("commands.kegare.settechnique.success", player.getDisplayName(), technique.displayName()), true);
        return 1;
    }

    private static int reroll(CommandSourceStack source, ServerPlayer player) {
        RollService.RerollResult result = RollService.reroll(player, RollReason.ADMIN);
        if (result == RollService.RerollResult.SEVERED) {
            source.sendFailure(Component.translatable("commands.kegare.severed", player.getDisplayName()));
            return 0;
        }
        if (result == RollService.RerollResult.NO_CANDIDATES) {
            source.sendFailure(Component.translatable("message.kegare.reroll.no_candidates"));
            return 0;
        }
        Component name = TechniqueService.current(player).map(Technique::displayName).map(c -> (Component) c).orElse(Component.literal("-"));
        source.sendSuccess(() -> Component.translatable("commands.kegare.reroll.success", player.getDisplayName(), name,
                RollService.get(player).rollsSinceLegendary()), true);
        return 1;
    }

    private static int setPity(CommandSourceStack source, ServerPlayer player, int rolls) {
        RollService.setPity(player, rolls);
        source.sendSuccess(() -> Component.translatable("commands.kegare.setpity.success", player.getDisplayName(), rolls), true);
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
            case REQUIRES_CE -> {
                source.sendFailure(Component.translatable("commands.kegare.trait.requires_ce", trait.displayName(), player.getDisplayName()));
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

    private static int rebirth(CommandSourceStack source, ServerPlayer player) {
        BirthService.rebirth(player);
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

    /** ce is shown as whole numbers everywhere (floored, like the HUD); the stored value keeps its fraction. */
    private static int whole(double ce) {
        return (int) Math.floor(ce);
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
