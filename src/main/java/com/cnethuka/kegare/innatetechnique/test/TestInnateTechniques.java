package com.cnethuka.kegare.innatetechnique.test;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * TEST CONTENT. Three placeholder innate techniques that prove the registry, roll and cast pipeline.
 * Epic is intentionally empty, so rolls also exercise "skip rarities with no innate technique".
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
