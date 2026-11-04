package com.cnethuka.kegare.trait;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KegareTraits {
    public static final DeferredRegister<Trait> TRAITS = DeferredRegister.create(KegareRegistries.TRAIT_KEY, Kegare.MODID);

    /** Unique, needs ce, passes on when its bearer is killed by a player. NO gameplay effects yet. */
    public static final DeferredHolder<Trait, Trait> LENSING_EYES = TRAITS.register("lensing_eyes", LensingEyesTrait::new);

    private KegareTraits() {}
}
