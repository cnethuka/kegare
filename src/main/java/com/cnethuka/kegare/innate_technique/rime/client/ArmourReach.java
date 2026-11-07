package com.cnethuka.kegare.innate_technique.rime.client;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;

/**
 * How far out from each part of the body the armour a player wears stands (pixels: the armour model's own inflation over the part's
 * box), 0 where there's none, so what Rime puts on a body (Freeze's frost, Rime Shell's plates) lies over it, never under. As
 * vanilla draws it: anything worn on the head out to 1.5 (a helmet's hat layer), a chestplate the body and arms out to 1, leggings
 * the body and legs out to 0.5, boots the legs out to 1. A chestplate's arms are wide ones, slim skin or not.
 */
final class ArmourReach {
    /** The parts, in the order {@link #of} gives them. */
    static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5;

    private ArmourReach() {}

    static float[] of(LivingEntity entity) {
        return of(!entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), worn(entity, EquipmentSlot.CHEST),
                worn(entity, EquipmentSlot.LEGS), worn(entity, EquipmentSlot.FEET));
    }

    static float[] of(boolean head, boolean chest, boolean legs, boolean feet) {
        float body = chest ? 1f : legs ? 0.5f : 0f;
        float arms = chest ? 1f : 0f;
        float leg = feet ? 1f : legs ? 0.5f : 0f;
        return new float[] {head ? 1.5f : 0f, body, arms, arms, leg, leg};
    }

    /** Armour for that slot in it, which the armour layer draws (an elytra or a carried item in the chest slot isn't). */
    private static boolean worn(LivingEntity entity, EquipmentSlot slot) {
        return entity.getItemBySlot(slot).getItem() instanceof ArmorItem armour && armour.getEquipmentSlot() == slot;
    }
}
