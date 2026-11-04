package com.cnethuka.kegare.innatetechnique.test;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * TEST CONTENT. Four placeholder innate techniques that prove the registry, roll, cast and reveal pipeline.
 * One per rarity, so every reveal can be shown.
 * Delete this package (and the {@code [test_abilities]} config) when real innate techniques land.
 */
public final class TestInnateTechniques {
    public static final DeferredRegister<InnateTechnique> INNATE_TECHNIQUES = DeferredRegister.create(KegareRegistries.INNATE_TECHNIQUE_KEY, Kegare.MODID);

    /** COMMON: dash (instant) + burst (charged). */
    public static final DeferredHolder<InnateTechnique, InnateTechnique> KINETIC = INNATE_TECHNIQUES.register("test_kinetic",
            () -> InnateTechnique.builder(InnateTechniqueRarity.COMMON)
                    .ability(TestAbilities.DASH)
                    .ability(TestAbilities.BURST)
                    .testContent()
                    .build());

    /** RARE: hover (channeled) + aura (toggle). */
    public static final DeferredHolder<InnateTechnique, InnateTechnique> FLUX = INNATE_TECHNIQUES.register("test_flux",
            () -> InnateTechnique.builder(InnateTechniqueRarity.RARE)
                    .ability(TestAbilities.HOVER)
                    .ability(TestAbilities.AURA)
                    .testContent()
                    .build());

    /** EPIC: burst (charged) + hover (channeled), shockwave ultimate. Exists so every rarity can be revealed. */
    public static final DeferredHolder<InnateTechnique, InnateTechnique> PRISM = INNATE_TECHNIQUES.register("test_prism",
            () -> InnateTechnique.builder(InnateTechniqueRarity.EPIC)
                    .ability(TestAbilities.BURST)
                    .ability(TestAbilities.HOVER)
                    .ultimate(TestAbilities.SHOCKWAVE)
                    .testContent()
                    .build());

    /** LEGENDARY: every cast type plus an ultimate. */
    public static final DeferredHolder<InnateTechnique, InnateTechnique> APEX = INNATE_TECHNIQUES.register("test_apex",
            () -> InnateTechnique.builder(InnateTechniqueRarity.LEGENDARY)
                    .ability(TestAbilities.DASH)
                    .ability(TestAbilities.BURST)
                    .ability(TestAbilities.HOVER)
                    .ability(TestAbilities.AURA)
                    .ultimate(TestAbilities.SHOCKWAVE)
                    .testContent()
                    .build());

    private TestInnateTechniques() {}
}
