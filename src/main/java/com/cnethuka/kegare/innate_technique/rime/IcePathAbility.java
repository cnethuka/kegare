package com.cnethuka.kegare.innate_technique.rime;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.innatetechnique.AbilityContext;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.EndReason;
import com.cnethuka.kegare.innatetechnique.visual.AbilityVisuals;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Special of Frost Field (CHANNELED): while R is held you slide fast the way you look, over a temporary ice trail
 * ({@link IcePaths}) that slows anyone else on it until it melts. No blocks are placed. Drains Malice per second.
 * Distance slid counts toward Slip 'n Slide.
 */
public final class IcePathAbility extends RimeSpecial {
    private static final AbilityVisuals VISUALS = AbilityVisuals.builder()
            .channelLoop(RimeIds.ANIM_ICE_PATH_SLIDE, null)
            .build();
    /** Lets the slide ride up one-block steps instead of stopping dead. */
    private static final ResourceLocation STEP_BOOST = Kegare.id("ice_path_step");

    IcePathAbility() {
        super("ice_path", CastType.CHANNELED, () -> Rime.FROST_FIELD);
    }

    @Override public double cost(ServerPlayer p) { return RimeConfig.PATH_COST.get() * environment(p); }
    @Override public double upkeepPerSecond(ServerPlayer p) { return RimeConfig.PATH_UPKEEP.get() * environment(p); }
    @Override public int cooldownTicks(ServerPlayer p) { return RimeConfig.ticks(RimeConfig.PATH_COOLDOWN); }
    @Override public int maxChannelTicks(ServerPlayer p) { return Math.max(1, RimeConfig.ticks(RimeConfig.PATH_MAX_DURATION)); }
    @Override public AbilityVisuals visuals() { return VISUALS; }
    @Override public boolean hasNoTarget() { return true; } // a slide: it moves only you

    @Override
    public void execute(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        AttributeInstance step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null && !step.hasModifier(STEP_BOOST)) {
            step.addTransientModifier(new AttributeModifier(STEP_BOOST, 0.5, AttributeModifier.Operation.ADD_VALUE));
        }
        VisualService.startLoop(p, RimeIds.ICE_PATH_TRAIL_KEY, RimeIds.ICE_PATH_TRAIL, 0, RimeConfig.ticks(RimeConfig.PATH_TRAIL_SECONDS));
        p.getData(RimeRegistry.TRACKER).icePathLast = p.position();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
        onCast(p);
    }

    @Override
    public void tick(AbilityContext ctx) {
        ServerPlayer p = ctx.player();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 1.0e-4) {
            Vec3 dir = flat.normalize().scale(RimeConfig.PATH_SPEED.get());
            p.setDeltaMovement(dir.x, p.getDeltaMovement().y, dir.z);
            p.hurtMarked = true; // the client owns its movement: push the slide to it every tick
        }
        p.resetFallDistance();
        IcePaths.lay(p);

        // Slip 'n Slide: horizontal distance slid, kept across sessions.
        RimeTracker tracker = p.getData(RimeRegistry.TRACKER);
        Vec3 now = p.position();
        if (tracker.icePathLast != null) {
            double dx = now.x - tracker.icePathLast.x;
            double dz = now.z - tracker.icePathLast.z;
            double moved = Math.sqrt(dx * dx + dz * dz);
            if (moved < 4.0) { // ignore teleports
                RimeStats stats = p.getData(RimeRegistry.STATS);
                stats.icePathDistance += moved;
                if (stats.icePathDistance >= RimeAdvancements.SLIP_N_SLIDE_BLOCKS) RimeAdvancements.award(p, RimeAdvancements.SLIP_N_SLIDE);
            }
        }
        tracker.icePathLast = now;
        if (ctx.activeTicks() % 6 == 0) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_STEP, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    @Override
    public void onEnd(AbilityContext ctx, EndReason reason) {
        ServerPlayer p = ctx.player();
        AttributeInstance step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null) step.removeModifier(STEP_BOOST);
        VisualService.stopLoop(p, RimeIds.ICE_PATH_TRAIL_KEY); // what's laid stays and melts on its own
        p.getData(RimeRegistry.TRACKER).icePathLast = null;
    }
}
