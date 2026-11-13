package com.cnethuka.kegare.api.boss;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/** Posted after one damage event has been accounted. Read-only and not cancellable. */
public final class BossDamageEvent extends Event {
    public enum Direction { INCOMING, OUTGOING }

    private final BossFightSnapshot encounter;
    private final Direction direction;
    private final UUID targetId;
    private final ResourceLocation targetTypeId;
    private final UUID participantId;
    private final ResourceLocation abilityId;
    private final ResourceLocation damageTypeId;
    private final ResourceLocation attackId;
    private final UUID attackInstanceId;
    private final BossDamageSnapshot damage;
    private final long tick;

    public BossDamageEvent(
            BossFightSnapshot encounter,
            Direction direction,
            UUID targetId,
            ResourceLocation targetTypeId,
            @Nullable UUID participantId,
            @Nullable ResourceLocation abilityId,
            ResourceLocation damageTypeId,
            @Nullable ResourceLocation attackId,
            @Nullable UUID attackInstanceId,
            BossDamageSnapshot damage,
            long tick) {
        this.encounter = encounter;
        this.direction = direction;
        this.targetId = targetId;
        this.targetTypeId = targetTypeId;
        this.participantId = participantId;
        this.abilityId = abilityId;
        this.damageTypeId = damageTypeId;
        this.attackId = attackId;
        this.attackInstanceId = attackInstanceId;
        this.damage = damage;
        this.tick = tick;
    }

    public BossFightSnapshot getEncounter() { return encounter; }
    public Direction getDirection() { return direction; }
    public UUID getTargetId() { return targetId; }
    public ResourceLocation getTargetTypeId() { return targetTypeId; }
    @Nullable public UUID getParticipantId() { return participantId; }
    @Nullable public ResourceLocation getAbilityId() { return abilityId; }
    public ResourceLocation getDamageTypeId() { return damageTypeId; }
    @Nullable public ResourceLocation getAttackId() { return attackId; }
    @Nullable public UUID getAttackInstanceId() { return attackInstanceId; }
    public BossDamageSnapshot getDamage() { return damage; }
    public long getTick() { return tick; }
}
