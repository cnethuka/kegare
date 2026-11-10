package com.cnethuka.kegare.innatetechnique;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base for an ability's special (cast by holding R and that ability's number key). It is a full {@link Ability} with its
 * own cast type, cost, effect and visuals, but it shares its base ability's cooldown: casting either one starts the
 * cooldown for both (see {@link Ability#cooldownKey()}). The cooldown length defaults to the base ability's.
 * <p>
 * The base ability returns this from {@link Ability#special()}; this points back with a supplier, so the two can be
 * constructed in either order.
 */
public abstract class SpecialAbility extends BaseAbility {
    private final Supplier<? extends Ability> base;
    private final List<Requirement> requirements;

    protected SpecialAbility(ResourceLocation id, CastType castType, Supplier<? extends Ability> base, Requirement... requirements) {
        super(id, castType);
        this.base = base;
        this.requirements = List.of(requirements);
    }

    public final Ability baseAbility() {
        return base.get();
    }

    @Override
    public final Optional<Ability> base() {
        return Optional.of(base.get());
    }

    /** Defaults to the base ability's cooldown, since the two share it. */
    @Override
    public int cooldownTicks(ServerPlayer player) {
        return base.get().cooldownTicks(player);
    }

    @Override
    public List<Requirement> requirements() {
        return requirements;
    }
}
