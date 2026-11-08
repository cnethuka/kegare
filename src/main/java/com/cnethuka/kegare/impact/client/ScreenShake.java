package com.cnethuka.kegare.impact.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Screen shake (DESIGN §3.23): the camera's angles only, as it's drawn. Never your aim, your facing or where you are, so a shake
 * can't throw a swing or a cast off. Each shake starts after the hit's freeze ends: a jolt the way the hit was going, then a fast
 * tremble that dies away. Shakes at once don't stack past the strongest by much.
 */
final class ScreenShake {
    private record Shake(long start, long end, float degrees, float jolt, Vec3 direction, float a, float b, float c) {}

    private static final List<Shake> SHAKES = new ArrayList<>();
    /** How fast it trembles (Hz), on each axis: three, so it never repeats into a wobble. */
    private static final float YAW_HZ = 17f, PITCH_HZ = 21f, ROLL_HZ = 13f;

    private ScreenShake() {}

    /**
     * @param degrees   how far, at its strongest
     * @param millis    how long, from {@code delayMillis} on
     * @param direction which way the hit was going; the first jolt goes with it ({@code jolt} 1, the one hit) or against it (-1, the
     *                  one hitting: a recoil)
     */
    static void add(float degrees, long millis, long delayMillis, Vec3 direction, float jolt) {
        if (degrees <= 0 || millis <= 0) return;
        long start = System.nanoTime() + Math.max(0, delayMillis) * 1_000_000L;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        SHAKES.add(new Shake(start, start + millis * 1_000_000L, degrees, jolt, direction, r.nextFloat() * Mth.TWO_PI,
                r.nextFloat() * Mth.TWO_PI, r.nextFloat() * Mth.TWO_PI));
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (SHAKES.isEmpty()) return;
        long now = System.nanoTime();
        SHAKES.removeIf(s -> now >= s.end);
        Camera camera = event.getCamera();
        float yaw = 0, pitch = 0, roll = 0, strongest = 0;
        for (Shake s : SHAKES) {
            if (now < s.start) continue;
            float t = (now - s.start) / (float) (s.end - s.start);
            float secs = (now - s.start) / 1.0e9f;
            float a = s.degrees * (1 - t) * (1 - t);
            strongest = Math.max(strongest, s.degrees);
            yaw += a * Mth.sin(Mth.TWO_PI * YAW_HZ * secs + s.a);
            pitch += 0.8f * a * Mth.sin(Mth.TWO_PI * PITCH_HZ * secs + s.b);
            roll += 0.5f * a * Mth.sin(Mth.TWO_PI * ROLL_HZ * secs + s.c);
            // The jolt: a quick shove the way the hit went, as the camera sees it, gone in about a tenth of a second.
            float kick = 1.2f * s.degrees * s.jolt * (float) Math.exp(-secs * 14);
            Vec3 left = new Vec3(camera.getLeftVector()), up = new Vec3(camera.getUpVector());
            yaw -= kick * (float) s.direction.dot(left);
            pitch -= kick * (float) s.direction.dot(up);
        }
        float cap = 1.3f * strongest;
        event.setYaw(event.getYaw() + Mth.clamp(yaw, -cap * 2, cap * 2));
        event.setPitch(event.getPitch() + Mth.clamp(pitch, -cap * 2, cap * 2));
        event.setRoll(event.getRoll() + Mth.clamp(roll, -cap, cap));
    }

    static boolean active() {
        return !SHAKES.isEmpty();
    }

    static void clear() {
        SHAKES.clear();
    }
}
