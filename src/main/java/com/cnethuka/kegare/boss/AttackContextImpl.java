package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.api.boss.BossAttackContext;
import com.cnethuka.kegare.api.boss.BossAttackOrigin;
import com.cnethuka.kegare.api.boss.BossAttackState;
import com.cnethuka.kegare.api.boss.BossFight;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

record AttackContextImpl(
        ServerLevel level,
        LivingEntity boss,
        @Nullable LivingEntity target,
        BossFight encounter,
        ResourceLocation attackId,
        UUID attackInstanceId,
        long gameTime,
        long attackTicks,
        long stateTicks,
        BossAttackState state) implements BossAttackContext {
    @Override
    public <T extends Entity> T markOrigin(T entity) {
        entity.setData(KegareAttachments.BOSS_ATTACK_ORIGIN,
                new BossAttackOrigin(encounter.id(), boss.getUUID(), attackInstanceId, attackId));
        return entity;
    }
}
