package com.cnethuka.kegare.client.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** The reveal's particles, on its canvas: square embers and flakes, and sparkles. Purely on-screen; nothing here is in the world. */
final class RevealParticles {
    private static final class Particle {
        float x, y, vx, vy, gravity, life, maxLife, fadeIn;
        int size, rgb;
        boolean sparkle;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    void clear() {
        particles.clear();
    }

    void ember(float x, float y, float vx, float vy, float gravity, float life, int size, int rgb) {
        add(x, y, vx, vy, gravity, life, size, rgb, false);
    }

    void sparkle(float x, float y, float vx, float vy, float gravity, float life, int size, int rgb) {
        add(x, y, vx, vy, gravity, life, size, rgb, true);
    }

    /** An ember that fades in where it appears, for particles that start in the middle of the screen. */
    void flake(float x, float y, float vx, float vy, float life, int size, int rgb) {
        add(x, y, vx, vy, 0f, life, size, rgb, false).fadeIn = 0.5f;
    }

    private Particle add(float x, float y, float vx, float vy, float gravity, float life, int size, int rgb, boolean sparkle) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.gravity = gravity;
        p.life = p.maxLife = life;
        p.size = size;
        p.rgb = rgb;
        p.sparkle = sparkle;
        particles.add(p);
        return p;
    }

    /** Sparkles flying out from a point. */
    void burst(float x, float y, int count, float speedMin, float speedMax, int rgb) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float speed = speedMin + random.nextFloat() * (speedMax - speedMin);
            int color = random.nextFloat() < 0.7f ? rgb : GuiDraw.mix(rgb, 0xFFFFFF, 0.6f);
            sparkle(x, y, Mth.cos(angle) * speed, Mth.sin(angle) * speed - 15f, 30f, 0.5f + random.nextFloat() * 0.9f, 3 + random.nextInt(5), color);
        }
    }

    void render(GuiGraphics g, float dt, int z) {
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0f) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += p.gravity * dt;
            float fade = Mth.clamp(p.life / p.maxLife * 1.6f, 0f, 1f);
            if (p.fadeIn > 0f) fade = Math.min(fade, Mth.clamp((p.maxLife - p.life) / p.fadeIn, 0f, 1f));
            int px = Math.round(p.x), py = Math.round(p.y);
            if (p.sparkle) {
                int size = Math.max(1, Math.round(p.size * (0.5f + 0.5f * p.life / p.maxLife)));
                GuiDraw.tinted(g, GuiDraw.SPARKLE, px - size / 2, py - size / 2, size, size, p.rgb, fade);
            } else {
                g.fill(px, py, px + p.size, py + p.size, ((int) (fade * 255) << 24) | p.rgb);
            }
        }
        g.pose().popPose();
    }
}
