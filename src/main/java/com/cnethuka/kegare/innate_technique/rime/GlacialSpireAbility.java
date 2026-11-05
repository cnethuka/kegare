package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Special of Glacial Lance (CHARGED): aim at the ground in range and release. A frost circle marks the spot, then an
 * ice spike erupts there ({@link GlacialSpireEntity}): damage, freeze stacks, and anyone on it is launched upward
 * (higher with more charge). No ground where you aim at release: fizzles for free.
 */
public final class GlacialSpireAbility extends RimeSpecial {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .chargeLoop(RimeIds.ANIM_GLACIAL_SPIRE_CHARGE, RimeIds.FX_GLACIAL_SPIRE_CHARGE)
            .release(RimeIds.ANIM_GLACIAL_SPIRE_RELEASE, null)
            .impact(RimeIds.FX_GLACIAL_SPIRE_ERUPT)
            .build();
    /** Aiming at a wall: look this far down from where the ray hit it for the floor in front of it. */
    private static final double MAX_DROP = 4.0;

    GlacialSpireAbility() {
        super("glacial_spire", CastType.CHARGED, () -> Rime.GLACIAL_LANCE);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.SPIRE_COST.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.SPIRE_COOLDOWN); }
    @Override public int minChargeTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.SPIRE_MIN_CHARGE); }
    @Override public int maxChargeTicks(ServerPlayer p) { return Math.max(1, RimeConfig.ticks(RimeConfig.SPIRE_MAX_CHARGE)); }
    @Override public AbilityVisuals visuals() { return VISUALS; }

    @Override
    public boolean canRelease(AbilityContext ctx) {
        return target(ctx.player()).isPresent();
    }

    @Override
    public Component blockedMessage(ServerPlayer player) {
        return Component.translatable("message.kegare.glacial_spire.no_ground", displayName(),
                String.format(Locale.ROOT, "%.0f", RimeConfig.SPIRE_RANGE.get()));
    }

    @Override
    public void tick(AbilityContext ctx) {
        if (ctx.activeTicks() == 1) {
            ServerPlayer p = ctx.player();
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1f, 0.5f);
        }
    }

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        Optional<Vec3> at = target(player);
        if (at.isEmpty()) return; // canRelease just checked; the world can't change in between
        GlacialSpireEntity spire = new GlacialSpireEntity(player.level(), player, at.get(), ctx.charge());
        player.level().addFreshEntity(spire);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.6f);
        onCast(player);
    }

    /**
     * Where the spike would rise: the top of the block you look at within range. Aiming at a wall or ceiling, the
     * floor just in front of it. Empty if there is no ground. Pure world query, so the client uses it for the
     * caster's aiming preview.
     */
    public static Optional<Vec3> target(Player player) {
        double range = RimeConfig.SPIRE_RANGE.get();
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return Optional.empty();
        if (hit.getDirection() == Direction.UP) return Optional.of(hit.getLocation());
        Vec3 inFront = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.3));
        BlockHitResult floor = level.clip(new ClipContext(inFront, inFront.subtract(0, MAX_DROP, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (floor.getType() == HitResult.Type.MISS || floor.getDirection() != Direction.UP) return Optional.empty();
        return Optional.of(floor.getLocation());
    }
}
