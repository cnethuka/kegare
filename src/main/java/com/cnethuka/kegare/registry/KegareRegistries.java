package com.cnethuka.kegare.registry;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.technique.Technique;
import com.cnethuka.kegare.trait.Trait;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Custom registries. Techniques and traits are registry entries, so addons (or later Kegare
 * content) plug in with a plain {@code DeferredRegister} and no changes to the base.
 * Both registries sync to clients, so the HUD can resolve names and ability lists by ID.
 */
public final class KegareRegistries {
    public static final ResourceKey<Registry<Technique>> TECHNIQUE_KEY = ResourceKey.createRegistryKey(Kegare.id("technique"));
    public static final ResourceKey<Registry<Trait>> TRAIT_KEY = ResourceKey.createRegistryKey(Kegare.id("trait"));

    public static final Registry<Technique> TECHNIQUE = new RegistryBuilder<>(TECHNIQUE_KEY).sync(true).create();
    public static final Registry<Trait> TRAIT = new RegistryBuilder<>(TRAIT_KEY).sync(true).create();

    private KegareRegistries() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, event -> {
            event.register(TECHNIQUE);
            event.register(TRAIT);
        });
    }
}
