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

    // Specials (R), each sharing its base ability's cooldown.
    public static final PossessAbility POSSESS = new PossessAbility();
    public static final FocusAbility FOCUS = new FocusAbility();
    public static final ChainBurstAbility CHAIN_BURST = new ChainBurstAbility();

    public static final DeferredHolder<InnateTechnique, InnateTechnique> PUPPET_MANIPULATION = INNATE_TECHNIQUES.register("puppet_manipulation",
            () -> new InnateTechnique(InnateTechnique.builder(InnateTechniqueRarity.EPIC)
                    .ability(PUPPET_CREATION)
                    .ability(DIRECTIVE)
                    .ability(CURIO_BURST)
                    .lockedUltimate()
                    .maliceColor(MALICE_COLOR)
                    .tabIcon(Items.ARMOR_STAND)) {
                /** Rerolled (or reborn, or set by an admin) away from it: every puppet dissolves, loaded or not. */
                @Override
                public void onRemoved(ServerPlayer player) {
                    if (holds(player)) return;
                    Possession.release(player, Possession.Return.TECHNIQUE_CHANGED);
                    Puppets.dissolveAll(player.server, player.getUUID());
                }
            });

    private PuppetManipulation() {}

    public static void register(IEventBus modBus) {
        INNATE_TECHNIQUES.register(modBus);
        PuppetRegistry.register(modBus);
        CastManager.addInterceptor(Possession::intercept); // possessing: R returns, nothing else casts
        VisualHooks.registerEffect(PuppetIds.FX_PUPPET_FORM);
        VisualHooks.registerEffect(PuppetIds.FX_PUPPET_DISSOLVE);
        for (PuppetTier tier : PuppetTier.values()) VisualHooks.registerEffect(CurioBursts.effect(tier));
    }

    public static boolean holds(ServerPlayer player) {
        return InnateTechniqueService.current(player).map(t -> t == PUPPET_MANIPULATION.get()).orElse(false);
    }
}
