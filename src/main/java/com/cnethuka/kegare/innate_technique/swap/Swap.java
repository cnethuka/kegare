package com.cnethuka.kegare.innate_technique.swap;

import com.cnethuka.kegare.Kegare;
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
 * Swap (COMMON): trade with other players. Positions, Awareness, Anchor and Exchange, with Switchback, Mimic, Send
 * Off and Momentum Exchange on R; Total Swap as the ultimate. Every swap fires {@link SwapTargetEvent} first. All
 * numbers: [swap] in kegare-server.toml.
 */
public final class Swap {
    public static final DeferredRegister<InnateTechnique> INNATE_TECHNIQUES = DeferredRegister.create(KegareRegistries.INNATE_TECHNIQUE_KEY, Kegare.MODID);

    /** Swap's Malice bar: teal. */
    public static final int MALICE_COLOR = 0x14B8A6;

    public static final PositionsAbility POSITIONS = new PositionsAbility();
    public static final AwarenessAbility AWARENESS = new AwarenessAbility();
    public static final AnchorAbility ANCHOR = new AnchorAbility();
    public static final ExchangeAbility EXCHANGE = new ExchangeAbility();

    // Specials (R), each sharing its base ability's cooldown.
    public static final SwitchbackAbility SWITCHBACK = new SwitchbackAbility();
    public static final MimicAbility MIMIC = new MimicAbility();
    public static final SendOffAbility SEND_OFF = new SendOffAbility();
    public static final MomentumExchangeAbility MOMENTUM_EXCHANGE = new MomentumExchangeAbility();

    public static final DeferredHolder<InnateTechnique, InnateTechnique> SWAP = INNATE_TECHNIQUES.register("swap",
            () -> InnateTechnique.builder(InnateTechniqueRarity.COMMON)
                    .ability(POSITIONS)
                    .ability(AWARENESS)
                    .ability(ANCHOR)
                    .ability(EXCHANGE)
                    .lockedUltimate()
                    .maliceColor(MALICE_COLOR)
                    .tabIcon(Items.ENDER_PEARL)
                    .build());

    private Swap() {}

    public static void register(IEventBus modBus) {
        INNATE_TECHNIQUES.register(modBus);
        SwapRegistry.register(modBus);
        VisualHooks.registerEffect(SwapIds.FX_SWAP_DEPART);
        VisualHooks.registerEffect(SwapIds.FX_SWAP_ARRIVE);
        VisualHooks.registerEffect(SwapIds.FX_SWAP_LINK);
        VisualHooks.registerEffect(SwapIds.FX_SWITCHBACK_TIMER);
        VisualHooks.registerEffect(SwapIds.FX_AWARENESS_SHIMMER);
        VisualHooks.registerEffect(SwapIds.FX_ANCHOR_MARKER);
        VisualHooks.registerEffect(SwapIds.FX_ANCHOR_TRAIL);
        VisualHooks.registerEffect(SwapIds.FX_EXCHANGE_TRANSFER);
        VisualHooks.registerEffect(SwapIds.FX_MOMENTUM_BURST);
    }

    public static boolean holds(ServerPlayer player) {
        return InnateTechniqueService.current(player).map(t -> t == SWAP.get()).orElse(false);
    }
}
