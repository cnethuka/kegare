package com.cnethuka.kegare.innate_technique.rime.client;

import com.cnethuka.kegare.client.visual.VanillaFallback;
import com.cnethuka.kegare.innate_technique.rime.RimeIds;
import com.cnethuka.kegare.innate_technique.rime.RimeRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Rime's client side: projectile renderers and the vanilla-particle stand-ins for its Photon effects. */
public final class RimeClient {
    private RimeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RimeClient::registerRenderers);
        // Lookalikes used when an effect can't load, or with vfxQuality = VANILLA.
        VanillaFallback.register(RimeIds.FX_FROST_SHARD_TRAIL, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 2, 6, 0.06, 0.01));
        VanillaFallback.register(RimeIds.FX_FROST_SHARD_IMPACT, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 14, 0.25, 0.12));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_CHARGE, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 2, 6, 0.25, 0.02));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_TRAIL, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 4, 8, 0.15, 0.02));
        VanillaFallback.register(RimeIds.FX_GLACIAL_LANCE_IMPACT, new VanillaFallback.Spec(
                new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PACKED_ICE)), 1, 28, 0.5, 0.25));
        VanillaFallback.register(RimeIds.FX_FROST_FIELD_CONE, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 6, 10, 0.2, 0.45, true));
        VanillaFallback.register(RimeIds.FX_RIME_SHELL_AURA, new VanillaFallback.Spec(ParticleTypes.SNOWFLAKE, 1, 6, 0.55, 0.0));
        VanillaFallback.register(RimeIds.FX_FREEZE_SHATTER, new VanillaFallback.Spec(
                new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ICE)), 1, 24, 0.45, 0.2));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // The item sprite, full-bright so the ice reads at night.
        event.registerEntityRenderer(RimeRegistry.FROST_SHARD.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        event.registerEntityRenderer(RimeRegistry.GLACIAL_LANCE.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.8f, true));
    }
}
