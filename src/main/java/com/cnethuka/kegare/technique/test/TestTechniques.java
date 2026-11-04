package com.cnethuka.kegare.technique.test;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.technique.Technique;
import com.cnethuka.kegare.technique.TechniqueRarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * TEST CONTENT. Three placeholder techniques that prove the registry, roll and cast pipeline.
 * Epic is intentionally empty, so rolls also exercise "skip rarities with no technique".
 * Delete this package (and the {@code [test_abilities]} config) when real techniques land.
 */
public final class TestTechniques {
    public static final DeferredRegister<Technique> TECHNIQUES = DeferredRegister.create(KegareRegistries.TECHNIQUE_KEY, Kegare.MODID);

    /** COMMON: dash (instant) + burst (charged). */
    public static final DeferredHolder<Technique, Technique> KINETIC = TECHNIQUES.register("test_kinetic",
            () -> Technique.builder(TechniqueRarity.COMMON)
                    .ability(TestAbilities.DASH)
                    .ability(TestAbilities.BURST)
                    .testContent()
                    .build());

    /** RARE: hover (channeled) + aura (toggle). */
    public static final DeferredHolder<Technique, Technique> FLUX = TECHNIQUES.register("test_flux",
            () -> Technique.builder(TechniqueRarity.RARE)
                    .ability(TestAbilities.HOVER)
                    .ability(TestAbilities.AURA)
                    .testContent()
                    .build());

    /** LEGENDARY: every cast type plus an ultimate. */
    public static final DeferredHolder<Technique, Technique> APEX = TECHNIQUES.register("test_apex",
            () -> Technique.builder(TechniqueRarity.LEGENDARY)
                    .ability(TestAbilities.DASH)
                    .ability(TestAbilities.BURST)
                    .ability(TestAbilities.HOVER)
                    .ability(TestAbilities.AURA)
                    .ultimate(TestAbilities.SHOCKWAVE)
                    .testContent()
                    .build());

    private TestTechniques() {}
}
