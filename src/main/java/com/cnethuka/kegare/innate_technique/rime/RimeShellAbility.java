package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastManager;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * TOGGLE: a shell of ice. While on: damage reduction, Malice upkeep, and melee attackers get Slowness
 * (see RimeEvents). Like every toggle it keeps running while you are stunned or frozen.
 */
public final class RimeShellAbility extends RimeAbility {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .toggleOn(RimeIds.ANIM_RIME_SHELL_TOGGLE, null)
            .toggleLoop(null, RimeIds.FX_RIME_SHELL_AURA)
            .toggleOff(RimeIds.ANIM_RIME_SHELL_TOGGLE, null)
            .build();

    RimeShellAbility() {
        super("rime_shell", CastType.TOGGLE);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.SHELL_COST.get() * environment(p); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return RimeConfig.SHELL_UPKEEP.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.SHELL_COOLDOWN); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public Optional<Ability> special() { return Optional.of(Rime.SHATTER_SHELL); }

    public boolean isActive(ServerPlayer player) {
        return CastManager.state(player).toggles().containsKey(id());
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        onCast(p);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        ServerPlayer p = ctx.player();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.1f);
    }
}
