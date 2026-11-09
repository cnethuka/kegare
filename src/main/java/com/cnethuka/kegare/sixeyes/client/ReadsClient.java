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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
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
 * Six Eyes' Reads on screen (DESIGN §3.32): a compact tag over every player and mob in view, no staring needed (a player's innate technique,
 * current Malice and health; a mob's kind, flow and health; a void says only "Void"), and a panel beside the one nearest the crosshair with
 * its whole state: what they're doing, health, defence, offence and movement, every effect, what's happening to them and, for a player,
 * hunger. Readable in a six-player fight: nearest first, at most the client setting's count ({@code [six_eyes] readTags}, 6), fading with
 * distance; the panel is boxed, so it reads by day, at night and against the sky, and never sits over the crosshair.
 */
public final class ReadsClient {
    static final ResourceLocation LAYER = Kegare.id("six_eyes_reads");
    /** Nothing inside this many pixels of the crosshair: tags move up out of the way, the panel goes to one side. */
    static final int CLEAR_OF_CROSSHAIR = 22;
    /** Fully clear this close, faded to a trace this far. */
    static final double NEAR = 8, FAR = 64;
    static final float TAG_SCALE = 0.6f;
    /** The panel's text, and smaller if it doesn't fit clear of the crosshair. */
    static final float[] PANEL_SCALES = {0.75f, 0.6f, 0.5f};
    private static final int PANEL_BACK = 0xD80B0810, DETAIL = 0xFFD8D8E0, DIM = 0xFF9A98A6, HEALTH = 0xFFFF8A8A, BAD = 0xFFFF6B6B;

    private static final Map<Integer, ReadsPayload.Glance> GLANCES = new HashMap<>();
    private static final Map<Integer, ReadsPayload.MobGlance> MOBS = new HashMap<>();
    @Nullable
    private static ReadsPayload.Focus focus;
    private static Matrix4f projection = new Matrix4f(), view = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;

    private ReadsClient() {}

    public static void init(IEventBus modBus) {
        ReadsPayload.clientHandler = p -> {
            GLANCES.clear();
            for (ReadsPayload.Glance g : p.glances()) GLANCES.put(g.entityId(), g);
            MOBS.clear();
            for (ReadsPayload.MobGlance m : p.mobs()) MOBS.put(m.entityId(), m);
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
            MOBS.clear();
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

    /** One line of the panel: its text and color, and an effect's icon before it, if it's one. */
    private record Line(Component text, int color, @Nullable Holder<MobEffect> icon) {}

    /** A row of shortened effects. */
    private record EffectRow(List<ReadsPayload.Effect> effects) {}

    /** Which body the panel was last beside, and on which side: it keeps that side while the body is near the crosshair. */
    private static int lastPanelFor = -1;
    private static boolean lastPanelRight = true;

    /** Tags placed so far this frame: x, y, half width, height (the panel's too, so nothing lands on it). */
    private static final List<float[]> PLACED = new ArrayList<>();

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!SightClient.isBearer() || mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        List<Tag> tags = new ArrayList<>();
        Tag focused = null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()) continue;
            if (e instanceof PuppetEntity puppet && puppet.isPossessed()) continue; // hidden inside whoever possesses it: they're read instead
            boolean read = e instanceof Player ? GLANCES.containsKey(e.getId()) : SightClient.mob(e.getId()) != null;
            if (!read) continue;
            Vec3 above = e.getPosition(partial).add(0, e.getBbHeight() + 0.45, 0);
            float[] at = project(above, w, h);
            if (at == null) continue;
            Tag tag = new Tag(e, e.distanceTo(mc.player), at[0], at[1]);
            if (focus != null && focus.entityId() == e.getId()) focused = tag;
            else tags.add(tag);
        }
        tags.sort(Comparator.comparingDouble(Tag::distance));
        PLACED.clear();
        Runnable panel = focused == null ? null : panel(g, mc, focused, w, h); // placed first, so the tags keep off it
        int shown = focused == null ? 0 : 1, limit = KegareClientConfig.SIX_EYES_READ_TAGS.get();
        for (Tag tag : tags) {
            if (shown >= limit) break;
            shown++;
            drawTag(g, mc, tag, w, h);
        }
        if (panel != null) panel.run();
    }

    // ---------------------------------------------------------------- the compact tags

    /** A body's compact Read and its color: "Rime · 820 Malice · 14/20", "Zombie · hostile flow · 18/20", "Void". */
    private static Line compact(Entity e) {
        if (e instanceof Player) {
            ReadsPayload.Glance glance = GLANCES.get(e.getId());
            if (glance == null || glance.severed()) return new Line(Component.translatable("gui.kegare.six_eyes.void"), 0xFFA0A0B0, null);
            ReadsPayload.Energy energy = glance.energy().get();
            InnateTechnique technique = energy.technique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
            Component name = technique == null ? Component.translatable("gui.kegare.six_eyes.read.no_technique") : technique.displayName();
            int color = SightClient.bright(technique == null ? InnateTechnique.DEFAULT_MALICE_COLOR : technique.maliceColor());
            return new Line(Component.translatable("gui.kegare.six_eyes.read.player", name, energy.malice(), health(energy.health(), energy.maxHealth())),
                    0xFF000000 | color, null);
        }
        ReadsPayload.MobGlance mob = MOBS.get(e.getId());
        Component health = mob == null ? null : health(mob.health(), mob.maxHealth());
        if (e instanceof PuppetEntity puppet) {
            // A puppet is read as what it is, whatever it looks like: whose, and its technique, in its owner's color, and its own health.
            Component base = Component.translatable("gui.kegare.six_eyes.read.puppet", puppet.ownerName(), PuppetManipulation.PUPPET_MANIPULATION.get().displayName());
            return new Line(withHealth(base, health), 0xFF000000 | SightClient.bright(puppet.maliceColor()), null);
        }
        SightClient.Mob seen = SightClient.mob(e.getId());
        Component kind = BuiltInRegistries.ENTITY_TYPE.byId(seen.type()).getDescription();
        Component base = Component.translatable(seen.hostile() ? "gui.kegare.six_eyes.read.mob_hostile" : "gui.kegare.six_eyes.read.mob_passive", kind);
        return new Line(withHealth(base, health), 0xFF000000 | (seen.hostile() ? SightClient.MOB_HOSTILE : SightClient.MOB_PASSIVE), null);
    }

    private static Component health(float health, float max) {
        return Component.translatable("gui.kegare.six_eyes.read.health_short", ReadsText.amount(health), ReadsText.amount(max));
    }

    private static Component withHealth(Component base, @Nullable Component health) {
        return health == null ? base : Component.translatable("gui.kegare.six_eyes.read.with_health", base, health);
    }

    private static void drawTag(GuiGraphics g, Minecraft mc, Tag tag, int w, int h) {
        float alpha = (float) Mth.clamp(1.0 - (tag.distance() - NEAR) / (FAR - NEAR), 0.3, 1.0);
        Line line = compact(tag.entity());
        int lineH = (int) Math.ceil(mc.font.lineHeight * TAG_SCALE) + 1;
        float x = tag.x(), y = tag.y() - lineH;
        float halfW = mc.font.width(line.text()) * TAG_SCALE / 2f;
        // Never over the crosshair: up and out of the way.
        float cx = w / 2f, cy = h / 2f;
        if (Math.abs(x - cx) < halfW + 4 && y < cy + CLEAR_OF_CROSSHAIR && y + lineH > cy - CLEAR_OF_CROSSHAIR) y = cy - CLEAR_OF_CROSSHAIR - lineH;
        // Nearest first, and never on top of one already placed (or the panel): a crowded fight stacks them upward.
        boolean moved = true;
        while (moved) {
            moved = false;
            for (float[] r : PLACED) {
                if (Math.abs(x - r[0]) < halfW + r[2] + 2 && y < r[1] + r[3] && y + lineH > r[1]) {
                    y = r[1] - lineH - 1;
                    moved = true;
                }
            }
        }
        PLACED.add(new float[] {x, y, halfW, lineH});
        int a = Mth.clamp((int) (alpha * 255), 0, 255) << 24;
        GuiDraw.centered(g, mc.font, line.text(), Math.round(x), Math.round(y), TAG_SCALE, a | (line.color() & 0xFFFFFF));
    }

    // ---------------------------------------------------------------- the focus's panel

    /** Lays the focus's panel out, keeps its place clear for the tags, and returns how to draw it. */
    private static Runnable panel(GuiGraphics g, Minecraft mc, Tag tag, int w, int h) {
        Entity e = tag.entity();
        Line title = compact(e);
        List<Object> rows = new ArrayList<>(); // Lines and EffectRows
        rows.add(title);
        ReadsPayload.Body body = focus.body().orElse(null);
        if (body != null) {
            InnateTechnique technique = e instanceof Player ? technique(e) : null;
            if (e instanceof Player) for (Component c : activity(body, technique)) rows.add(new Line(c, DETAIL, null));
            vitals(rows, body.vitals());
            effects(rows, body.effects());
            Component status = status(body.status());
            if (status != null) rows.add(new Line(status, BAD, null));
            if (body.vitals().food() >= 0) rows.add(new Line(Component.translatable("gui.kegare.six_eyes.read.hunger", body.vitals().food()), DETAIL, null));
        }
        Font font = mc.font;
        // Smaller if it won't fit clear of the crosshair at full size; left out if it fits nowhere, rather than cover it.
        for (float scale : PANEL_SCALES) {
            int lineH = (int) Math.ceil(font.lineHeight * scale) + 1, pad = 3, icon = (int) Math.ceil(9 * scale);
            int inner = 0;
            for (Object row : rows) inner = Math.max(inner, rowWidth(font, row, icon, scale));
            int pw = inner + pad * 2, ph = rows.size() * lineH + pad * 2 - 1;
            Boolean prefer = lastPanelFor == e.getId() ? lastPanelRight : null;
            int[] at = ReadsText.placePanel(tag.x(), tag.y(), pw, ph, w, h, CLEAR_OF_CROSSHAIR, 10, prefer);
            if (at == null) continue;
            lastPanelFor = e.getId();
            lastPanelRight = at[2] == 1;
            PLACED.add(new float[] {at[0] + pw / 2f, at[1], pw / 2f, ph});
            int border = 0xFF000000 | (title.color() & 0xFFFFFF);
            return () -> {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(at[0], at[1], at[0] + pw, at[1] + ph, PANEL_BACK);
                g.renderOutline(at[0], at[1], pw, ph, border);
                int y = at[1] + pad;
                for (Object row : rows) {
                    drawRow(g, mc, row, at[0] + pad, y, icon, scale);
                    y += lineH;
                }
                g.pose().popPose();
            };
        }
        return null;
    }

    private static int rowWidth(Font font, Object row, int icon, float scale) {
        if (row instanceof Line line) return (line.icon() == null ? 0 : icon + 2) + Math.round(font.width(line.text()) * scale);
        int width = 0;
        for (ReadsPayload.Effect effect : ((EffectRow) row).effects()) width += icon + 1 + Math.round(font.width(shortEffect(effect)) * scale) + 5;
        return width;
    }

    private static void drawRow(GuiGraphics g, Minecraft mc, Object row, int x, int y, int icon, float scale) {
        if (row instanceof Line line) {
            if (line.icon() != null) {
                drawIcon(g, mc, line.icon(), x, y - 1, icon);
                x += icon + 2;
            }
            GuiDraw.text(g, mc.font, line.text(), x, y, scale, line.color());
            return;
        }
        for (ReadsPayload.Effect effect : ((EffectRow) row).effects()) {
            Holder<MobEffect> holder = effect(effect.effect());
            if (holder != null) drawIcon(g, mc, holder, x, y - 1, icon);
            x += icon + 1;
            Component text = shortEffect(effect);
            GuiDraw.text(g, mc.font, text, x, y, scale, effectColor(holder));
            x += Math.round(mc.font.width(text) * scale) + 5;
        }
    }

    private static void drawIcon(GuiGraphics g, Minecraft mc, Holder<MobEffect> effect, int x, int y, int size) {
        TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
        g.blit(x, y, 0, size, size, sprite);
    }

    @Nullable
    private static InnateTechnique technique(Entity e) {
        ReadsPayload.Glance glance = GLANCES.get(e.getId());
        return glance == null ? null : glance.energy().flatMap(ReadsPayload.Energy::technique).map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
    }

    /** What they're doing: charging or channelling and how far, their toggles, their cooldowns, or "Idle". */
    private static List<Component> activity(ReadsPayload.Body body, @Nullable InnateTechnique technique) {
        List<Component> out = new ArrayList<>();
        body.cast().ifPresent(cast -> {
            Component name = name(technique, cast.ability());
            if (cast.castType() == CastType.CHARGED.ordinal()) {
                out.add(Component.translatable("gui.kegare.six_eyes.read.charging", name, Math.round(cast.progress() * 100) + "%"));
            } else if (cast.progress() < 0) {
                out.add(Component.translatable("gui.kegare.six_eyes.read.channelling_open", name, String.format(java.util.Locale.ROOT, "%.1f", -cast.progress())));
            } else {
                out.add(Component.translatable("gui.kegare.six_eyes.read.channelling", name, Math.round(cast.progress() * 100) + "%"));
            }
        });
        if (!body.toggles().isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (ResourceLocation id : body.toggles()) names.add(name(technique, id));
            out.add(Component.translatable("gui.kegare.six_eyes.read.toggles", join(names)));
        }
        if (!body.cooldowns().isEmpty()) {
            List<Component> parts = new ArrayList<>();
            for (ReadsPayload.Cooldown c : body.cooldowns()) {
                parts.add(Component.translatable("gui.kegare.six_eyes.read.cooldown", name(technique, c.ability()), String.format(java.util.Locale.ROOT, "%.1f", c.seconds())));
            }
            out.add(Component.translatable("gui.kegare.six_eyes.read.cooldowns", join(parts)));
        }
        if (out.isEmpty()) out.add(Component.translatable("gui.kegare.six_eyes.read.idle"));
        return out;
    }

    /** Health and absorption; armour, toughness and knockback resistance; attack and movement, as they are now. */
    private static void vitals(List<Object> rows, ReadsPayload.Vitals v) {
        MutableComponent health = Component.translatable("gui.kegare.six_eyes.read.health", ReadsText.amount(v.health()), ReadsText.amount(v.maxHealth()));
        if (v.absorption() > 0) health.append(Component.translatable("gui.kegare.six_eyes.read.absorption", ReadsText.amount(v.absorption())));
        rows.add(new Line(health, HEALTH, null));
        rows.add(new Line(Component.translatable("gui.kegare.six_eyes.read.defence", ReadsText.amount(Math.max(0, v.armor())),
                ReadsText.amount(Math.max(0, v.toughness())), ReadsText.percent(Math.max(0, v.knockbackResistance()))), DETAIL, null));
        List<Component> offence = new ArrayList<>();
        offence.add(v.attackDamage() == ReadsPayload.Vitals.NONE ? Component.translatable("gui.kegare.six_eyes.read.no_attack")
                : Component.translatable("gui.kegare.six_eyes.read.attack", ReadsText.amount(v.attackDamage())));
        if (v.attackSpeed() != ReadsPayload.Vitals.NONE) offence.add(Component.translatable("gui.kegare.six_eyes.read.attack_speed", ReadsText.amount(v.attackSpeed())));
        if (v.movement() != ReadsPayload.Vitals.NONE) {
            offence.add(Component.translatable("gui.kegare.six_eyes.read.movement", String.format(java.util.Locale.ROOT, "%.3f", v.movement()),
                    ReadsText.share(v.movement(), v.movementBase())));
        }
        rows.add(new Line(join(offence, " · "), DETAIL, null));
    }

    /** Every effect: a line each with its name while there are few; shortened to icon, level and time when there are many. */
    private static void effects(List<Object> rows, List<ReadsPayload.Effect> effects) {
        if (effects.isEmpty()) {
            rows.add(new Line(Component.translatable("gui.kegare.six_eyes.read.no_effects"), DIM, null));
            return;
        }
        if (!ReadsText.shortened(effects.size())) {
            for (ReadsPayload.Effect effect : effects) {
                Holder<MobEffect> holder = effect(effect.effect());
                Component name = holder == null ? Component.literal(effect.effect().getPath()) : holder.value().getDisplayName();
                rows.add(new Line(Component.translatable("gui.kegare.six_eyes.read.effect", name, ReadsText.level(effect.amplifier()), ReadsText.duration(effect.ticks())),
                        effectColor(holder), holder));
            }
            return;
        }
        int shown = ReadsText.effectsShown(effects.size());
        for (int i = 0; i < shown; i += ReadsText.EFFECTS_PER_ROW) rows.add(new EffectRow(effects.subList(i, Math.min(shown, i + ReadsText.EFFECTS_PER_ROW))));
        if (shown < effects.size()) rows.add(new Line(Component.translatable("gui.kegare.six_eyes.read.more_effects", effects.size() - shown), DIM, null));
    }

    private static Component shortEffect(ReadsPayload.Effect effect) {
        return Component.literal(ReadsText.level(effect.amplifier()) + " " + ReadsText.duration(effect.ticks()));
    }

    @Nullable
    private static Holder<MobEffect> effect(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(id).map(h -> (Holder<MobEffect>) h).orElse(null);
    }

    private static int effectColor(@Nullable Holder<MobEffect> effect) {
        if (effect == null) return DETAIL;
        return switch (effect.value().getCategory()) {
            case BENEFICIAL -> 0xFF9EE6A0;
            case HARMFUL -> 0xFFFF9A8A;
            default -> DETAIL;
        };
    }

    /** What's happening to the body, if anything: burning, Freeze, a stun, Burnout, possessing a puppet. */
    @Nullable
    private static Component status(ReadsPayload.Status s) {
        List<Component> parts = new ArrayList<>();
        if (s.burning()) parts.add(Component.translatable("gui.kegare.six_eyes.read.burning"));
        if (s.freezeStacks() > 0) parts.add(Component.translatable("gui.kegare.six_eyes.read.freeze", s.freezeStacks()));
        if (s.stunTicks() > 0) parts.add(Component.translatable("gui.kegare.six_eyes.read.stunned", ReadsText.seconds(s.stunTicks())));
        if (s.burnoutTicks() > 0) parts.add(Component.translatable("gui.kegare.six_eyes.read.burnt_out", ReadsText.seconds(s.burnoutTicks())));
        if (s.possessing()) parts.add(Component.translatable("gui.kegare.six_eyes.read.possessing"));
        return parts.isEmpty() ? null : join(parts, " · ");
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
        return join(parts, ", ");
    }

    private static Component join(List<Component> parts, String separator) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(parts.get(i));
        }
        return out;
    }
}
