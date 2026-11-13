package com.cnethuka.kegare.boss;

import com.cnethuka.kegare.api.boss.BossAttackOrigin;
import com.cnethuka.kegare.progression.Attribution;
import com.cnethuka.kegare.progression.CastOrigin;
import com.cnethuka.kegare.registry.KegareAttachments;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class BossAttribution {
    private BossAttribution() {}

    record Outgoing(BossFightImpl fight, @Nullable ResourceLocation attackId, @Nullable UUID attackInstanceId) {}

    @Nullable
    static ResourceLocation abilityId(@Nullable Attribution.Hit hit, DamageSource source) {
        if (hit == null) return null;
        ResourceLocation tagged = tagged(hit.attacker(), source.getDirectEntity());
        if (tagged == null) tagged = tagged(hit.attacker(), source.getEntity());
        return tagged != null ? tagged : hit.ability() == null ? null : hit.ability().id();
    }

    @Nullable
    private static ResourceLocation tagged(ServerPlayer attacker, @Nullable Entity entity) {
        if (entity == null || !entity.hasData(KegareAttachments.CAST_ORIGIN)) return null;
        CastOrigin origin = entity.getData(KegareAttachments.CAST_ORIGIN);
        return origin.caster().equals(attacker.getUUID()) ? origin.ability() : null;
    }

    static ResourceLocation damageType(ServerLevel level, DamageSource source) {
        ResourceLocation id = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getKey(source.type());
        return id != null ? id : ResourceLocation.withDefaultNamespace("unknown");
    }

    static ResourceLocation entityType(Entity entity) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    @Nullable
    static Outgoing outgoing(LivingEntity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel level)) return null;
        Outgoing tagged = fromOrigin(source.getDirectEntity(), level);
        if (tagged == null) tagged = fromOrigin(source.getEntity(), level);
        if (tagged != null) return tagged;
        Outgoing direct = fromBoss(source.getDirectEntity(), level);
        if (direct == null) direct = fromBoss(source.getEntity(), level);
        return direct;
    }

    @Nullable
    private static Outgoing fromOrigin(@Nullable Entity entity, ServerLevel level) {
        if (entity == null || !entity.hasData(KegareAttachments.BOSS_ATTACK_ORIGIN)) return null;
        BossAttackOrigin origin = entity.getData(KegareAttachments.BOSS_ATTACK_ORIGIN);
        Optional<BossFightImpl> found = BossRuntime.findEncounter(origin.encounterId());
        if (found.isEmpty()) return null;
        BossFightImpl fight = found.get();
        if (fight.level().getServer() != level.getServer() || !fight.bossId().equals(origin.bossId())) return null;
        return new Outgoing(fight, origin.attackId(), origin.attackInstanceId());
    }

    @Nullable
    private static Outgoing fromBoss(@Nullable Entity entity, ServerLevel level) {
        if (entity == null) return null;
        Optional<BossFightImpl> found = BossRuntime.find(entity.getUUID());
        if (found.isEmpty()) return null;
        BossFightImpl fight = found.get();
        if (fight.level().getServer() != level.getServer() || fight.boss() != entity) return null;
        BossAttackRunner.DirectAttack attack = fight.runner().directAttack();
        return attack == null ? new Outgoing(fight, null, null)
                : new Outgoing(fight, attack.attackId(), attack.attackInstanceId());
    }
}
