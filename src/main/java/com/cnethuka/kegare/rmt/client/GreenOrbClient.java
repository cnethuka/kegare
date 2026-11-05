package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.rmt.OrbPayload;
import com.cnethuka.kegare.rmt.RmtConfig;
import com.cnethuka.kegare.rmt.RmtIds;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * The green orb on its finder's client (nobody else is ever sent it). Shows it where the server put it, makes it
 * swell while it is being looked at (feedback only: the server decides), fades it out on time, and plays the burst and
 * sound, locally, when the server says it was taken in.
 */
public final class GreenOrbClient {
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.45f, 1f, 0.55f), 1.2f);

    @Nullable
    private static Vec3 pos;
    private static long shownAt;
    private static int lifetime;
    @Nullable
    private static ClientVisuals.PositionalEffect effect;
    private static float looked;

    private GreenOrbClient() {}

    public static void handle(OrbPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (payload.action()) {
            case SHOW -> {
                hide(true);
                pos = payload.pos();
                shownAt = level.getGameTime();
                lifetime = payload.lifetimeTicks();
                looked = 0;
                effect = ClientVisuals.startAt(RmtIds.FX_ORB, pos);
                level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f, false);
            }
            case HIDE -> hide(false);
            case TAKEN -> {
                Vec3 at = payload.pos();
                hide(true);
                ClientVisuals.playAt(level, at, Visual.effect(RmtIds.FX_UNLOCK_BURST, Visual.Anchor.BODY));
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) ClientVisuals.playOnce(player, RmtIds.HEAL_PULSE);
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.7f, false);
                level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1f, 1.4f, false);
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null || mc.player == null) return;
        long age = mc.level.getGameTime() - shownAt;
        if (age >= lifetime) {
            hide(false); // fades out
            return;
        }
        // Swells while looked at (the server keeps the real count).
        double angle = 10;
        int lookTicks = 20;
        try {
            angle = RmtConfig.ORB_LOOK_ANGLE.get();
            lookTicks = Math.max(1, RmtConfig.ticks(RmtConfig.ORB_LOOK_SECONDS));
        } catch (IllegalStateException notLoaded) {
            // keep the defaults
        }
        Vec3 to = pos.subtract(mc.player.getEyePosition());
        boolean looking = to.lengthSqr() > 1.0e-6 && mc.player.getLookAngle().dot(to.normalize()) >= Math.cos(Math.toRadians(angle));
        looked = looking ? Math.min(1f, looked + 1f / lookTicks) : Math.max(0f, looked - 0.15f);
        if (effect != null) {
            effect.scale(1f + 0.45f * looked);
        } else if (age % 2 == 0) { // vanilla stand-in
            mc.level.addParticle(GREEN, pos.x, pos.y, pos.z, 0, 0, 0);
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.x + (mc.level.random.nextDouble() - 0.5) * 0.5,
                    pos.y + (mc.level.random.nextDouble() - 0.5) * 0.5, pos.z + (mc.level.random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pos = null;
        effect = null;
    }

    private static void hide(boolean immediately) {
        if (effect != null) effect.stop(immediately);
        effect = null;
        pos = null;
    }
}
