package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.CostEnvironment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;

/**
 * Where Rime is cheap or costly. Cold factors (rain/snow, water, cold biome) each multiply; a hot biome or the Nether
 * makes it dearer. The result multiplies every Rime cost and upkeep. See [rime.environment] in the server config.
 */
public final class RimeEnvironment {
    public static final String PRECIPITATION = "environment.kegare.rime.precipitation";
    public static final String WATER = "environment.kegare.rime.water";
    public static final String COLD_BIOME = "environment.kegare.rime.cold_biome";
    public static final String HOT_BIOME = "environment.kegare.rime.hot_biome";
    public static final String NETHER = "environment.kegare.rime.nether";

    private RimeEnvironment() {}

    public static CostEnvironment evaluate(Entity entity) {
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        List<CostEnvironment.Factor> factors = new ArrayList<>();
        boolean hot = false;
        if (level.dimensionType().ultraWarm()) {
            factors.add(new CostEnvironment.Factor(NETHER, RimeConfig.ENV_NETHER.get().floatValue()));
            hot = true;
        } else {
            if (precipitationAt(level, pos)) factors.add(new CostEnvironment.Factor(PRECIPITATION, RimeConfig.ENV_PRECIPITATION.get().floatValue()));
            if (inOrNextToWater(entity)) factors.add(new CostEnvironment.Factor(WATER, RimeConfig.ENV_WATER.get().floatValue()));
            float temperature = level.getBiome(pos).value().getBaseTemperature();
            if (temperature < RimeConfig.ENV_COLD_BIOME_TEMPERATURE.get()) {
                factors.add(new CostEnvironment.Factor(COLD_BIOME, RimeConfig.ENV_COLD_BIOME.get().floatValue()));
            } else if (temperature >= RimeConfig.ENV_HOT_BIOME_TEMPERATURE.get()) {
                factors.add(new CostEnvironment.Factor(HOT_BIOME, RimeConfig.ENV_HOT_BIOME.get().floatValue()));
                hot = true;
            }
        }
        float multiplier = 1f;
        for (CostEnvironment.Factor f : factors) multiplier *= f.multiplier();
        float min = RimeConfig.ENV_MIN.get().floatValue();
        float max = Math.max(min, RimeConfig.ENV_MAX.get().floatValue());
        multiplier = Mth.clamp(multiplier, min, max);
        CostEnvironment.Icon icon = hot ? CostEnvironment.Icon.HOT : factors.isEmpty() ? CostEnvironment.Icon.NONE : CostEnvironment.Icon.COLD;
        return new CostEnvironment(multiplier, icon, List.copyOf(factors));
    }

    /** Rain or snow is falling on this spot. */
    public static boolean precipitationAt(Level level, BlockPos pos) {
        return level.isRainingAt(pos.above()) || snowingAt(level, pos);
    }

    /** Snow is falling on this spot (vanilla's isRainingAt is false for snow). */
    public static boolean snowingAt(Level level, BlockPos pos) {
        if (!level.isRaining() || !level.canSeeSky(pos.above())) return false;
        return level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.SNOW;
    }

    /** "Home Field": snow falling on you in a cold biome. */
    public static boolean snowfallInColdBiome(Entity entity) {
        BlockPos pos = entity.blockPosition();
        return snowingAt(entity.level(), pos)
                && entity.level().getBiome(pos).value().getBaseTemperature() < RimeConfig.ENV_COLD_BIOME_TEMPERATURE.get();
    }

    /** In water, or water within a block of the body. */
    public static boolean inOrNextToWater(Entity entity) {
        if (entity.isInWater()) return true;
        AABB box = entity.getBoundingBox().inflate(1.0, 0.0, 1.0);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (entity.level().getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }
}
