package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.Kegare;
import com.cnethuka.kegare.client.CombatPanelLayer;
import com.cnethuka.kegare.client.gui.RevealScript.Scene;
import com.cnethuka.kegare.client.gui.RevealScript.Step;
import com.cnethuka.kegare.client.visual.PlayerAnimations;
import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.innatetechnique.Ability;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.network.RevealPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.KegareTraits;
import com.cnethuka.kegare.trait.Trait;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

/**
 * The cinematic reveal of a roll the server has ALREADY decided and saved (Awakening, Reawakening, admin rerolls, replacement rolls).
 * Cosmetic only: closing it at any time (or disconnecting) changes nothing, and it never sends anything back. Every sound is a UI sound
 * on this client, so nobody else hears it.
 * <p>
 * {@link RevealScript} says which scene runs when; this draws them. They're drawn on a canvas at a whole-number multiple of GUI units
 * ({@link RevealLayout}), so the scenes look the same at every GUI scale and only have to fit 320x240.
 * <ul>
 *   <li>Esc, Space, Enter or a click skips to the end; again closes.</li>
 *   <li>Client config {@code reveal.animation=false} shows the end at once; {@code reveal.alwaysFull} plays the full intro every time.</li>
 *   <li>{@code reveal.reducedMotion} (or vanilla's Hide Lightning Flashes) tones flashes down and turns shake off.</li>
 * </ul>
 */
public final class RevealScreen extends Screen {
    private static final ResourceLocation TALISMAN = Kegare.id("reveal/talisman");
    private static final ResourceLocation GLOW = Kegare.id("reveal/glow");
    private static final ResourceLocation RING = Kegare.id("reveal/ring");
    private static final ResourceLocation RAY = Kegare.id("reveal/ray");
    private static final ResourceLocation STAR = Kegare.id("reveal/star");
    private static final ResourceLocation STAR_SMALL = Kegare.id("reveal/star_small");
    private static final ResourceLocation ASH = Kegare.id("reveal/ash");
    private static final ResourceLocation[] FLAMES = new ResourceLocation[8];
    private static final ResourceLocation[] EYE = new ResourceLocation[6];

    static {
        for (int i = 0; i < FLAMES.length; i++) FLAMES[i] = Kegare.id("reveal/flame_" + i);
        for (int i = 0; i < EYE.length; i++) EYE[i] = Kegare.id("reveal/eye_" + i);
    }

    private static final int TALISMAN_W = 40;
    private static final int TALISMAN_H = 80;
    private static final int ASH_W = 48;
    private static final int ASH_H = 18;
    private static final int EYE_W = 72;
    private static final int EYE_H = 36;
    private static final int SEVERED_GREY = 0x8A8A92;
    private static final int SIX_EYES_BLUE = 0x39C0FF;

    // Draw order. GUI depth testing is on, so what's drawn later must not sit behind what's already there.
    private static final int Z_PARTICLES = 150;
    private static final int Z_BAND = 180;
    private static final int Z_DETAILS = 200;
    private static final int Z_TEXT = 300;
    private static final int Z_OVERLAY = 800;
    private static final int Z_HINT = 850;

    /** How long a word takes to slam down. */
    private static final long SLAM_MS = 130;
    private static final long STAR_DROP_MS = 220;
    /** Presses this soon after the reveal opens, or after it reached its end, are ignored: a double-press shouldn't throw the result away unseen. */
    private static final long INPUT_GUARD_MS = 280;
    /** The signature animation plays this often while the name shows. */
    private static final long SIGNATURE_EVERY_MS = 3400;

    private final RevealPayload reveal;
    @Nullable
    private final InnateTechnique result;
    @Nullable
    private final InnateTechniqueRarity rarity;
    private final RevealScript script;
    private final boolean sixEyes;
    private final boolean fakeOut;
    private final boolean animated;
    private final List<Trait> listedTraits = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final RevealParticles particles = new RevealParticles();
    /** The talisman's burn line: how far each column lags the middle, so the edge is ragged. */
    private final float[] burnEdge = new float[TALISMAN_W];

    // The clock.
    private long startMillis = -1;
    private long lastFrameMillis;
    private boolean skipped;
    private long endedAtMillis = -1;
    @Nullable
    private Step current;
    /** One-shot cues already played in the current scene. */
    private int cues;
    private boolean stingerPlayed;
    /** When the name card began; the card's fade-ins run from it. Negative: it's shown complete (skipped to, or no animation). */
    private long cardStartMillis = -1;
    /** When {@link Scene#RESULT} began after another scene: its extra lines fade in from then. */
    private long resultStartMillis = -1;
    private long nextSignatureMillis;
    @Nullable
    private ResourceLocation playingSignature;

    // One-shot screen effects.
    private long flashStartMillis;
    private long flashLengthMillis;
    private float flashStrength;
    private int flashRgb;
    private long shakeStartMillis;
    private long shakeLengthMillis;
    private float shakeAmount;
    private long ringStartMillis = -1;
    private int ringRgb;
    private float ringX, ringY, ringSize;

    // Layout, recomputed in init(): follows GUI scale and window size.
    private int canvasScale = 1;
    private int cw = RevealLayout.MIN_W;
    private int ch = RevealLayout.MIN_H;
    private int centerX;
    private int centerY;
    private Card card = Card.EMPTY;

    /** Where the name card's parts go on this canvas. */
    private record Card(int emblem, int emblemY, int nameScale, int nameY, int starsY, List<FormattedCharSequence> desc, int descY, int iconsY,
                        int iconStep, int linesY) {
        static final Card EMPTY = new Card(48, 0, 1, 0, 0, List.of(), 0, 0, 0, 0);
    }

    public RevealScreen(RevealPayload reveal) {
        super(Component.translatable("gui.kegare.reveal.title." + reveal.reason().name().toLowerCase(Locale.ROOT)));
        this.reveal = reveal;
        this.result = reveal.severed() ? null : reveal.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        this.rarity = result == null ? null : result.rarity();
        this.sixEyes = reveal.traits().contains(KegareTraits.SIX_EYES.getId());
        for (ResourceLocation id : reveal.traits()) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait != null && !id.equals(KegareTraits.SIX_EYES.getId())) listedTraits.add(trait); // Six Eyes has its own lines
        }
        this.animated = KegareClientConfig.REVEAL_ANIMATION.get();
        boolean seen = result != null && KegareClientConfig.REVEAL_SEEN_TECHNIQUES.get().contains(result.id().toString());
        boolean full = RevealScript.playsFull(seen, KegareClientConfig.REVEAL_ALWAYS_FULL.get());
        this.script = RevealScript.of(new RevealScript.Input(rarity, reveal.severed(), reveal.birth(), sixEyes, full));
        this.fakeOut = rarity == InnateTechniqueRarity.LEGENDARY && full && animated
                && (KegareClientConfig.REVEAL_DEBUG_FORCE_FAKE_OUT.get() || random.nextDouble() < KegareClientConfig.REVEAL_FAKE_OUT_CHANCE.get());
        if (animated && result != null && !seen) markSeen(result);
        if (!animated) skipped = true;
        for (int c = 0; c < TALISMAN_W; c++) {
            burnEdge[c] = 3.5f * Mth.sin(c * 0.42f) + 2.5f * Mth.sin(c * 1.13f + 1.7f) + (random.nextFloat() - 0.5f) * 2.5f;
        }
    }

    /** The first roll of a technique plays its full intro; this client remembers that it has. */
    private static void markSeen(InnateTechnique technique) {
        List<String> seen = new ArrayList<>(KegareClientConfig.REVEAL_SEEN_TECHNIQUES.get());
        seen.add(technique.id().toString());
        KegareClientConfig.REVEAL_SEEN_TECHNIQUES.set(seen);
        KegareClientConfig.SPEC.save();
    }

    @Override
    protected void init() {
        canvasScale = RevealLayout.canvasScale(width, height);
        cw = Mth.ceil(width / (float) canvasScale);
        ch = Mth.ceil(height / (float) canvasScale);
        centerX = cw / 2;
        centerY = ch / 2;
        card = layoutCard();
    }

    /** The name card, stacked from the top: title, emblem with the name across it, stars and rarity, description, icons, then the extra lines. */
    private Card layoutCard() {
        if (result == null) return Card.EMPTY;
        int top = 13, bottom = 13;
        int nameScale = RevealLayout.textScale(font.width(result.displayName()), cw - 24, ch >= 340 ? 4 : 3);
        List<FormattedCharSequence> desc = font.split(GuiDraw.description(result.descriptionId()), Math.min(cw - 32, 360));
        int descH = desc.size() * (font.lineHeight + 1);
        int icons = CombatPanelLayer.SLOT_SIZE;
        int below = 13 + descH + 3 + icons + 4 + extraLinesHeight();
        int room = ch - top - bottom - below - 2;
        int emblem = RevealLayout.emblemSize(room);
        int y = top + Math.max(0, (room - emblem) / 2);
        int nameH = font.lineHeight * nameScale;
        int nameY = y + Math.round(emblem * 0.72f) - nameH / 2;
        int under = Math.max(y + emblem, nameY + nameH) + 2;
        int descY = under + 13;
        int iconsY = descY + descH + 3;
        int step = RevealLayout.rowStep(result.abilities().size(), icons, 3, cw - 24);
        return new Card(emblem, y, nameScale, nameY, under, desc, descY, iconsY, step, iconsY + icons + 4);
    }

    /** The lines under the icons: Malice (or why this roll happened), Six Eyes' two lines, any other trait's. */
    private int extraLinesHeight() {
        int line = font.lineHeight + 1;
        int lines = 1;
        if (sixEyes) lines += 2;
        for (Trait trait : listedTraits) lines += trait.isUnique() ? 2 : 1;
        return lines * line;
    }

    // ---------------------------------------------------------------- the clock and input

    private long now() {
        return Util.getMillis();
    }

    private boolean atEnd(long t) {
        return t >= script.total();
    }

    /** Skip to the end, or close once it's there. */
    private void advance() {
        long now = now();
        if (startMillis < 0 || now - startMillis < INPUT_GUARD_MS) return; // not drawn yet, or only just
        long t = skipped ? script.total() : now - startMillis;
        if (!atEnd(t)) {
            skipped = true;
            endedAtMillis = now;
        } else if (endedAtMillis < 0 || now - endedAtMillis >= INPUT_GUARD_MS) {
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            advance();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        advance();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Esc skips first, then closes (keyPressed)
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        stopSignature();
        RevealQueue.onRevealClosed();
        super.removed();
    }

    // ---------------------------------------------------------------- the frame

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Opaque: nothing behind (chat, HUD) can spoil the result, since the HUD already shows it.
        g.pose().pushPose();
        g.pose().scale(canvasScale, canvasScale, 1f);
        GuiDraw.inkBackground(g, cw, ch);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = now();
        if (startMillis < 0) startMillis = now;
        float dt = lastFrameMillis == 0 ? 0f : Math.min(0.1f, (now - lastFrameMillis) / 1000f);
        lastFrameMillis = now;
        long t = skipped ? script.total() : now - startMillis;
        Step step = script.at(t);
        if (step != current) enter(step, current, now);
        if (atEnd(t) && endedAtMillis < 0) endedAtMillis = now;

        g.pose().pushPose();
        g.pose().scale(canvasScale, canvasScale, 1f);
        g.pose().pushPose();
        applyShake(g, now);
        switch (step.scene()) {
            case OMEN -> renderOmen(g, step, t, dt);
            case BURN_OUT -> renderBurnOut(g, step, t, dt);
            case RARITY -> renderRarity(g, step, t, now);
            case STARS -> renderStars(g, step, t, now);
            case EPITHET -> renderEpithet(g, step, t);
            case NAME -> renderCard(g, step.scene(), now, dt, partialTick);
            case MALICE -> renderMalice(g, step, t, now);
            case HEAVEN -> renderLine(g, dt, Component.translatable("gui.kegare.reveal.severed.heaven"), 0xD8D8DE);
            case BORN_WITHOUT -> renderLine(g, dt, Component.translatable("gui.kegare.reveal.severed.title"), GuiDraw.RED & 0xFFFFFF);
            case SIX_EYES -> renderSixEyes(g, step, t, dt);
            case RESULT -> renderResult(g, now, dt, partialTick);
        }
        renderRing(g, now);
        particles.render(g, dt, Z_PARTICLES);
        g.pose().popPose();
        renderOverlays(g, step, t, now);
        renderHint(g, t, now);
        g.pose().popPose();
    }

    /** A scene begins. */
    private void enter(Step step, @Nullable Step previous, long now) {
        current = step;
        cues = 0;
        boolean cardContinues = previous != null && previous.scene() == Scene.NAME && step.scene() == Scene.RESULT;
        if (!cardContinues) particles.clear();
        Scene scene = step.scene();
        if (previous != null && previous.scene() == Scene.NAME && !cardContinues) stopSignature();
        switch (scene) {
            case OMEN, BURN_OUT -> {
                play(SoundEvents.FLINTANDSTEEL_USE, 1f, 0.9f);
                play(SoundEvents.FIRECHARGE_USE, scene == Scene.BURN_OUT ? 0.7f : 0.9f, 0.45f);
            }
            case NAME -> {
                cardStartMillis = now;
                nextSignatureMillis = now + 300;
                stinger();
                nameImpact(now);
            }
            case MALICE -> play(SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
            case HEAVEN -> play(SoundEvents.BEACON_DEACTIVATE, 0.6f, 0.9f);
            case BORN_WITHOUT -> play(SoundEvents.SOUL_ESCAPE.value(), 0.7f, 0.9f);
            case SIX_EYES -> {
                play(SoundEvents.BEACON_ACTIVATE, 1.5f, 0.7f);
                play(SoundEvents.ENDER_EYE_DEATH, 0.7f, 0.6f);
            }
            case RESULT -> {
                if (skipped) {
                    cardStartMillis = -1; // complete at once
                    resultStartMillis = -1;
                    if (animated && result != null) nameImpact(now);
                } else if (cardContinues) {
                    resultStartMillis = now;
                } else { // back from a later scene
                    cardStartMillis = -1;
                    resultStartMillis = now;
                }
                if (!cardContinues) nextSignatureMillis = now + 200;
                stinger();
            }
            default -> {}
        }
    }

    /** The sound of the result landing. Plays once per reveal, even if the player skipped to it. */
    private void stinger() {
        if (stingerPlayed) return;
        stingerPlayed = true;
        if (reveal.severed()) {
            play(SoundEvents.BEACON_DEACTIVATE, 0.6f, 1f);
            return;
        }
        if (rarity == null) return;
        switch (rarity) {
            case COMMON -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1f);
            case RARE -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            case EPIC -> play(SoundEvents.PLAYER_LEVELUP, 0.8f, 1f);
            case LEGENDARY -> {
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                play(SoundEvents.TOTEM_USE, 1.2f, 0.5f);
            }
        }
    }

    private void nameImpact(long now) {
        int rgb = rarity == null ? 0xFFFFFF : rarity.color();
        if (rarity == InnateTechniqueRarity.LEGENDARY) {
            flash(GuiDraw.mix(rgb, 0xFFFFFF, 0.7f), 1f, 520, now);
            shake(3f, 320, now);
        } else {
            flash(rgb, rarity == InnateTechniqueRarity.EPIC ? 0.6f : 0.35f, 260, now);
        }
        int cy = card.emblemY + card.emblem / 2;
        ring(centerX, cy, card.emblem * 2.6f, rgb, now);
        particles.burst(centerX, cy, rarity == InnateTechniqueRarity.LEGENDARY ? 70 : 28, 30f, 130f, maliceParticleColor());
    }

    // ---------------------------------------------------------------- one-shot effects

    private boolean reducedMotion() {
        return KegareClientConfig.REVEAL_REDUCED_MOTION.get() || (minecraft != null && minecraft.options.hideLightningFlash().get());
    }

    private void flash(int rgb, float strength, long length, long now) {
        flashRgb = rgb;
        flashStrength = reducedMotion() ? strength * 0.15f : strength;
        flashStartMillis = now;
        flashLengthMillis = length;
    }

    private void shake(float amount, long length, long now) {
        if (reducedMotion()) return;
        shakeAmount = amount;
        shakeStartMillis = now;
        shakeLengthMillis = length;
    }

    private void applyShake(GuiGraphics g, long now) {
        if (shakeLengthMillis <= 0) return;
        float left = 1f - (now - shakeStartMillis) / (float) shakeLengthMillis;
        if (left <= 0f) {
            shakeLengthMillis = 0;
            return;
        }
        float amount = shakeAmount * left * left;
        g.pose().translate(Math.round((random.nextFloat() - 0.5f) * 2f * amount), Math.round((random.nextFloat() - 0.5f) * 2f * amount), 0);
    }

    private void ring(float x, float y, float size, int rgb, long now) {
        ringX = x;
        ringY = y;
        ringSize = size;
        ringRgb = rgb;
        ringStartMillis = now;
    }

    private void renderRing(GuiGraphics g, long now) {
        if (ringStartMillis < 0) return;
        float p = (now - ringStartMillis) / 420f;
        if (p >= 1f) {
            ringStartMillis = -1;
            return;
        }
        int size = Math.round(ringSize * (0.25f + 0.75f * outCubic(p)));
        additive(true);
        GuiDraw.tinted(g, RING, Math.round(ringX) - size / 2, Math.round(ringY) - size / 2, size, size, ringRgb, (1f - p) * 0.8f);
        additive(false);
    }

    /** Flash, and the dip to black between scenes. */
    private void renderOverlays(GuiGraphics g, Step step, long t, long now) {
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_OVERLAY);
        if (flashLengthMillis > 0) {
            float left = 1f - (now - flashStartMillis) / (float) flashLengthMillis;
            if (left <= 0f) flashLengthMillis = 0;
            else g.fill(0, 0, cw + 1, ch + 1, ((int) (flashStrength * left * left * 255) << 24) | flashRgb);
        }
        float dip = dip(step, t);
        if (dip > 0f) g.fill(0, 0, cw + 1, ch + 1, (int) (dip * 255) << 24);
        g.pose().popPose();
    }

    /** How black the screen is, 0-1: each scene fades in from and out to black, over its own lengths. */
    private float dip(Step step, long t) {
        long in, out;
        switch (step.scene()) {
            case OMEN, BURN_OUT -> { in = 320; out = 100; }
            case RARITY -> { in = 0; out = 120; }
            case STARS -> { in = 90; out = 140; }
            case EPITHET -> { in = 220; out = 160; }
            case NAME -> { in = 0; out = reveal.birth() ? 160 : 0; }
            case MALICE -> { in = 140; out = 180; }
            case HEAVEN -> { in = 500; out = 320; }
            case BORN_WITHOUT -> { in = 320; out = 260; }
            case SIX_EYES -> { in = 240; out = 300; }
            default -> {
                // Back to the card from a later scene.
                if (resultStartMillis < 0 || previousIsName()) return 0f;
                return 1f - clamp01((now() - resultStartMillis) / 220f);
            }
        }
        long age = step.age(t), left = step.end() - t;
        float fadeIn = in <= 0 ? 0f : 1f - clamp01(age / (float) in);
        float fadeOut = out <= 0 ? 0f : 1f - clamp01(left / (float) out);
        return Math.max(fadeIn, fadeOut);
    }

    /** Whether the name scene runs straight into the result, with no cut between them. */
    private boolean previousIsName() {
        List<Step> steps = script.steps();
        return steps.size() >= 2 && steps.get(steps.size() - 2).scene() == Scene.NAME;
    }

    private void renderHint(GuiGraphics g, long t, long now) {
        boolean end = atEnd(t);
        float alpha;
        if (end) {
            alpha = 0.55f + 0.25f * Mth.sin((now % 2400L) / 2400f * Mth.TWO_PI);
        } else {
            alpha = 0.4f * clamp01((t - 600L) / 500f);
        }
        if (alpha <= 0.02f) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_HINT);
        Component hint = Component.translatable(end ? "gui.kegare.reveal.continue" : "gui.kegare.reveal.skip");
        FormattedCharSequence seq = hint.getVisualOrderText();
        g.drawString(font, seq, centerX - font.width(seq) / 2, ch - 11, color(0xC8C2D0, alpha), true);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- Omen

    private void renderOmen(GuiGraphics g, Step step, long t, float dt) {
        darken(g, 0xB8);
        float p = step.progress(t);
        InnateTechniqueRarity shown = RevealScript.omenFlame(rarity, fakeOut, p);
        int rgb = shown.color();
        if (fakeOut && p >= RevealScript.FAKE_OUT_AT) {
            float flare = clamp01((p - RevealScript.FAKE_OUT_AT) / 0.05f);
            rgb = GuiDraw.mix(InnateTechniqueRarity.EPIC.color(), rarity.color(), flare);
            if (cues == 0) {
                cues = 1;
                long now = now();
                flash(GuiDraw.mix(rarity.color(), 0xFFFFFF, 0.5f), 0.85f, 420, now);
                shake(3f, 360, now);
                play(SoundEvents.FIRECHARGE_USE, 0.7f, 1f);
                play(SoundEvents.BLAZE_SHOOT, 0.7f, 0.8f);
            }
        }
        float heat = switch (rarity) {
            case COMMON -> 0.72f;
            case RARE -> 0.86f;
            case EPIC -> 1f;
            case LEGENDARY -> 1.14f;
        };
        // A flare, for the fake-out; and everything flares as the talisman finishes, into the rarity's slam.
        float flare = 0f;
        if (fakeOut) flare = Math.max(flare, 0.55f * (1f - clamp01(Math.abs(p - RevealScript.FAKE_OUT_AT - 0.05f) / 0.12f)));
        flare = Math.max(flare, 0.5f * clamp01((p - 0.86f) / 0.14f));
        float burn = clamp01((p - 0.05f) / 0.87f);
        burn = burn * burn * (3f - 2f * burn) * 0.25f + burn * 0.75f;
        float size = heat * clamp01(p / 0.14f) * (1f + flare);
        renderTalisman(g, burn, rgb, size, 1f, t, dt, heat * (1f + flare));
    }

    private void renderBurnOut(GuiGraphics g, Step step, long t, float dt) {
        darken(g, 0xB8);
        float p = step.progress(t);
        float burn = clamp01((p - 0.04f) / 0.62f);
        float dying = 1f - clamp01((p - 0.42f) / 0.3f);
        float size = 0.85f * clamp01(p / 0.12f) * dying;
        int rgb = GuiDraw.mix(SEVERED_GREY, 0x44444A, 1f - dying);
        renderTalisman(g, burn, rgb, size, 1f - 0.55f * clamp01(p / 0.5f), t, dt, 0.8f * dying);

        // What it leaves.
        float ash = clamp01((p - 0.52f) / 0.26f);
        int q = omenScale();
        int ty = (ch - TALISMAN_H * q) / 2;
        int w = ASH_W * q, h = ASH_H * q;
        if (ash > 0f) {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, ash);
            g.blitSprite(ASH, centerX - w / 2, ty + TALISMAN_H * q - h, w, h);
            g.setColor(1f, 1f, 1f, 1f);
        }
        if (p >= 0.68f && cues == 0) {
            cues = 1;
            play(SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.8f);
        }
        spawnAshFlakes(dt, 10f * clamp01((p - 0.3f) / 0.3f));
    }

    private int omenScale() {
        return ch >= 340 ? 3 : 2;
    }

    /**
     * The talisman, burning from the bottom up: it's drawn a column at a time, each cut off at the burn line, with charred paper and a line
     * of embers along the cut and the flame standing on it.
     *
     * @param burn  how much has burned, 0-1
     * @param size  the flame's size (1 = about the talisman's width)
     * @param paper how bright the paper is
     */
    private void renderTalisman(GuiGraphics g, float burn, int rgb, float size, float paper, long t, float dt, float heat) {
        int q = omenScale();
        int tw = TALISMAN_W * q, th = TALISMAN_H * q;
        int x = centerX - tw / 2;
        // It sinks as it burns, which keeps the fire near the middle of the screen.
        int y = (ch - th) / 2 + Math.round(th * 0.46f * burn) + Math.round(Mth.sin(t * 0.0021f) * q);
        int front = y + Math.round(th * (1f - burn));

        if (size > 0.01f) glow(g, centerX, front, Math.round(tw * 3.4f * Math.max(0.5f, size)), rgb, 0.5f * Math.min(1f, size));

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(q, q, 1f);
        RenderSystem.enableBlend();
        g.setColor(paper, paper, paper, 1f);
        float jag = Math.min(1f, burn * 6f) * Math.min(1f, (1f - burn) * 6f);
        int[] keep = new int[TALISMAN_W];
        for (int c = 0; c < TALISMAN_W; c++) {
            keep[c] = Mth.clamp(Math.round(TALISMAN_H * (1f - burn) + burnEdge[c] * jag), 0, TALISMAN_H);
            if (keep[c] > 0) g.blitSprite(TALISMAN, TALISMAN_W, TALISMAN_H, c, 0, c, 0, 1, keep[c]);
        }
        g.setColor(1f, 1f, 1f, 1f);
        if (burn > 0f) {
            int hot = color(GuiDraw.mix(rgb, 0xFFFFFF, 0.45f), 1f);
            for (int c = 2; c < TALISMAN_W - 2; c++) {
                int k = keep[c];
                if (k <= 0) continue;
                g.fill(c, Math.max(0, k - 5), c + 1, k, 0x70180A08);
                g.fill(c, Math.max(0, k - 3), c + 1, k, 0xB0100806);
                g.fill(c, k - 1, c + 1, k, hot);
            }
        }
        g.pose().popPose();

        if (size > 0.01f) {
            int fw = Math.round(tw * 1.25f * size);
            int fh = Math.round(fw * 64f / 48f * Mth.lerp(burn, 1f, 0.72f));
            int frame = (int) ((t / 70L) % FLAMES.length);
            int base = front + fh / 7;
            // Blended normally, or the bright paper behind it would burn the rarity's color out to white. The glow goes on top.
            GuiDraw.tinted(g, FLAMES[frame], centerX - fw / 2, base - fh, fw, fh, GuiDraw.mix(rgb, 0xFFFFFF, 0.12f), 0.96f);
            int coreW = Math.round(fw * 0.5f), coreH = Math.round(fh * 0.46f);
            GuiDraw.tinted(g, FLAMES[(frame + 3) % FLAMES.length], centerX - coreW / 2, base - coreH, coreW, coreH, GuiDraw.mix(rgb, 0xFFFFFF, 0.6f), 0.85f);
            additive(true);
            GuiDraw.tinted(g, FLAMES[(frame + 5) % FLAMES.length], centerX - fw / 2, base - fh, fw, fh, rgb, 0.22f);
            additive(false);
        }

        for (float budget = dt * 46f * heat; budget > 0f; budget -= 1f) {
            if (random.nextFloat() >= budget) continue;
            float ex = x + random.nextFloat() * tw;
            int color = GuiDraw.mix(rgb, 0xFFFFFF, random.nextFloat() * 0.6f);
            particles.ember(ex, front + random.nextFloat() * 6f - 3f, (random.nextFloat() - 0.5f) * 22f, -18f - random.nextFloat() * 56f, -6f,
                    0.5f + random.nextFloat() * 0.8f, 1 + random.nextInt(2), color);
        }
    }

    /** Ash drifting down. It's too slow to cross the screen within a scene, so flakes appear all over it and fade in where they are. */
    private void spawnAshFlakes(float dt, float perSecond) {
        for (float budget = dt * perSecond; budget > 0f; budget -= 1f) {
            if (random.nextFloat() >= budget) continue;
            particles.flake(centerX + (random.nextFloat() - 0.5f) * cw * 0.8f, random.nextFloat() * ch, (random.nextFloat() - 0.5f) * 9f, 10f + random.nextFloat() * 16f,
                    1.8f + random.nextFloat() * 1.8f, 1 + random.nextInt(2), GuiDraw.mix(0x55555C, 0xB4B4BD, random.nextFloat()));
        }
    }

    // ---------------------------------------------------------------- Rarity, Stars, Epithet

    private void renderRarity(GuiGraphics g, Step step, long t, long now) {
        darken(g, 0xC8);
        int rgb = rarity.color();
        long age = step.age(t);
        Component word = Component.translatable(rarity.translationKey());
        int s = RevealLayout.textScale(font.width(word), cw - 48, ch >= 340 ? 6 : 5);
        float slam = phase(age, 0, SLAM_MS);
        if (slam >= 1f && cues == 0) {
            cues = 1;
            play(SoundEvents.MACE_SMASH_GROUND, 1.25f - rarity.ordinal() * 0.17f, 0.85f);
            if (rarity == InnateTechniqueRarity.LEGENDARY) play(SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.3f);
            shake(rarity == InnateTechniqueRarity.COMMON ? 1.5f : 2f + rarity.ordinal() * 0.7f, 280, now);
            flash(rgb, 0.3f + rarity.ordinal() * 0.1f, 200, now);
            ring(centerX, centerY, font.width(word) * s * 1.7f, rgb, now);
            particles.burst(centerX, centerY, 16 + rarity.ordinal() * 12, 40f, 150f, rgb);
        }
        float pulse = 0.5f + 0.5f * Mth.sin(age * 0.004f);
        if (slam >= 1f) glow(g, centerX, centerY, font.width(word) * s * 2, rgb, 0.26f + 0.08f * pulse);
        float scale = s * Mth.lerp(slam * slam, 2.6f, 1f);
        centeredText(g, word.getVisualOrderText(), centerX, centerY, scale, rgb, phase(age, 0, 70));
        if (slam >= 1f) {
            float echo = phase(age, SLAM_MS, 340);
            if (echo < 1f) centeredText(g, word.getVisualOrderText(), centerX, centerY, s * (1f + 0.7f * outCubic(echo)), rgb, (1f - echo) * 0.36f);
            int w = Math.round((font.width(word) * s + 40) * outCubic(phase(age, SLAM_MS, 260)));
            hairline(g, w, centerY + font.lineHeight * s / 2 + 5, rgb, 1f);
        }
    }

    private void renderStars(GuiGraphics g, Step step, long t, long now) {
        darken(g, 0xC8);
        int rgb = rarity.color();
        long age = step.age(t);
        Component word = Component.translatable(rarity.translationKey());
        int n = RevealScript.stars(rarity);
        int size = ch >= 340 ? 48 : 32;
        int gap = size / 4;
        int step2 = size + gap;
        int x0 = RevealLayout.rowStart(n, size, step2, centerX);
        int restY = centerY - size / 2 + 6;
        centeredText(g, word.getVisualOrderText(), centerX, restY - 18, 2f, rgb, 1f);
        long interval = (long) (step.length() * 0.74f / n);
        glow(g, centerX, restY + size / 2, n * step2 + size * 2, rgb, 0.16f);
        for (int i = 0; i < n; i++) {
            int x = x0 + i * step2;
            tinted(g, STAR, x, restY, size, size, 0x2B2433, 0.7f); // the empty place it lands in
            long a = age - i * interval;
            if (a < 0) continue;
            float drop = phase(a, 0, STAR_DROP_MS);
            if (drop >= 1f && cues <= i) {
                cues = i + 1;
                play(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.95f + i * 0.24f, 0.9f);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f + i * 0.2f, 0.7f);
                particles.burst(x + size / 2f, restY + size / 2f, 12 + i * 3, 25f, 95f, rgb);
                shake(1f + i * 0.35f, 130, now);
                if (i == n - 1) ring(centerX, restY + size / 2f, n * step2 + size, rgb, now);
            }
            float settle = phase(a, STAR_DROP_MS, 170);
            float squash = settle < 1f ? Mth.sin(settle * Mth.PI) * 0.2f : 0f;
            int y = restY - Math.round((1f - drop * drop) * (restY + size + 8));
            int w = Math.round(size * (1f + squash)), h = Math.round(size * (1f - squash));
            if (drop >= 1f) glow(g, x + size / 2, restY + size / 2, size * 2, rgb, 0.34f * (1f - 0.6f * settle));
            tinted(g, STAR, x + (size - w) / 2, y + size - h, w, h, rgb, 1f);
        }
    }

    private void renderEpithet(GuiGraphics g, Step step, long t) {
        darken(g, 0xE6);
        long age = step.age(t);
        String full = result.epithet().getString();
        int scale = RevealLayout.textScale(font.width(full), cw - 32, ch >= 340 ? 3 : 2);
        long typing = (long) (step.length() * 0.68f);
        int chars = Math.round(full.length() * phase(age, 200, typing));
        if (chars > cues) {
            if (!Character.isWhitespace(full.charAt(chars - 1))) {
                play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.5f + random.nextFloat() * 0.35f, 0.16f);
            }
            cues = chars;
        }
        // Left-aligned from where the finished line starts, so it doesn't creep sideways as it types.
        int x = centerX - font.width(full) * scale / 2;
        int y = centerY - font.lineHeight * scale / 2;
        String typed = full.substring(0, chars);
        g.pose().pushPose();
        g.pose().translate(x, y, Z_TEXT);
        g.pose().scale(scale, scale, 1f);
        int ink = 0xFFE9E4F0;
        g.drawString(font, typed, 0, 0, ink, true);
        boolean typingNow = chars < full.length();
        if (typingNow || (age / 280L) % 2 == 0) {
            int cx = font.width(typed) + 1;
            g.fill(cx, 0, cx + 1, font.lineHeight - 1, ink);
        }
        g.pose().popPose();
        float finished = phase(age, 200 + typing, 320);
        if (finished > 0f) {
            hairline(g, Math.round((font.width(full) * scale + 24) * outCubic(finished)), y + font.lineHeight * scale + 6, rarity.color(), finished);
        }
    }

    // ---------------------------------------------------------------- the name card

    private void renderResult(GuiGraphics g, long now, float dt, float partialTick) {
        if (result != null) {
            renderCard(g, Scene.RESULT, now, dt, partialTick);
        } else if (reveal.severed()) {
            renderSeveredResult(g, dt);
        } else {
            renderNothing(g);
        }
    }

    /** The technique's name card: {@link Scene#NAME} plays it in, {@link Scene#RESULT} holds it with the extra lines. */
    private void renderCard(GuiGraphics g, Scene scene, long now, float dt, float partialTick) {
        darken(g, 0x96);
        long age = cardStartMillis < 0 ? 60_000L : now - cardStartMillis;
        long nameLength = nameLength();
        int rgb = rarity.color();
        Card c = card;
        int emblemCy = c.emblemY() + c.emblem() / 2;

        if (rarity == InnateTechniqueRarity.LEGENDARY) {
            float raysIn = outCubic(phase(age, 0, 500));
            renderRays(g, centerX, emblemCy, Math.max(cw, ch) * 0.75f * raysIn, 0xFFC85A, 0.62f * raysIn, now);
        }
        glow(g, centerX, emblemCy, Math.round(c.emblem() * 2.6f), result.maliceColor(), 0.5f * phase(age, 0, 300));
        glow(g, centerX, emblemCy, Math.round(c.emblem() * 2f), rgb, 0.24f * phase(age, 0, 300));

        // The emblem, behind the name, settling back as it fades up.
        float emblemIn = phase(age, 40, 340);
        if (emblemIn > 0f) {
            float zoom = 1f + 0.14f * (1f - outCubic(emblemIn));
            g.pose().pushPose();
            g.pose().translate(centerX, emblemCy, 0);
            g.pose().scale(zoom, zoom, 1f);
            g.pose().translate(-centerX, -emblemCy, 0);
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, emblemIn);
            GuiDraw.framedEmblem(g, result, centerX - c.emblem() / 2, c.emblemY(), c.emblem());
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }

        if (age >= 120) renderModel(g, c, partialTick);
        tickSignature(now);
        emitCardParticles(c, dt);

        // A dark band so the name reads across the art.
        float nameIn = phase(age, 0, 150);
        int nameH = font.lineHeight * c.nameScale();
        int nameW = font.width(result.displayName()) * c.nameScale();
        darkBand(g, nameW, c.nameY() - 3, nameH + 5, nameIn);
        float nameZoom = 1f + 0.5f * (1f - outCubic(nameIn));
        centeredText(g, result.displayName().getVisualOrderText(), centerX, c.nameY() + nameH / 2f, c.nameScale() * nameZoom, rgb, nameIn);

        // The details fade in once the name has landed. A skipped-to card has them already.
        long detailFrom = (long) (nameLength * 0.3f);
        float details = phase(age, detailFrom, Math.max(300L, nameLength - detailFrom));
        renderStarRow(g, c, details);
        int y = c.descY();
        if (details > 0f) {
            g.pose().pushPose();
            g.pose().translate(0, 0, Z_TEXT);
            for (FormattedCharSequence line : c.desc()) {
                g.drawString(font, line, centerX - font.width(line) / 2, y, color(0xBDB7C6, details), true);
                y += font.lineHeight + 1;
            }
            g.pose().popPose();
        }
        renderIcons(g, c, details);

        float title = phase(age, detailFrom, 400);
        if (title > 0f) centeredText(g, title(), centerX, 8, 1f, 0x8E8899, title);
        if (scene == Scene.RESULT) {
            float extras = resultStartMillis < 0 ? 1f : clamp01((now - resultStartMillis) / 300f);
            renderExtraLines(g, c.linesY(), extras);
        }
    }

    private FormattedCharSequence title() {
        return title.getVisualOrderText();
    }

    private long nameLength() {
        for (Step step : script.steps()) if (step.scene() == Scene.NAME) return step.length();
        return 1000L;
    }

    private void renderRays(GuiGraphics g, int x, int y, float length, int rgb, float alpha, long now) {
        if (length < 4f) return;
        additive(true);
        float spin = reducedMotion() ? 0f : (now % 90_000L) / 90_000f * Mth.TWO_PI;
        int rays = 14;
        for (int i = 0; i < rays; i++) {
            float wave = 0.5f + 0.5f * Mth.sin(i * 2.4f + now * 0.0011f);
            int len = Math.round(length * (0.72f + 0.28f * wave));
            int w = Math.round(len * 0.24f);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotation(spin + i * Mth.TWO_PI / rays));
            GuiDraw.tinted(g, RAY, -w / 2, 0, w, len, rgb, alpha * (0.35f + 0.65f * wave));
            g.pose().popPose();
        }
        additive(false);
    }

    /** The name card's "stars and rarity" row. */
    private void renderStarRow(GuiGraphics g, Card c, float alpha) {
        if (alpha <= 0f) return;
        int rgb = rarity.color();
        int n = RevealScript.stars(rarity);
        Component rarityName = Component.translatable(rarity.translationKey()).withColor(rgb);
        Component line = reveal.byHardPity() ? Component.translatable("gui.kegare.reveal.hard_pity", rarityName, reveal.rollNumber()) : rarityName;
        int textW = font.width(line);
        int rowW = n * 10 + 4 + textW;
        int x = centerX - rowW / 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_DETAILS);
        for (int i = 0; i < n; i++) tinted(g, STAR_SMALL, x + i * 10, c.starsY() + 1, 9, 9, rgb, alpha);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_TEXT);
        g.drawString(font, line.getVisualOrderText(), x + n * 10 + 4, c.starsY() + 2, color(0xFFFFFF, alpha), true);
        g.pose().popPose();
    }

    /** The technique's ability icons, one after another. */
    private void renderIcons(GuiGraphics g, Card c, float alpha) {
        List<Ability> abilities = result.abilities();
        int n = abilities.size();
        int slot = CombatPanelLayer.SLOT_SIZE;
        int x0 = RevealLayout.rowStart(n, slot, c.iconStep(), centerX);
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_DETAILS);
        for (int i = 0; i < n; i++) {
            float a = clamp01(alpha * (n + 2f) - i);
            if (a <= 0f) continue;
            Ability ability = abilities.get(i);
            int x = x0 + i * c.iconStep();
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, a);
            CombatPanelLayer.slotFrame(g, x, c.iconsY(), false);
            if (!GuiDraw.abilityIcon(g, ability, x + 2, c.iconsY() + 2)) {
                g.setColor(1f, 1f, 1f, 1f);
                g.drawCenteredString(font, ability.displayName().getString().substring(0, 1), x + slot / 2, c.iconsY() + 6, color(0xFFFFFF, a));
            }
            g.setColor(1f, 1f, 1f, 1f);
        }
        g.pose().popPose();
    }

    /** Under the icons: Malice on a birth, otherwise why this roll happened; then Six Eyes' lines and any other trait. */
    private int renderExtraLines(GuiGraphics g, int y, float alpha) {
        if (alpha <= 0f) return y;
        int line = font.lineHeight + 1;
        if (reveal.birth()) {
            Component tier = Component.translatable(tier().translationKey());
            lineAt(g, Component.translatable("gui.kegare.reveal.malice", reveal.capacity(), tier), y, lifted(maliceColor()), alpha);
            y += line;
        } else {
            lineAt(g, Component.translatable("gui.kegare.reveal.subtitle." + reveal.reason().name().toLowerCase(Locale.ROOT)), y, 0x8E8899, alpha);
            y += line;
        }
        if (sixEyes) {
            lineAt(g, Component.translatable("gui.kegare.reveal.six_eyes"), y, GuiDraw.AQUA & 0xFFFFFF, alpha);
            y += line;
            lineAt(g, Component.translatable("gui.kegare.reveal.trait_private"), y, 0x8E8899, alpha);
            y += line;
        }
        for (Trait trait : listedTraits) {
            lineAt(g, Component.translatable("gui.kegare.reveal.trait", trait.displayName()), y, GuiDraw.AQUA & 0xFFFFFF, alpha);
            y += line;
            if (trait.isUnique()) {
                lineAt(g, Component.translatable("gui.kegare.reveal.trait_private"), y, 0x8E8899, alpha);
                y += line;
            }
        }
        return y;
    }

    private void lineAt(GuiGraphics g, Component text, int y, int rgb, float alpha) {
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_TEXT);
        FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font, seq, centerX - font.width(seq) / 2, y, color(rgb, alpha), true);
        g.pose().popPose();
    }

    private MaliceTier tier() {
        return MaliceTier.values()[Mth.clamp(reveal.tier(), 0, MaliceTier.values().length - 1)];
    }

    private int maliceColor() {
        return result != null ? result.maliceColor() : InnateTechnique.DEFAULT_MALICE_COLOR;
    }

    /** Sparkles rise past the emblem, in the technique's Malice color. Some are paler, so a dark color (Rime's) still shows on the dark background. */
    private void emitCardParticles(Card c, float dt) {
        if (rarity == null) return;
        float perSecond = 14f + rarity.ordinal() * 10f;
        int rgb = result.maliceColor();
        for (float budget = dt * perSecond; budget > 0f; budget -= 1f) {
            if (random.nextFloat() >= budget) continue;
            float x = centerX + (random.nextFloat() - 0.5f) * c.emblem() * 2f;
            float y = c.emblemY() + c.emblem() * (0.6f + random.nextFloat() * 0.5f);
            int color = random.nextFloat() < 0.5f ? rgb : maliceParticleColor();
            particles.sparkle(x, y, (random.nextFloat() - 0.5f) * 10f, -12f - random.nextFloat() * 26f, -3f, 0.8f + random.nextFloat() * 1.2f,
                    3 + random.nextInt(4), color);
        }
    }

    private int maliceParticleColor() {
        return GuiDraw.mix(maliceColor(), 0xFFFFFF, 0.42f);
    }

    // ---------------------------------------------------------------- the player model

    /** The local player beside the emblem, at a real partial tick: vanilla's GUI entity call always passes 1, which steps PAL's animation at 20 fps. */
    private void renderModel(GuiGraphics g, Card c, float partialTick) {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) return;
        int scale = Math.round(c.emblem() / 2.05f);
        int x = centerX - c.emblem() / 2 - Math.round(scale * 0.98f) - 2;
        int feetY = c.emblemY() + c.emblem() - Math.round(c.emblem() * 0.02f);
        // Facing the emblem, a little toward the viewer. 180 faces the screen.
        float body = 180f - 28f, look = 180f - 40f;
        float yBody = player.yBodyRot, yBodyO = player.yBodyRotO, yaw = player.getYRot(), yawO = player.yRotO;
        float pitch = player.getXRot(), pitchO = player.xRotO, head = player.yHeadRot, headO = player.yHeadRotO;
        player.yBodyRot = player.yBodyRotO = body;
        player.setYRot(look);
        player.yRotO = look;
        player.setXRot(4f);
        player.xRotO = 4f;
        player.yHeadRot = player.yHeadRotO = look;

        Quaternionf upright = new Quaternionf().rotateZ(Mth.PI);
        Quaternionf camera = new Quaternionf();
        g.pose().pushPose();
        g.pose().translate(x, feetY, 50);
        g.pose().scale(scale, scale, -scale);
        g.pose().mulPose(upright);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.overrideCameraOrientation(camera.conjugate(new Quaternionf()).rotateY(Mth.PI));
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(player, 0.0, 0.0, 0.0, 0f, partialTick, g.pose(), g.bufferSource(), 0xF000F0));
        g.flush();
        dispatcher.setRenderShadow(true);
        g.pose().popPose();
        Lighting.setupFor3DItems();

        player.yBodyRot = yBody;
        player.yBodyRotO = yBodyO;
        player.setYRot(yaw);
        player.yRotO = yawO;
        player.setXRot(pitch);
        player.xRotO = pitchO;
        player.yHeadRot = head;
        player.yHeadRotO = headO;
    }

    /** Plays the technique's signature animation on the model every few seconds. PAL draws it because the model is the real local player. */
    private void tickSignature(long now) {
        if (!animated || result == null || minecraft == null || minecraft.player == null) return;
        if (now < nextSignatureMillis) return;
        result.signatureAnimation().ifPresent(animation -> {
            nextSignatureMillis = now + SIGNATURE_EVERY_MS;
            playingSignature = animation;
            PlayerAnimations.play(minecraft.player, animation, false);
        });
    }

    private void stopSignature() {
        if (playingSignature != null && minecraft != null && minecraft.player != null) PlayerAnimations.stop(minecraft.player, playingSignature);
        playingSignature = null;
    }

    // ---------------------------------------------------------------- Malice, Six Eyes, Severed

    private void renderMalice(GuiGraphics g, Step step, long t, long now) {
        darken(g, 0xC8);
        long age = step.age(t);
        int rgb = lifted(maliceColor());
        float count = outCubic(phase(age, 120, (long) (step.length() * 0.52f)));
        int shown = Math.round(reveal.capacity() * count);
        String finalText = Integer.toString(reveal.capacity());
        int s = RevealLayout.textScale(font.width(finalText), cw - 48, ch >= 340 ? 6 : 5);
        int y = centerY - 8;
        glow(g, centerX, y, Math.max(120, font.width(finalText) * s * 2), maliceColor(), 0.5f * phase(age, 0, 400));
        centeredText(g, Component.translatable("kegare.term.malice").getVisualOrderText(), centerX, y - font.lineHeight * s / 2f - 12, 1f, 0xBDB7C6, phase(age, 0, 200));

        int ticks = (int) (age / 70L);
        if (count < 1f && ticks > cues) {
            cues = ticks;
            play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f + count * 0.8f, 0.22f);
        }
        centeredText(g, Component.literal(Integer.toString(shown)).getVisualOrderText(), centerX, y, s, rgb, 1f);

        float tierIn = phase(age, (long) (step.length() * 0.58f), SLAM_MS);
        boolean vast = tier() == MaliceTier.VAST;
        if (tierIn > 0f) {
            if (tierIn >= 1f && cues < 100_000) {
                cues = 100_000;
                play(vast ? SoundEvents.BELL_RESONATE : SoundEvents.AMETHYST_BLOCK_RESONATE, vast ? 0.8f : 1.1f, 1f);
                if (vast) {
                    play(SoundEvents.BEACON_POWER_SELECT, 0.7f, 1f);
                    flash(GuiDraw.mix(InnateTechniqueRarity.LEGENDARY.color(), 0xFFFFFF, 0.5f), 0.5f, 320, now);
                    shake(3f, 300, now);
                }
                ring(centerX, y, Math.max(120, font.width(finalText) * s * 2.2f), rgb, now);
                particles.burst(centerX, y, vast ? 60 : 22, 30f, 130f, maliceParticleColor());
            }
            Component word = Component.translatable(tier().translationKey());
            float scale = 2f * Mth.lerp(tierIn * tierIn, 2f, 1f);
            centeredText(g, word.getVisualOrderText(), centerX, y + font.lineHeight * s / 2f + 13, scale, vast ? GuiDraw.GOLD & 0xFFFFFF : 0xF0ECF5, tierIn);
        }
    }

    private void renderSixEyes(GuiGraphics g, Step step, long t, float dt) {
        darken(g, 0xEA);
        long age = step.age(t);
        int q = ch >= 340 ? 4 : 3;
        int w = EYE_W * q, h = EYE_H * q;
        int y = centerY - h / 2 - 20;
        float open = outCubic(phase(age, 180, 900));
        int frame = Mth.clamp(Math.round(open * (EYE.length - 1)), 0, EYE.length - 1);
        float pulse = 0.85f + 0.15f * Mth.sin(age * 0.0036f);
        glow(g, centerX, y + h / 2, Math.round(w * (0.9f + 0.9f * open)), SIX_EYES_BLUE, (0.12f + 0.5f * open) * pulse);
        sprite(g, EYE[frame], centerX - w / 2, y, w, h);
        if (open >= 1f && cues == 0) {
            cues = 1;
            play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 0.9f);
            ring(centerX, y + h / 2f, w * 1.4f, SIX_EYES_BLUE, now());
        }
        for (float budget = dt * 14f * open; budget > 0f; budget -= 1f) {
            if (random.nextFloat() >= budget) continue;
            particles.sparkle(centerX + (random.nextFloat() - 0.5f) * w, y + random.nextFloat() * h, (random.nextFloat() - 0.5f) * 14f,
                    -6f - random.nextFloat() * 12f, 0f, 0.7f + random.nextFloat(), 3 + random.nextInt(3), GuiDraw.mix(SIX_EYES_BLUE, 0xFFFFFF, random.nextFloat() * 0.6f));
        }
        Component words = Component.translatable("gui.kegare.reveal.six_eyes");
        int scale = RevealLayout.textScale(font.width(words), cw - 32, ch >= 340 ? 3 : 2);
        float first = phase(age, 1200, 500);
        centeredText(g, words.getVisualOrderText(), centerX, y + h + 12 + font.lineHeight * scale / 2f, scale, GuiDraw.AQUA & 0xFFFFFF, first);
        float second = phase(age, 2300, 500);
        centeredText(g, Component.translatable("gui.kegare.reveal.trait_private").getVisualOrderText(), centerX,
                y + h + 16 + font.lineHeight * scale + font.lineHeight / 2f + 2, 1f, 0xA8A2B2, second);
    }

    /** A single line of text on black, as ash falls past it. */
    private void renderLine(GuiGraphics g, float dt, Component text, int rgb) {
        darken(g, 0xE6);
        spawnAshFlakes(dt, 10f);
        int scale = RevealLayout.textScale(font.width(text), cw - 32, ch >= 340 ? 4 : 3);
        centeredText(g, text.getVisualOrderText(), centerX, centerY, scale, rgb, 1f);
    }

    private void renderSeveredResult(GuiGraphics g, float dt) {
        darken(g, 0xC0);
        spawnAshFlakes(dt, 6f);
        int emblem = ch >= 340 ? 96 : 48;
        Component heading = Component.translatable("gui.kegare.reveal.severed.title");
        int scale = RevealLayout.textScale(font.width(heading), cw - 32, 2);
        List<FormattedCharSequence> desc = font.split(Component.translatable("gui.kegare.reveal.severed.desc"), Math.min(cw - 40, 300));
        int line = font.lineHeight + 1;
        int traits = 0;
        for (Trait trait : listedTraits) traits += (trait.isUnique() ? 2 : 1) * line;
        int total = emblem + 6 + line + font.lineHeight * scale + 4 + line + 4 + desc.size() * line + (traits > 0 ? 4 + traits : 0);
        int y = Math.max(14, (ch - total) / 2);
        centeredText(g, title(), centerX, 8, 1f, 0x8E8899, 1f);
        sprite(g, GuiDraw.SEVERED_EMBLEM, centerX - emblem / 2, y, emblem, emblem);
        y += emblem + 6;
        lineAt(g, Component.translatable("gui.kegare.reveal.severed.heaven"), y, 0xA8A8B0, 1f);
        y += line;
        centeredText(g, heading.getVisualOrderText(), centerX, y + font.lineHeight * scale / 2f, scale, 0xE6E6E6, 1f);
        y += font.lineHeight * scale + 4;
        lineAt(g, Component.translatable("gui.kegare.reveal.severed.subtitle"), y, GuiDraw.RED & 0xFFFFFF, 1f);
        y += line + 4;
        for (FormattedCharSequence text : desc) {
            g.pose().pushPose();
            g.pose().translate(0, 0, Z_TEXT);
            g.drawString(font, text, centerX - font.width(text) / 2, y, color(0xAAAAAA, 1f), true);
            g.pose().popPose();
            y += line;
        }
        y += 4;
        for (Trait trait : listedTraits) {
            lineAt(g, Component.translatable("gui.kegare.reveal.trait", trait.displayName()), y, GuiDraw.AQUA & 0xFFFFFF, 1f);
            y += line;
            if (trait.isUnique()) {
                lineAt(g, Component.translatable("gui.kegare.reveal.trait_private"), y, 0x8E8899, 1f);
                y += line;
            }
        }
    }

    /** Nothing was eligible to roll: only what's still true (Malice, traits). */
    private void renderNothing(GuiGraphics g) {
        darken(g, 0xC0);
        centeredText(g, title(), centerX, 8, 1f, 0x8E8899, 1f);
        int y = centerY - 16;
        y += GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.reveal.no_innate_technique"), centerX, y, Math.min(cw - 40, 300), GuiDraw.GRAY) + 6;
        renderExtraLines(g, y, 1f);
    }

    // ---------------------------------------------------------------- drawing helpers

    /** Dims the ink background, nearly to black in the darkest scenes. */
    private void darken(GuiGraphics g, int alpha) {
        g.fill(0, 0, cw + 1, ch + 1, alpha << 24 | 0x030206);
    }

    /** A centered one-pixel line, brightest in the middle and fading out toward both ends. */
    private void hairline(GuiGraphics g, int w, int y, int rgb, float alpha) {
        if (w < 4 || alpha <= 0f) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_DETAILS);
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            int half = w / 2 - i * w / (2 * steps);
            g.fill(centerX - half, y, centerX + half, y + 1, ((int) (alpha * 90) << 24) | (rgb & 0xFFFFFF));
        }
        g.pose().popPose();
    }

    /** Darker in the middle, thinning out at both ends. */
    private void darkBand(GuiGraphics g, int textWidth, int y, int h, float alpha) {
        if (alpha <= 0f) return;
        int half = Math.min(cw / 2, textWidth / 2 + 28);
        int steps = 7;
        int perStep = (int) (alpha * 34);
        g.pose().pushPose();
        g.pose().translate(0, 0, Z_BAND);
        for (int i = 0; i < steps; i++) {
            int inset = i * 4;
            g.fill(centerX - half + inset, y, centerX + half - inset, y + h, perStep << 24 | 0x05030A);
        }
        g.pose().popPose();
    }

    /** Text centered on a point, at any scale. Whole-number scales at a whole-number point stay crisp. */
    private void centeredText(GuiGraphics g, FormattedCharSequence text, float x, float y, float scale, int rgb, float alpha) {
        if (alpha <= 0.02f) return; // Font treats near-zero alpha as opaque
        g.pose().pushPose();
        g.pose().translate(Math.round(x), Math.round(y), Z_TEXT);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, -font.lineHeight / 2, color(rgb, alpha), true);
        g.pose().popPose();
    }

    private static int color(int rgb, float alpha) {
        int a = Math.max(4, Math.round(clamp01(alpha) * 255));
        return a << 24 | (rgb & 0xFFFFFF);
    }

    /** A color light enough to read as text on near-black: a dark Malice color (Rime's) is paled until it does. */
    private static int lifted(int rgb) {
        int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float luma = (0.3f * r + 0.59f * gr + 0.11f * b) / 255f;
        return luma >= 0.55f ? rgb : GuiDraw.mix(rgb, 0xFFFFFF, Mth.clamp((0.55f - luma) / (1f - luma), 0f, 1f));
    }

    private void glow(GuiGraphics g, int x, int y, int size, int rgb, float alpha) {
        if (alpha <= 0f || size <= 0) return;
        additive(true);
        GuiDraw.tinted(g, GLOW, x - size / 2, y - size / 2, size, size, rgb, alpha);
        additive(false);
    }

    private static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        GuiDraw.sprite(g, sprite, x, y, w, h);
    }

    private static void tinted(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, int rgb, float alpha) {
        GuiDraw.tinted(g, sprite, x, y, w, h, rgb, alpha);
    }

    private static void additive(boolean on) {
        RenderSystem.enableBlend();
        if (on) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0f, 1f);
    }

    /** 0 before {@code from}, 1 after {@code from + length}. */
    private static float phase(long age, long from, long length) {
        return length <= 0 ? (age >= from ? 1f : 0f) : clamp01((age - from) / (float) length);
    }

    private static float outCubic(float f) {
        float inv = 1f - f;
        return 1f - inv * inv * inv;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
