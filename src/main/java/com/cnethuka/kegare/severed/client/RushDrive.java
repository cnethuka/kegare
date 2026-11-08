package com.cnethuka.kegare.severed.client;

/** Closing Line's drive (DESIGN §3.27), built in its own step. */
final class RushDrive {
    private RushDrive() {}

    static boolean active() {
        return false;
    }

    static double speed() {
        return 0;
    }

    static net.minecraft.world.phys.Vec3 direction() {
        return net.minecraft.world.phys.Vec3.ZERO;
    }
}
