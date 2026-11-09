package com.cnethuka.kegare.sixeyes;

import com.cnethuka.kegare.registry.KegareItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * What covers the eyes (DESIGN §3.32): a blindfold or sunglasses in Curios' "eyes" slot. Anyone can wear them and they mean nothing on
 * someone without the eyes, so wearing one proves nothing. On a bearer they change only how fast Strain builds and falls, and whether the
 * uncovered edge is there: never what the eyes see, nor how little their energy wastes.
 */
public enum Eyewear {
    NONE, SUNGLASSES, BLINDFOLD;

    public static final String SLOT = "eyes";

    public boolean covered() {
        return this != NONE;
    }

    public static Eyewear of(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.getStacksHandler(SLOT)).map(handler -> {
            Eyewear best = NONE;
            for (int i = 0; i < handler.getSlots(); i++) {
                Eyewear e = of(handler.getStacks().getStackInSlot(i));
                if (e.ordinal() > best.ordinal()) best = e;
            }
            return best;
        }).orElse(NONE);
    }

    public static Eyewear of(ItemStack stack) {
        if (stack.is(KegareItems.BLINDFOLD.get())) return BLINDFOLD;
        if (stack.is(KegareItems.SUNGLASSES.get())) return SUNGLASSES;
        return NONE;
    }
}
