package com.cnethuka.kegare.innate_technique.time_manipulation;

import com.cnethuka.kegare.Kegare;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Time Manipulation's registry entries: Decelerate's projectile. */
public final class TimeRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Kegare.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DecelerateEntity>> DECELERATE = ENTITIES.register("decelerate",
            () -> EntityType.Builder.<DecelerateEntity>of(DecelerateEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(6).updateInterval(2).build("decelerate"));

    private TimeRegistry() {}

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
