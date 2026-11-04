package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.CostEnvironment;
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
 * Rime (RARE): the first real innate technique. Frost Shard, Glacial Lance, Frost Field, Rime Shell; no ultimate yet
 * (the slot shows as locked until its Dominion arrives). Freeze stacks on targets; cheaper in the cold, dearer in the
 * heat. All numbers: [rime] in kegare-server.toml.
 */
public final class Rime {
    public static final DeferredRegister<InnateTechnique> INNATE_TECHNIQUES = DeferredRegister.create(KegareRegistries.INNATE_TECHNIQUE_KEY, Kegare.MODID);

    public static final FrostShardAbility FROST_SHARD = new FrostShardAbility();
    public static final GlacialLanceAbility GLACIAL_LANCE = new GlacialLanceAbility();
    public static final FrostFieldAbility FROST_FIELD = new FrostFieldAbility();
    public static final RimeShellAbility RIME_SHELL = new RimeShellAbility();

    public static final DeferredHolder<InnateTechnique, InnateTechnique> RIME = INNATE_TECHNIQUES.register("rime",
            () -> new RimeInnateTechnique(InnateTechnique.builder(InnateTechniqueRarity.RARE)
                    .ability(FROST_SHARD)
                    .ability(GLACIAL_LANCE)
                    .ability(FROST_FIELD)
                    .ability(RIME_SHELL)
                    .lockedUltimate()
                    .tabIcon(Items.BLUE_ICE)));

    private Rime() {}

    public static void register(IEventBus modBus) {
        INNATE_TECHNIQUES.register(modBus);
        RimeRegistry.register(modBus);
        VisualHooks.registerEffect(RimeIds.FX_FREEZE_SHATTER);
    }

    public static boolean holds(ServerPlayer player) {
        return InnateTechniqueService.current(player).map(t -> t == RIME.get()).orElse(false);
    }

    private static final class RimeInnateTechnique extends InnateTechnique {
        RimeInnateTechnique(Builder builder) {
            super(builder);
        }

        @Override
        public CostEnvironment environment(ServerPlayer player) {
            return RimeEnvironment.evaluate(player);
        }
    }
}
