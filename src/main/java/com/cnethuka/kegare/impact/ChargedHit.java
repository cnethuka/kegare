package com.cnethuka.kegare.impact;

/**
 * Something that hits (a projectile, a spike, a puppet going off) and carries the charge it was cast at, from 0 to 1. The Impact
 * System reads it from the damage source's direct entity: the charge is one of the three things a hit's strength comes from.
 */
public interface ChargedHit {
    float hitCharge();
}
