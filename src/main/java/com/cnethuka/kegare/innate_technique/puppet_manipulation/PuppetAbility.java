package com.cnethuka.kegare.innate_technique.puppet_manipulation;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.BaseAbility;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.SpecialAbility;
import java.util.function.Supplier;

/** Shared base of Puppet Manipulation's abilities. */
abstract class PuppetAbility extends BaseAbility {
    PuppetAbility(String name, CastType castType) {
        super(Kegare.id(name), castType);
    }

    /** A Puppet Manipulation special (R): shares its base ability's cooldown. */
    abstract static class Special extends SpecialAbility {
        Special(String name, CastType castType, Supplier<? extends Ability> base) {
            super(Kegare.id(name), castType, base);
        }
    }
}
