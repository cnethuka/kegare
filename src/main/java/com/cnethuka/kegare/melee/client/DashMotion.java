package com.cnethuka.kegare.melee.client;

/** How fast a dash goes at a given point: plain logic, kept apart from the client classes so it can be tested on its own. */
final class DashMotion {
    /** Closer than this to the end is there. */
    private static final double DONE = 0.05;

    private DashMotion() {}

    /** @return the speed for this tick, 0 once it has gone its distance. Slows to land exactly on it, whatever the keys added. */
    static double speedAt(double travelled, double distance, double speed) {
        double left = distance - travelled;
        return left <= DONE ? 0 : Math.min(speed, left);
    }
}
