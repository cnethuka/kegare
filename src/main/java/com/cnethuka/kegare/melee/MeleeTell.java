package com.cnethuka.kegare.melee;

import com.cnethuka.kegare.innate_technique.swap.Disguises;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueService;
import com.cnethuka.kegare.innatetechnique.visual.Visual;
import com.cnethuka.kegare.innatetechnique.visual.VisualService;
import com.cnethuka.kegare.malice.MaliceService;
import com.cnethuka.kegare.progression.Attribution;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a ready Reinforced Strike looks like on a player: Malice in their Innate Technique's color round the attacking hand, or for a
 * Severed player, with no Malice to show, a physical tell. The color is read from the technique, so a new one gets it by itself.
 * <p>
 * <b>A disguise can't be given away by it.</b> Everyone but the wearer is sent the look of whoever the wearer looks like, and that's
 * all their client ever gets: there's nothing in the packet for a modified client to read the real color from. The wearer sees their own.
 */
public final class MeleeTell {
    public enum Kind { NONE, GLOW, PHYSICAL }

    /** @param color RGB, as the technique defines it; {@link Kind#GLOW} only */
    public record Look(Kind kind, int color) {
        public static final Look NONE = new Look(Kind.NONE, 0);
        public static final Look PHYSICAL = new Look(Kind.PHYSICAL, 0);

        public static Look glow(int rgb) {
            return new Look(Kind.GLOW, rgb & 0xFFFFFF);
        }
    }

    private MeleeTell() {}

    /** How a player's own Malice looks: their technique's color, or the physical tell if they're Severed. */
    public static Look of(ServerPlayer player) {
        if (MaliceService.isSevered(player)) return Look.PHYSICAL;
        return Look.glow(InnateTechniqueService.current(player).map(InnateTechnique::maliceColor).orElse(InnateTechnique.DEFAULT_MALICE_COLOR));
    }

    /** What everyone else sees on them: under a disguise, the look of the face they wear. */
    public static Look seenByOthers(ServerPlayer player) {
        Optional<UUID> face = Disguises.getDisguisedAs(player);
        if (face.isEmpty()) return of(player);
        ServerPlayer wearer = Attribution.player(player.server, face.get());
        // A disguise ends when its face logs out, so this shouldn't happen. If it does, a neutral color, never the real one.
        return wearer == null ? Look.glow(InnateTechnique.DEFAULT_MALICE_COLOR) : of(wearer);
    }

    /**
     * Innate colors run dark (Rime's is navy), and a dark glow isn't one: the same hue at full brightness, with a little white in it.
     * Only for drawing: the technique's color is what travels.
     */
    public static int bright(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max == 0) return 0xFFFFFFFF;
        float scale = 255f / max;
        return 0xFF000000 | channel(r * scale) << 16 | channel(g * scale) << 8 | channel(b * scale);
    }

    private static int channel(float value) {
        return Math.round(value + (255f - value) * 0.12f);
    }

    /** Sends whatever changed: the Strike came or went, or a disguise began or ended while it was ready. Every tick it's ready, and when it ends. */
    static void sync(ServerPlayer player, MeleeState state, boolean ready) {
        Look own = ready ? of(player) : Look.NONE;
        if (!own.equals(state.tellOwn)) {
            state.tellOwn = own;
            PacketDistributor.sendToPlayer(player, new MeleeTellPayload(player.getId(), own));
        }
        Look others = ready ? seenByOthers(player) : Look.NONE;
        if (!others.equals(state.tellOthers)) {
            state.tellOthers = others;
            PacketDistributor.sendToPlayersTrackingEntity(player, new MeleeTellPayload(player.getId(), others));
        }
    }

    /** Someone came into range after it started. */
    static void onStartTracking(ServerPlayer tracker, Entity target) {
        if (target instanceof ServerPlayer player && player.hasData(MeleeLayer.ATTACHMENT) && MeleeLayer.state(player).tellOthers.kind() != Kind.NONE) {
            PacketDistributor.sendToPlayer(tracker, new MeleeTellPayload(player.getId(), MeleeLayer.state(player).tellOthers));
        }
    }

    /**
     * The Strike lands: the impact and a burst of Malice in the same color as the glow, at the target. Severed: dust and grit. Tried in gold
     * against the Innate color: gold under an Innate burst reads as two effects, and this reads as what was on the fist going off.
     */
    public static void playStrikeImpact(ServerPlayer attacker, Vec3 at) {
        var level = attacker.serverLevel();
        VisualService.playAt(level, at, attacker, impact(of(attacker)), impact(seenByOthers(attacker)));
        VisualService.playAt(level, at, attacker, burst(of(attacker)), burst(seenByOthers(attacker)));
    }

    private static Visual impact(Look look) {
        return look.kind() == Kind.GLOW
                ? Visual.effect(MeleeIds.FX_STRIKE_IMPACT, Visual.Anchor.BODY).tinted(bright(look.color()))
                : Visual.effect(MeleeIds.FX_STRIKE_IMPACT_PHYSICAL, Visual.Anchor.BODY);
    }

    private static Visual burst(Look look) {
        return look.kind() == Kind.GLOW ? Visual.effect(MeleeIds.FX_STRIKE_BURST, Visual.Anchor.BODY).tinted(bright(look.color())) : Visual.NONE;
    }
}
