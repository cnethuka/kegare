package com.cnethuka.kegare.rmt.client;

import com.cnethuka.kegare.client.visual.ClientVisuals;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.rmt.OrbPath;
import com.cnethuka.kegare.rmt.OrbPayload;
import com.cnethuka.kegare.rmt.RmtIds;
import java.util.Arrays;
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
 * The green orb on its finder's client (nobody else is ever sent it). Draws it where the server says it has got to on the route
 * the server rolled, with dots marking the way ahead, swells it as they get further, and plays the burst and sound, locally, when
 * the server says it was taken in. Only ever shows: the server decides everything.
 */
public final class GreenOrbClient {
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.45f, 1f, 0.55f), 1.2f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1f, 1f, 1f), 0.5f);
    private static final int DOTS_PER_LEG = 4;

    @Nullable
    private static OrbPath path;
    private static int total;
    private static int progress;
    @Nullable
    private static ClientVisuals.PositionalEffect effect;
    /** The way ahead: one per dot, null once the orb has passed it. */
    private static ClientVisuals.PositionalEffect[] dots = new ClientVisuals.PositionalEffect[0];
    private static boolean usingPhoton;

    private GreenOrbClient() {}

    public static void handle(OrbPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (payload.action()) {
            case SHOW -> {
                hide(true);
                path = new OrbPath(payload.path());
                total = Math.max(1, payload.value());
                progress = 0;
                Vec3 start = path.at(0);
                effect = ClientVisuals.startAt(RmtIds.FX_ORB, start);
                usingPhoton = effect != null;
                dots = new ClientVisuals.PositionalEffect[(path.points().size() - 1) * DOTS_PER_LEG];
                level.playLocalSound(start.x, start.y, start.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.6f, false);
            }
            case PROGRESS -> progress = payload.value();
            case HIDE -> hide(false);
            case TAKEN -> {
                Vec3 at = payload.path().get(0);
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
        if (path == null || mc.level == null || mc.player == null) return;
        double fraction = progress / (double) total;
        Vec3 pos = path.at(fraction);
        if (effect != null) {
            effect.moveTo(pos);
            effect.scale(1f + 0.45f * (float) fraction);
        } else if (!usingPhoton && mc.level.getGameTime() % 2 == 0) { // vanilla stand-in
            mc.level.addParticle(GREEN, pos.x, pos.y, pos.z, 0, 0, 0);
            mc.level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.x + (mc.level.random.nextDouble() - 0.5) * 0.4,
                    pos.y + (mc.level.random.nextDouble() - 0.5) * 0.4, pos.z + (mc.level.random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
        tickDots(mc.level, fraction);
    }

    /** The dots ahead of the orb are lit; it puts them out as it passes, and they come back if it runs back. */
    private static void tickDots(ClientLevel level, double fraction) {
        boolean vanillaBeat = !usingPhoton && level.getGameTime() % 4 == 0;
        for (int i = 0; i < dots.length; i++) {
            double at = (i + 1) / (double) dots.length;
            boolean ahead = at > fraction;
            if (usingPhoton) {
                if (ahead && dots[i] == null) {
                    dots[i] = ClientVisuals.startAt(RmtIds.FX_TRAIL_DOT, path.at(at));
                } else if (!ahead && dots[i] != null) {
                    dots[i].stop(false);
                    dots[i] = null;
                }
            } else if (ahead && vanillaBeat) {
                Vec3 pos = path.at(at);
                level.addParticle(WHITE, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
    }

    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        path = null;
        effect = null;
        dots = new ClientVisuals.PositionalEffect[0];
    }

    private static void hide(boolean immediately) {
        if (effect != null) effect.stop(immediately);
        effect = null;
        for (ClientVisuals.PositionalEffect dot : dots) {
            if (dot != null) dot.stop(immediately);
        }
        Arrays.fill(dots, null);
        path = null;
    }
}
