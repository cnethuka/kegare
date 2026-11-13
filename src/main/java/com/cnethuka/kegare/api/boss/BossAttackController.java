package com.cnethuka.kegare.api.boss;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** The encounter's server-side runner. AI, target selection and attack choice stay in the addon. */
public interface BossAttackController {
    BossAttackStartResult start(BossAttack attack, @Nullable LivingEntity target);

    /** Driven by the framework's server tick. Repeated calls for the same game tick are ignored. */
    void tick();

    /** Cancels the running attack, applies its cooldown and runs its end cleanup. */
    boolean cancel();

    Optional<ResourceLocation> activeAttack();

    Optional<UUID> activeAttackInstance();

    int cooldownTicksRemaining(ResourceLocation attackId);
}
