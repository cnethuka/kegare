package com.cnethuka.kegare.sixeyes.client;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.gui.GuiDraw;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetEntity;
import com.cnethuka.kegare.innate_technique.puppet_manipulation.PuppetManipulation;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.CastType;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.loadout.AbilityLookup;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.sixeyes.ReadsPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Six Eyes' Reads on screen (DESIGN §3.32): a compact tag over every player and mob in view, no staring needed (a player's innate
 * technique and current Malice; a mob's kind and flow; a void says only "Void"), and full detail under the one nearest the crosshair (what
 * they're charging or channelling and how far, cooldowns, toggles). Readable in a six-player fight: nearest first, at most the client
 * setting's count ({@code [six_eyes] readTags}, 6), fading with distance, never over the crosshair.
 */
public final class ReadsClient {
    static final ResourceLocation LAYER = Kegare.id("six_eyes_reads");
    /** No tag inside this many pixels of the crosshair: it moves up out of the way. */
    static final int CLEAR_OF_CROSSHAIR = 22;
    /** Fully clear this close, faded to a trace this far. */
    static final double NEAR = 8, FAR = 64;

    private static final Map<Integer, ReadsPayload.Glance> GLANCES = new HashMap<>();
    @Nullable
    private static ReadsPayload.Focus focus;
    private static Matrix4f projection = new Matrix4f(), view = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private ReadsClient() {}

    public static void init(IEventBus modBus) {
        ReadsPayload.clientHandler = p -> {
            GLANCES.clear();
            for (ReadsPayload.Glance g : p.glances()) GLANCES.put(g.entityId(), g);
            focus = p.focus().orElse(null);
        };
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelow(VanillaGuiLayers.CROSSHAIR, LAYER, ReadsClient::render));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
            projection = new Matrix4f(e.getProjectionMatrix());
            view = new Matrix4f(e.getModelViewMatrix());
            camera = e.getCamera().getPosition();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            GLANCES.clear();
            focus = null;
        });
    }

    /** Where a point in the world falls on the GUI, or null behind the camera or off screen. */
    @Nullable
    static float[] project(Vec3 world, int guiWidth, int guiHeight) {
        Vec3 rel = world.subtract(camera);
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f);
        view.transform(clip);
        projection.transform(clip);
        if (clip.w <= 0.05f) return null;
        float nx = clip.x / clip.w, ny = clip.y / clip.w;
        if (Math.abs(nx) > 1.05f || Math.abs(ny) > 1.05f) return null;
        return new float[] {(nx + 1f) / 2f * guiWidth, (1f - ny) / 2f * guiHeight};
    }

    private record Tag(Entity entity, double distance, float x, float y) {}

    /** Tags placed so far this frame: x, y, half width, height. */
    private static final List<float[]> PLACED = new ArrayList<>();

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!SightClient.isBearer() || mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        List<Tag> tags = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()) continue;
            if (e instanceof PuppetEntity puppet && puppet.isPossessed()) continue; // hidden inside whoever possesses it: they're read instead
            boolean read = e instanceof Player ? GLANCES.containsKey(e.getId()) : SightClient.mob(e.getId()) != null;
            if (!read) continue;
            Vec3 above = e.getPosition(partial).add(0, e.getBbHeight() + 0.45, 0);
            float[] at = project(above, w, h);
            if (at == null) continue;
            tags.add(new Tag(e, e.distanceTo(mc.player), at[0], at[1]));
        }
        tags.sort(Comparator.comparingDouble(Tag::distance));
        int shown = 0, limit = KegareClientConfig.SIX_EYES_READ_TAGS.get();
        PLACED.clear();
        for (Tag tag : tags) {
            boolean focused = focus != null && focus.entityId() == tag.entity().getId();
            if (shown >= limit && !focused) continue; // the focus always shows
            shown++;
            draw(g, mc, tag, focused, w, h);
        }
    }

    private static void draw(GuiGraphics g, Minecraft mc, Tag tag, boolean focused, int w, int h) {
        float alpha = (float) Mth.clamp(1.0 - (tag.distance() - NEAR) / (FAR - NEAR), 0.3, 1.0);
        if (focused) alpha = 1f;
        List<Component> lines = new ArrayList<>();
        int color;
        Entity e = tag.entity();
        if (e instanceof Player) {
            ReadsPayload.Glance glance = GLANCES.get(e.getId());
            if (glance.severed()) {
                lines.add(Component.translatable("gui.kegare.six_eyes.void"));
                color = 0xA0A0B0;
            } else {
                InnateTechnique technique = glance.technique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
                Component name = technique == null ? Component.translatable("gui.kegare.six_eyes.read.no_technique") : technique.displayName();
                lines.add(Component.translatable("gui.kegare.six_eyes.read.player", name, glance.malice()));
                color = SightClient.bright(technique == null ? InnateTechnique.DEFAULT_MALICE_COLOR : technique.maliceColor());
                if (focused) lines.addAll(detail(technique));
            }
        } else if (e instanceof PuppetEntity puppet) {
            // A puppet is read as what it is, whatever it looks like: whose, and its technique, in its owner's color.
            lines.add(Component.translatable("gui.kegare.six_eyes.read.puppet", puppet.ownerName(), PuppetManipulation.PUPPET_MANIPULATION.get().displayName()));
            color = SightClient.bright(puppet.maliceColor());
        } else {
            SightClient.Mob mob = SightClient.mob(e.getId());
            Component kind = BuiltInRegistries.ENTITY_TYPE.byId(mob.type()).getDescription();
            lines.add(Component.translatable(mob.hostile() ? "gui.kegare.six_eyes.read.mob_hostile" : "gui.kegare.six_eyes.read.mob_passive", kind));
            color = mob.hostile() ? SightClient.MOB_HOSTILE : SightClient.MOB_PASSIVE;
        }
        float scale = focused ? 0.75f : 0.6f;
        int lineH = (int) Math.ceil(mc.font.lineHeight * scale) + 1;
        int total = lines.size() * lineH;
        float x = tag.x(), y = tag.y() - total;
        // Never over the crosshair: up and out of the way.
        float cx = w / 2f, cy = h / 2f;
        float halfW = 0;
        for (Component c : lines) halfW = Math.max(halfW, mc.font.width(c) * scale / 2f);
        if (Math.abs(x - cx) < halfW + 4 && y < cy + CLEAR_OF_CROSSHAIR && y + total > cy - CLEAR_OF_CROSSHAIR) y = cy - CLEAR_OF_CROSSHAIR - total;
        // Nearest first, and never on top of one already placed: a crowded fight stacks them upward.
        boolean moved = true;
        while (moved) {
            moved = false;
            for (float[] r : PLACED) {
                if (Math.abs(x - r[0]) < halfW + r[2] + 2 && y < r[1] + r[3] && y + total > r[1]) {
                    y = r[1] - total - 1;
                    moved = true;
                }
            }
        }
        PLACED.add(new float[] {x, y, halfW, total});
        int a = Mth.clamp((int) (alpha * 255), 0, 255) << 24;
        for (int i = 0; i < lines.size(); i++) {
            int c = i == 0 ? color : 0xD8D8E0;
            GuiDraw.centered(g, mc.font, lines.get(i), Math.round(x), Math.round(y) + i * lineH, scale, a | c);
        }
    }

    /** The focus's full detail: what they're doing, their cooldowns, their toggles. */
    private static List<Component> detail(@Nullable InnateTechnique technique) {
        List<Component> out = new ArrayList<>();
        if (focus == null || focus.severed()) return out;
        focus.cast().ifPresent(cast -> {
            Component name = name(technique, cast.ability());
            if (cast.castType() == CastType.CHARGED.ordinal()) {
                out.add(Component.translatable("gui.kegare.six_eyes.read.charging", name, Math.round(cast.progress() * 100) + "%"));
            } else if (cast.progress() < 0) {
                out.add(Component.translatable("gui.kegare.six_eyes.read.channelling_open", name, String.format(java.util.Locale.ROOT, "%.1f", -cast.progress())));
            } else {
                out.add(Component.translatable("gui.kegare.six_eyes.read.channelling", name, Math.round(cast.progress() * 100) + "%"));
            }
        });
        if (!focus.toggles().isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (ResourceLocation id : focus.toggles()) names.add(name(technique, id));
            out.add(Component.translatable("gui.kegare.six_eyes.read.toggles", join(names)));
        }
        if (!focus.cooldowns().isEmpty()) {
            List<Component> parts = new ArrayList<>();
            for (ReadsPayload.Cooldown c : focus.cooldowns()) {
                parts.add(Component.translatable("gui.kegare.six_eyes.read.cooldown", name(technique, c.ability()), String.format(java.util.Locale.ROOT, "%.1f", c.seconds())));
            }
            out.add(Component.translatable("gui.kegare.six_eyes.read.cooldowns", join(parts)));
        }
        if (out.isEmpty()) out.add(Component.translatable("gui.kegare.six_eyes.read.idle"));
        return out;
    }

    private static Component name(@Nullable InnateTechnique technique, ResourceLocation id) {
        Optional<Ability> ability = AbilityLookup.find(technique, id);
        if (ability.isEmpty() && technique != null) {
            for (Ability a : technique.abilities()) {
                if (a.special().filter(s -> s.id().equals(id)).isPresent()) return a.special().get().displayName();
            }
            if (technique.ultimate().filter(u -> u.id().equals(id)).isPresent()) return technique.ultimate().get().displayName();
        }
        return ability.map(Ability::displayName).orElse(Component.translatable("ability." + id.getNamespace() + "." + id.getPath()));
    }

    private static Component join(List<Component> parts) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(parts.get(i));
        }
        return out;
    }
}
