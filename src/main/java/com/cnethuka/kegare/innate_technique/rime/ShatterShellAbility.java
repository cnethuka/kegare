package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.combat.CombatHelper;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualPhase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Special of Rime Shell (INSTANT): burst outward. Everyone nearby is knocked back and gets freeze stacks. An active
 * Rime Shell is used up by it (cheap); with no shell up it costs more. Either way the shared cooldown starts.
 */
public final class ShatterShellAbility extends RimeSpecial {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .cast(RimeIds.ANIM_SHATTER_SHELL_CAST, RimeIds.FX_SHATTER_SHELL_BURST)
            .anchor(VisualPhase.CAST, Visual.Anchor.BODY)
            .build();

    ShatterShellAbility() {
        super("shatter_shell", CastType.INSTANT, () -> Rime.RIME_SHELL);
    }

    /** Depends on whether the shell is up right now (the HUD and Sorcerer screen show the current one). */
    @Override
    public double cost(ServerPlayer p) {
        double base = Rime.RIME_SHELL.isActive(p) ? RimeConfig.SHATTER_COST.get() : RimeConfig.SHATTER_COST_NO_SHELL.get();
        return base * environment(p);
    }

    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.SHATTER_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        CastManager.switchOff(p, Rime.RIME_SHELL, EndReason.CONSUMED); // the shell is what bursts

        double radius = RimeConfig.SHATTER_RADIUS.get();
        double knockback = RimeConfig.SHATTER_KNOCKBACK.get();
        int stacks = RimeConfig.SHATTER_FREEZE_STACKS.get();
        int players = 0;
        com.cnethuka.kegare.melee.CleanDodge.areaWentOff(p.serverLevel(), p, com.cnethuka.kegare.melee.CleanDodge.sphere(p.position(), radius));
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> e != p && e.isAlive() && !e.isSpectator() && e.distanceToSqr(p) <= radius * radius)) {
            if (CombatHelper.isBoss(target)) continue; // never knocked back, never frozen (DESIGN §4)
            double dx = p.getX() - target.getX();
            double dz = p.getZ() - target.getZ();
            if (dx * dx + dz * dz < 1.0e-4) {
                dx = p.getRandom().nextDouble() - 0.5;
                dz = p.getRandom().nextDouble() - 0.5;
            }
            target.knockback(knockback, dx, dz);
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.15, 0));
            target.hurtMarked = true;
            FreezeService.addStacks(target, stacks, p);
            if (target instanceof ServerPlayer) players++;
        }
        if (players >= RimeAdvancements.SHATTERED_PRESSURE_PLAYERS) RimeAdvancements.award(p, RimeAdvancements.SHATTERED_PRESSURE);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
        onCast(p);
    }
}
