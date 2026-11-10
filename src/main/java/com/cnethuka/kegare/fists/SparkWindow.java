package com.cnethuka.kegare.fists;

import com.cnethuka.kegare.innate_technique.rime.FreezeService;
import com.cnethuka.kegare.malice.MaliceData;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.GradeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * How wide a Black Spark's window is on this try (DESIGN §3.28): {@code windowMs} times one factor for each condition behind it, worked out
 * fresh each time, never past {@code maxWindowShare} of the jitter. Each factor is small, and all of them are config.
 */
public record SparkWindow(double malice, double opponent, double body, double weather, double grade, double zone, double ms) {
    /** The factors multiplied together. */
    public double factor() {
        return malice * opponent * body * weather * grade * zone;
    }

    public static SparkWindow of(ServerPlayer attacker, LivingEntity target, boolean inZone) {
        double malice = malice(attacker), opponent = opponent(attacker, target), body = body(attacker), weather = weather(attacker.level(),
                attacker.blockPosition()), grade = grade(attacker), zone = inZone ? FistsConfig.ZONE_WINDOW.get() : 1.0;
        double ms = FistsConfig.WINDOW_MS.get() * malice * opponent * body * weather * grade * zone;
        ms = Math.min(ms, FistsConfig.MAX_WINDOW_SHARE.get() * FistsConfig.JITTER_MS.get());
        return new SparkWindow(malice, opponent, body, weather, grade, zone, ms);
    }

    /** Your Malice, current against capacity: fuller is wider. */
    static double malice(ServerPlayer attacker) {
        MaliceData data = MaliceService.get(attacker);
        double share = data.capacity() <= 0 ? 0 : Mth.clamp(data.current() / data.capacity(), 0, 1);
        return Mth.lerp(share, FistsConfig.MALICE_EMPTY.get(), FistsConfig.MALICE_FULL.get());
    }

    /** Your opponent's Malice against yours: stronger is narrower. Mobs and Severed players have none. */
    static double opponent(ServerPlayer attacker, LivingEntity target) {
        double yours = Math.max(1, MaliceService.get(attacker).capacity());
        double theirs = 0;
        if (target instanceof ServerPlayer p && MaliceService.get(p).isBorn() && !MaliceService.isSevered(p)) theirs = MaliceService.get(p).capacity();
        double factor = 1.0 / (1.0 + FistsConfig.OPPONENT_WEIGHT.get() * (theirs / yours - 1.0));
        return Mth.clamp(factor, FistsConfig.OPPONENT_MIN.get(), FistsConfig.OPPONENT_MAX.get());
    }

    /** Your physical condition: health, hunger, and Weakness, Slowness and Freeze stacks narrow it. */
    static double body(ServerPlayer attacker) {
        double health = Mth.clamp(attacker.getHealth() / Math.max(1f, attacker.getMaxHealth()), 0, 1);
        double food = Mth.clamp(attacker.getFoodData().getFoodLevel() / 20.0, 0, 1);
        double out = Mth.lerp(health, FistsConfig.HEALTH_LOW.get(), 1.0) * Mth.lerp(food, FistsConfig.HUNGER_LOW.get(), 1.0);
        out *= effect(attacker, MobEffects.WEAKNESS, FistsConfig.WEAKNESS.get());
        out *= effect(attacker, MobEffects.MOVEMENT_SLOWDOWN, FistsConfig.SLOWNESS.get());
        out *= Math.max(0.5, 1.0 - FistsConfig.FREEZE_PER_STACK.get() * FreezeService.stacks(attacker));
        return out;
    }

    private static double effect(LivingEntity entity, Holder<net.minecraft.world.effect.MobEffect> effect, double perLevel) {
        MobEffectInstance on = entity.getEffect(effect);
        return on == null ? 1.0 : Math.pow(perLevel, on.getAmplifier() + 1);
    }

    /**
     * Temperature and humidity where you stand: the further the biome's temperature is from mild, the wetter the biome, and any rain or
     * storm there, the narrower. Canon names these, not which way they push: the direction is an assumption, and every nudge is small.
     */
    static double weather(Level level, BlockPos at) {
        Biome biome = level.getBiome(at).value();
        boolean rain = level.isRainingAt(at.above());
        return weather(biome.getBaseTemperature(), biome.getModifiedClimateSettings().downfall(), rain, rain && level.isThundering());
    }

    /** The same, from the raw numbers: the biome's temperature and rainfall, and whether it's raining or storming there. */
    static double weather(double temperature, double downfall, boolean raining, boolean thundering) {
        double away = Math.min(1.0, Math.abs(temperature - FistsConfig.MILD_TEMPERATURE.get()));
        double out = (1.0 - FistsConfig.TEMPERATURE_WEIGHT.get() * away) * (1.0 - FistsConfig.HUMIDITY_WEIGHT.get() * Mth.clamp(downfall, 0, 1));
        if (raining) out *= FistsConfig.RAIN.get();
        if (thundering) out *= FistsConfig.THUNDER.get();
        return out;
    }

    /** Your Grade: higher is wider. */
    static double grade(ServerPlayer attacker) {
        return 1.0 + FistsConfig.GRADE_STEP.get() * GradeService.grade(attacker).ordinal();
    }
}
