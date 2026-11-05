package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.network.PlayerSync;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Freeze stacks on any living target. Stacks melt one at a time after a while. At the max the target is frozen
 * solid: a stun through the existing stun system (so it interrupts charges and channels), movement locked, then
 * the stacks reset and it can't gain stacks for a short while after breaking free.
 */
public final class FreezeService {
    private static final ResourceLocation LOCK = Kegare.id("frozen");

    private FreezeService() {}

    public static FreezeData get(LivingEntity entity) {
        return entity.getData(RimeRegistry.FREEZE);
    }

    public static int stacks(LivingEntity entity) {
        return entity.hasData(RimeRegistry.FREEZE) ? get(entity).stacks : 0;
    }

    public static boolean isFrozen(LivingEntity entity) {
        return entity.hasData(RimeRegistry.FREEZE) && get(entity).frozen;
    }

    public static long frozenUntil(LivingEntity entity) {
        return entity.hasData(RimeRegistry.FREEZE) && get(entity).frozen ? get(entity).frozenUntil : 0L;
    }

    /** Adds stacks (ignored while frozen or briefly after breaking free). Freezes at the max. */
    public static void addStacks(LivingEntity target, int count, @Nullable ServerPlayer attacker) {
        if (count <= 0 || !target.isAlive() || target.level().isClientSide) return;
        FreezeData data = get(target);
        long now = target.level().getGameTime();
        if (data.frozen || data.immuneUntil > now) return;
        int max = RimeConfig.FREEZE_MAX_STACKS.get();
        data.stacks = Math.min(max, data.stacks + count);
        data.lastChange = now;
        if (target instanceof ServerPlayer player) PlayerSync.markDirty(player);
        if (data.stacks >= max) freeze(target, attacker);
    }

    private static void freeze(LivingEntity target, @Nullable ServerPlayer attacker) {
        FreezeData data = get(target);
        long now = target.level().getGameTime();
        int ticks = Math.max(1, RimeConfig.ticks(RimeConfig.FREEZE_DURATION));
        // Charging or channeling right now? (checked before the stun interrupts it)
        boolean wasCasting = target instanceof ServerPlayer p && CastManager.state(p).casting() != null;

        data.stacks = 0;
        data.lastChange = now;
        data.frozen = true;
        data.frozenUntil = now + ticks;
        data.frozenBy = attacker != null ? attacker.getUUID() : null;
        CombatHelper.stun(target, ticks); // existing stun system: interrupts charges and channels
        lock(target, true);
        // Vanilla's frost overlay and shiver, kept below "fully frozen" so vanilla never adds freeze damage.
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() - 1));
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1f, 0.7f);
        VisualService.startLoop(target, RimeIds.FROZEN_LOOP_KEY, RimeIds.FROZEN, 0);
        if (target instanceof ServerPlayer player) PlayerSync.markDirty(player);

        if (attacker != null && target instanceof ServerPlayer victim && victim != attacker) {
            RimeAdvancements.award(attacker, RimeAdvancements.SHATTER);
            if (wasCasting) RimeAdvancements.award(attacker, RimeAdvancements.BRAIN_FREEZE);
            if (victim.level().dimension() == Level.NETHER) RimeAdvancements.award(attacker, RimeAdvancements.LONG_WAY_FROM_HOME);
            int recent = attacker.getData(RimeRegistry.TRACKER).recordFreeze(victim.getUUID(), now, RimeAdvancements.DEEP_FREEZE_WINDOW_TICKS);
            if (recent >= RimeAdvancements.DEEP_FREEZE_COUNT) RimeAdvancements.award(attacker, RimeAdvancements.DEEP_FREEZE);
        }
    }

    /** Every tick for entities with freeze data: break free when the time is up, otherwise melt stacks. */
    static void tick(LivingEntity entity) {
        FreezeData data = get(entity);
        long now = entity.level().getGameTime();
        if (data.frozen) {
            if (now >= data.frozenUntil) thaw(entity, data, now);
            return;
        }
        if (data.stacks > 0 && now - data.lastChange >= Math.max(1, RimeConfig.ticks(RimeConfig.FREEZE_DECAY_SECONDS))) {
            data.stacks--;
            data.lastChange = now;
            if (entity instanceof ServerPlayer player) PlayerSync.markDirty(player);
        }
    }

    private static void thaw(LivingEntity entity, FreezeData data, long now) {
        data.frozen = false;
        data.frozenUntil = 0;
        data.frozenBy = null;
        data.immuneUntil = now + RimeConfig.ticks(RimeConfig.FREEZE_IMMUNITY_SECONDS);
        lock(entity, false);
        VisualService.stopLoop(entity, RimeIds.FROZEN_LOOP_KEY);
        if (entity.level() instanceof ServerLevel level && entity.isAlive()) {
            VisualService.playAt(level, entity.position().add(0, entity.getBbHeight() / 2.0, 0), RimeIds.SHATTER);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 1.2f);
        }
        if (entity instanceof ServerPlayer player) PlayerSync.markDirty(player);
    }

    /** Clears everything (death): no shatter, no immunity. */
    public static void clear(LivingEntity entity) {
        if (!entity.hasData(RimeRegistry.FREEZE)) return;
        FreezeData data = get(entity);
        data.stacks = 0;
        data.frozen = false;
        data.frozenUntil = 0;
        data.frozenBy = null;
        lock(entity, false);
    }

    /** Frozen solid can't walk or jump: transient modifiers (never saved). */
    private static void lock(LivingEntity entity, boolean on) {
        lock(entity, Attributes.MOVEMENT_SPEED, on);
        lock(entity, Attributes.JUMP_STRENGTH, on);
    }

    private static void lock(LivingEntity entity, Holder<Attribute> attribute, boolean on) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(LOCK);
        if (on) instance.addTransientModifier(new AttributeModifier(LOCK, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
