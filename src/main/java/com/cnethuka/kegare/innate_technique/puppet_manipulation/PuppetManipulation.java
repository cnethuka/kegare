package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.VisualHooks;
import com.cnethuka.kegare.registry.KegareRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Puppet Manipulation (EPIC): half-size puppets of yourself, charged with Malice. Puppet Creation makes them; the
 * Dominion ultimate comes later (locked). All numbers: [puppet_manipulation] in kegare-server.toml.
 */
public final class PuppetManipulation {
    public static final DeferredRegister<InnateTechnique> INNATE_TECHNIQUES = DeferredRegister.create(KegareRegistries.INNATE_TECHNIQUE_KEY, Kegare.MODID);

    /** The Malice bar and the puppets' glow: lavender. */
    public static final int MALICE_COLOR = 0xC4B5FD;

    public static final PuppetCreationAbility PUPPET_CREATION = new PuppetCreationAbility();
    public static final DirectiveAbility DIRECTIVE = new DirectiveAbility();
    public static final CurioBurstAbility CURIO_BURST = new CurioBurstAbility();
    public static final UnderstudyAbility UNDERSTUDY = new UnderstudyAbility();
    public static final StringsAbility STRINGS = new StringsAbility();

    // Specials (R), each sharing its base ability's cooldown.
    public static final PossessAbility POSSESS = new PossessAbility();
    public static final FocusAbility FOCUS = new FocusAbility();
    public static final ChainBurstAbility CHAIN_BURST = new ChainBurstAbility();
    public static final StandInAbility STAND_IN = new StandInAbility();
    public static final ZipLeashAbility ZIP_LEASH = new ZipLeashAbility();

    public static final DeferredHolder<InnateTechnique, InnateTechnique> PUPPET_MANIPULATION = INNATE_TECHNIQUES.register("puppet_manipulation",
            () -> new InnateTechnique(InnateTechnique.builder(InnateTechniqueRarity.EPIC)
                    .ability(PUPPET_CREATION)
                    .ability(DIRECTIVE)
                    .ability(CURIO_BURST)
                    .ability(UNDERSTUDY)
                    .ability(STRINGS)
                    .lockedUltimate()
                    .maliceColor(MALICE_COLOR)
                    .signatureAnimation(PuppetIds.ANIM_CREATE_RELEASE)
                    .tabIcon(Items.ARMOR_STAND)) {
                /** Rerolled (or reborn, or set by an admin) away from it: every puppet dissolves, loaded or not. */
                @Override
                public void onRemoved(ServerPlayer player) {
                    if (holds(player)) return;
                    Possession.release(player, Possession.Return.TECHNIQUE_CHANGED);
                    Strings.cut(player);
                    Puppets.dissolveAll(player.server, player.getUUID());
                }
            });

    private PuppetManipulation() {}

    public static void register(IEventBus modBus) {
        INNATE_TECHNIQUES.register(modBus);
        PuppetRegistry.register(modBus);
        CastManager.addInterceptor(Possession::intercept); // possessing: R returns, nothing else casts
        // A possessed puppet's punch is the player's own melee, so nothing else ties it to Possess.
        com.cnethuka.kegare.progression.Attribution.addAttributor((attacker, source) ->
                Possession.isPossessing(attacker) && source.getDirectEntity() == attacker ? POSSESS : null);
        VisualHooks.registerEffect(PuppetIds.FX_PUPPET_FORM);
        VisualHooks.registerEffect(PuppetIds.FX_PUPPET_DISSOLVE);
        VisualHooks.registerEffect(PuppetIds.FX_POSSESS_LINK);
        VisualHooks.registerEffect(PuppetIds.FX_DIRECTIVE_PULSE);
        VisualHooks.registerEffect(PuppetIds.FX_FOCUS_MARK);
        VisualHooks.registerEffect(PuppetIds.FX_STAND_IN_GROW);
        VisualHooks.registerEffect(PuppetIds.FX_UNDERSTUDY_SWAP);
        VisualHooks.registerEffect(PuppetIds.FX_STRINGS_ATTACH);
        VisualHooks.registerEffect(PuppetIds.FX_STRINGS_SNAP);
        VisualHooks.registerEffect(PuppetIds.FX_STRINGS_TENSION);
        for (PuppetTier tier : PuppetTier.values()) VisualHooks.registerEffect(CurioBursts.effect(tier));
    }

    public static boolean holds(ServerPlayer player) {
        return InnateTechniqueService.current(player).map(t -> t == PUPPET_MANIPULATION.get()).orElse(false);
    }
}
