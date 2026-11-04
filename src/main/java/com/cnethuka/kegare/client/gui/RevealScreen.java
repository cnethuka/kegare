package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.config.KegareClientConfig;
import com.cnethuka.kegare.config.KegareServerConfig;
import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.innatetechnique.InnateTechniqueRarity;
import com.cnethuka.kegare.malice.MaliceTier;
import com.cnethuka.kegare.network.RevealPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import com.cnethuka.kegare.trait.Trait;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * Gacha-style reveal of a roll the server has ALREADY decided and saved (Awakening, Reawakening, admin rerolls,
 * replacement rolls). Cards scroll past, slow down and land on {@link RevealPayload#innateTechnique()}.
 * Cosmetic only: closing it at any time (or disconnecting) changes nothing, and it never sends anything back.
 * <ul>
 *   <li>Skip button, Esc, Enter or Space jumps straight to the result; on the result they close the screen.</li>
 *   <li>Client config {@code reveal.animation=false} shows the result instantly.</li>
 *   <li>Birth: also shows Malice capacity and tier, or the Severed reveal, and birth traits (this player only).</li>
 * </ul>
 */
public final class RevealScreen extends Screen {
    private static final long SPIN_MILLIS = 4200L;
    private static final int STRIP_LENGTH = 36;
    private static final int CARDS_AFTER_RESULT = 5;
    private static final int[] FALLBACK_WEIGHTS = {50, 30, 15, 5};

    private final RevealPayload reveal;
    @Nullable
    private final InnateTechnique result;
    private final List<InnateTechnique> strip = new ArrayList<>();
    private final int landingIndex = STRIP_LENGTH - 1 - CARDS_AFTER_RESULT;
    private final RandomSource random = RandomSource.create();
    private final List<Sparkle> sparkles = new ArrayList<>();

    private long startMillis = -1;
    private long lastFrameMillis;
    private boolean landed;
    private boolean landingEffectsDone;
    private int lastPassed = -1;
    private Button button;

    // Layout, recomputed in init() (so it follows GUI scale and window size).
    private float titleScale;
    private float nameScale;
    private int titleY;
    private int subtitleY;
    private int stripY;
    private int cardW;
    private int cardH;
    private int spacing;
    private int resultY;

    public RevealScreen(RevealPayload reveal) {
        super(Component.translatable("gui.kegare.reveal.title." + reveal.reason().name().toLowerCase(Locale.ROOT)));
        this.reveal = reveal;
        this.result = reveal.innateTechnique().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        buildStrip();
        if (result == null || reveal.severed() || !KegareClientConfig.REVEAL_ANIMATION.get()) landed = true;
    }

    /** Random cosmetic cards (weighted like real rolls), with the server's result at the landing slot. */
    private void buildStrip() {
        if (result == null) return;
        boolean includeTest = serverConfig(() -> KegareServerConfig.INCLUDE_TEST_INNATE_TECHNIQUES.get(), true);
        int[] weights = serverConfig(KegareServerConfig::rarityWeights, FALLBACK_WEIGHTS);
        List<InnateTechnique> pool = new ArrayList<>();
        for (InnateTechnique t : KegareRegistries.INNATE_TECHNIQUE) {
            if (!t.isTestContent() || includeTest) pool.add(t);
        }
        for (int i = 0; i < STRIP_LENGTH; i++) strip.add(pool.isEmpty() ? result : weightedPick(pool, weights));
        strip.set(landingIndex, result);
    }

    private InnateTechnique weightedPick(List<InnateTechnique> pool, int[] weights) {
        int total = 0;
        for (InnateTechnique t : pool) total += Math.max(1, weights[t.rarity().ordinal()]);
        int pick = random.nextInt(Math.max(1, total));
        for (InnateTechnique t : pool) {
            pick -= Math.max(1, weights[t.rarity().ordinal()]);
            if (pick < 0) return t;
        }
        return pool.get(pool.size() - 1);
    }

    /** Server config is synced to the client while connected; fall back if it isn't loaded. */
    private static <T> T serverConfig(java.util.function.Supplier<T> getter, T fallback) {
        try {
            return getter.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    @Override
    protected void init() {
        titleScale = width >= 420 ? 2f : 1.5f;
        nameScale = width >= 420 ? 1.5f : 1.25f;
        titleY = Math.max(6, (int) (height * 0.05f));
        subtitleY = titleY + (int) (font.lineHeight * titleScale) + 4;
        stripY = subtitleY + 16;
        cardH = Mth.clamp((int) (height * 0.34f), 64, 130);
        cardW = (int) (cardH * 0.72f);
        spacing = cardW + Math.max(4, cardW / 8);
        resultY = stripY + cardH + 10;
        button = addRenderableWidget(Button.builder(buttonLabel(), b -> advance())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private Component buttonLabel() {
        return Component.translatable(landed ? "gui.kegare.reveal.continue" : "gui.kegare.reveal.skip");
    }

    /** Skip to the result, or close once it is shown. */
    private void advance() {
        if (!landed) {
            landed = true;
        } else {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Opaque: nothing behind (chat, HUD) can spoil the result.
        g.fillGradient(0, 0, width, height, 0xFF150B22, 0xFF05030A); // fully opaque: the HUD already shows the result
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        long now = Util.getMillis();
        if (startMillis < 0) startMillis = now;
        float dt = lastFrameMillis == 0 ? 0f : Math.min(0.1f, (now - lastFrameMillis) / 1000f);
        lastFrameMillis = now;

        GuiDraw.centered(g, font, title, width / 2, titleY, titleScale, GuiDraw.WHITE);
        if (reveal.severed()) {
            renderSevered(g);
        } else {
            renderSubtitle(g);
            if (result != null) {
                renderStrip(g, now);
                if (landed) renderResult(g);
            } else {
                GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.reveal.no_innate_technique"),
                        width / 2, stripY + 20, Math.min(width - 40, 300), GuiDraw.GRAY);
            }
        }
        if (landed && !landingEffectsDone) {
            landingEffectsDone = true;
            onLanded();
        }
        renderSparkles(g, dt);
    }

    private void renderSubtitle(GuiGraphics g) {
        Component subtitle;
        if (reveal.birth()) {
            Component tier = Component.translatable(MaliceTier.values()[Mth.clamp(reveal.tier(), 0, MaliceTier.values().length - 1)].translationKey());
            subtitle = Component.translatable("gui.kegare.reveal.malice", reveal.capacity(), tier).withColor(GuiDraw.MALICE & 0xFFFFFF);
        } else {
            subtitle = Component.translatable("gui.kegare.reveal.subtitle." + reveal.reason().name().toLowerCase(Locale.ROOT)).withColor(GuiDraw.GRAY & 0xFFFFFF);
        }
        GuiDraw.centered(g, font, subtitle, width / 2, subtitleY, 1f, GuiDraw.WHITE);
    }

    private void renderStrip(GuiGraphics g, long now) {
        float progress = landed ? 1f : Mth.clamp((now - startMillis) / (float) SPIN_MILLIS, 0f, 1f);
        float eased = 1f - (float) Math.pow(1f - progress, 4);
        float offset = eased * landingIndex * spacing;
        if (!landed) {
            int passed = (int) Math.floor((offset + spacing * 0.5f) / spacing);
            if (passed != lastPassed) {
                if (lastPassed >= 0) play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.25f);
                lastPassed = passed;
            }
            if (progress >= 1f) {
                landed = true;
                offset = landingIndex * spacing;
            }
        }
        int center = width / 2;
        for (int i = 0; i < strip.size(); i++) {
            if (landed && i == landingIndex) continue; // drawn last, on top
            int x = Math.round(center + i * spacing - offset) - cardW / 2;
            if (x > width || x + cardW < 0) continue;
            GuiDraw.card(g, font, strip.get(i), x, stripY, cardW, cardH, landed ? 0.6f : 0f);
        }
        if (landed) {
            int x = center - cardW / 2;
            int color = result.rarity().color();
            float pulse = 0.65f + 0.35f * Mth.sin((now % 2000L) / 2000f * Mth.TWO_PI);
            GuiDraw.glow(g, x, stripY, cardW, cardH, color, glowStrength(result.rarity()) * pulse);
            g.pose().pushPose();
            g.pose().translate(center, stripY + cardH / 2f, 0);
            g.pose().scale(1.08f, 1.08f, 1f);
            g.pose().translate(-center, -(stripY + cardH / 2f), 0);
            GuiDraw.card(g, font, result, x, stripY, cardW, cardH, 0f);
            g.pose().popPose();
        } else {
            // Center markers (above everything on the strip).
            for (int i = 0; i < 5; i++) {
                g.fill(center - (5 - i), stripY - 9 + i, center + (5 - i), stripY - 8 + i, 400, GuiDraw.GOLD);
                g.fill(center - (5 - i), stripY + cardH + 8 - i, center + (5 - i), stripY + cardH + 9 - i, 400, GuiDraw.GOLD);
            }
        }
    }

    private static float glowStrength(InnateTechniqueRarity rarity) {
        return switch (rarity) {
            case COMMON -> 0.45f;
            case RARE -> 0.6f;
            case EPIC -> 0.8f;
            case LEGENDARY -> 1f;
        };
    }

    private void renderResult(GuiGraphics g) {
        int color = result.rarity().color();
        int y = resultY + 4;
        GuiDraw.centered(g, font, result.displayName(), width / 2, y, nameScale, 0xFF000000 | color);
        y += (int) (font.lineHeight * nameScale) + 3;
        Component rarity = Component.translatable(result.rarity().translationKey()).withColor(color);
        Component rarityLine = reveal.byHardPity()
                ? Component.translatable("gui.kegare.reveal.hard_pity", rarity, reveal.rollNumber())
                : rarity;
        GuiDraw.centered(g, font, rarityLine, width / 2, y, 1f, GuiDraw.WHITE);
        y += font.lineHeight + 3;
        y += GuiDraw.wrappedCentered(g, font, GuiDraw.description(result.descriptionId()), width / 2, y, Math.min(width - 40, 320), GuiDraw.GRAY) + 2;
        renderTraits(g, y);
    }

    /** Birth traits: shown only here, to this player (the payload was sent to them alone). */
    private int renderTraits(GuiGraphics g, int y) {
        for (ResourceLocation id : reveal.traits()) {
            Trait trait = KegareRegistries.TRAIT.get(id);
            if (trait == null) continue;
            GuiDraw.centered(g, font, Component.translatable("gui.kegare.reveal.trait", trait.displayName()), width / 2, y, 1f, GuiDraw.AQUA);
            y += font.lineHeight + 1;
            if (trait.isUnique()) {
                GuiDraw.centered(g, font, Component.translatable("gui.kegare.reveal.trait_private"), width / 2, y, 1f, GuiDraw.DARK_GRAY);
                y += font.lineHeight + 1;
            }
        }
        return y;
    }

    private void renderSevered(GuiGraphics g) {
        int emblem = Mth.clamp(height / 5, 32, 64);
        int y = subtitleY + 4;
        GuiDraw.sprite(g, GuiDraw.SEVERED_EMBLEM, width / 2 - emblem / 2, y, emblem, emblem);
        y += emblem + 8;
        GuiDraw.centered(g, font, Component.translatable("gui.kegare.reveal.severed.title"), width / 2, y, nameScale, 0xFFE6E6E6);
        y += (int) (font.lineHeight * nameScale) + 3;
        GuiDraw.centered(g, font, Component.translatable("gui.kegare.reveal.severed.subtitle"), width / 2, y, 1f, GuiDraw.RED);
        y += font.lineHeight + 4;
        y += GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.reveal.severed.desc"), width / 2, y, Math.min(width - 40, 300), GuiDraw.GRAY) + 2;
        renderTraits(g, y);
    }

    private void onLanded() {
        if (button != null) button.setMessage(buttonLabel());
        if (reveal.severed()) {
            play(SoundEvents.BEACON_DEACTIVATE, 0.6f, 1f);
            return;
        }
        if (result == null) return;
        switch (result.rarity()) {
            case COMMON -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
            case RARE -> play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
            case EPIC -> play(SoundEvents.PLAYER_LEVELUP, 1f, 0.8f);
            case LEGENDARY -> {
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                play(SoundEvents.TOTEM_USE, 1.2f, 0.5f);
                for (int i = 0; i < 60; i++) spawnSparkle(width / 2f, stripY + cardH / 2f, 40f + random.nextFloat() * 120f);
            }
        }
    }

    // ---------------------------------------------------------------- Legendary particles

    private static final class Sparkle {
        float x, y, vx, vy, life, maxLife, size;
        int color;
    }

    private void spawnSparkle(float x, float y, float speed) {
        Sparkle s = new Sparkle();
        float angle = random.nextFloat() * Mth.TWO_PI;
        s.x = x;
        s.y = y;
        s.vx = Mth.cos(angle) * speed;
        s.vy = Mth.sin(angle) * speed - 30f;
        s.maxLife = s.life = 0.8f + random.nextFloat() * 1.2f;
        s.size = 3 + random.nextInt(5);
        s.color = random.nextFloat() < 0.7f ? InnateTechniqueRarity.LEGENDARY.color() : 0xFFFFFF;
        sparkles.add(s);
    }

    private void renderSparkles(GuiGraphics g, float dt) {
        boolean legendaryShown = landed && !reveal.severed() && result != null && result.rarity() == InnateTechniqueRarity.LEGENDARY;
        if (legendaryShown) {
            // A steady trickle from the card's edges.
            for (float budget = dt * 28f; budget > 0f; budget -= 1f) {
                if (random.nextFloat() >= budget) continue;
                float ex = width / 2f + (random.nextFloat() - 0.5f) * cardW * 1.1f;
                float ey = stripY + (random.nextBoolean() ? 0 : cardH);
                spawnSparkle(ex, ey, 10f + random.nextFloat() * 30f);
            }
        }
        Iterator<Sparkle> it = sparkles.iterator();
        while (it.hasNext()) {
            Sparkle s = it.next();
            s.life -= dt;
            if (s.life <= 0f) {
                it.remove();
                continue;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += 25f * dt;
            int size = Math.max(1, Math.round(s.size * (0.5f + 0.5f * s.life / s.maxLife)));
            g.pose().pushPose();
            g.pose().translate(0, 0, 450);
            GuiDraw.tinted(g, GuiDraw.SPARKLE, Math.round(s.x) - size / 2, Math.round(s.y) - size / 2, size, size, s.color, Mth.clamp(s.life / s.maxLife, 0f, 1f));
            g.pose().popPose();
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------------------------------------------------------------- input / lifecycle

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            advance();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        RevealQueue.onRevealClosed();
        super.removed();
    }
}
