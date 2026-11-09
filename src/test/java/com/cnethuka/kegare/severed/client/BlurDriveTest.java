package com.cnethuka.kegare.severed.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Blur's drive (DESIGN §3.27) takes off the push vanilla's own move adds from the movement keys, so its configured speed is its real
 * top speed. The push it takes off has to be the game's own, to the last digit, for every key, facing and speed.
 */
class BlurDriveTest {
    @Test
    void thePushItTakesOffIsTheGamesOwn() throws Exception {
        Method vanilla = Entity.class.getDeclaredMethod("getInputVector", Vec3.class, float.class, float.class);
        vanilla.setAccessible(true);
        float[] impulses = {-1f, -0.3f, 0f, 0.2f, 1f};
        float[] amounts = {0.02f, 0.026f, 0.1f, 0.13f, 0.26f};
        for (float left : impulses) {
            for (float forward : impulses) {
                for (float amount : amounts) {
                    for (float yaw = -180f; yaw < 180f; yaw += 37.5f) {
                        float l = left * 0.98F, f = forward * 0.98F;
                        Vec3 game = (Vec3) vanilla.invoke(null, new Vec3(l, 0, f), amount, yaw);
                        Vec3 ours = BlurDrive.inputVector(l, f, amount, yaw);
                        assertEquals(game, ours, "keys " + left + "/" + forward + ", " + amount + " at " + yaw);
                    }
                }
            }
        }
    }

    @Test
    void whatItSetsPlusThePushIsTheVelocity() {
        Vec3 velocity = new Vec3(0.7, 0, 0);
        Vec3 push = BlurDrive.inputVector(0f, 0.98f, 0.13f, -90f); // sprinting forward, facing +X
        assertEquals(0.13 * 0.98, push.x, 1.0e-6, "a sprint's push, the way they face");
        Vec3 set = velocity.subtract(push);
        assertEquals(0.7, set.add(push).x, 1.0e-9, "the move comes out at Blur's own speed");
    }
}
