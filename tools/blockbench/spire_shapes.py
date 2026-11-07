"""Glacial Spire's shapes: a cluster of ice crystals bursting up out of the ground, in blocks, Y up, the ground at y = 0 and the
spike's foot sunk a little below it (so it can rise out of the ground in the game without a seam showing).

Made of the same convex crystals as the lance (lance_shapes.crystal): every face a flat plane, every edge a ridge. Drawn at its
full-charge size; the game scales it down for less charge and leaves the `heavy` group off below half a charge.

Pieces (each erupts on its own, a beat apart, in the game):
    core     the spire itself: one great crystal with two more grown into its foot, about 3.4 blocks to the point
    crown    three crystals round it, half its height, leaning well out
    ring     seven short ones jutting out of the ground round its foot, out to about 1.3 blocks (the reach of the hit, 1.5)
    heavy    two more great crystals and four more short ones, for a charge over half: wider, heavier
    chunk_*  pieces it breaks into when it shatters, about their own middle, along Y
"""
import math
import random

import lance_shapes as L

#: (name, ticks after the eruption it starts rising, the charge (0 to 1) it needs to be there)
PIECES = (("ring", 0.0, 0.0), ("core", 0.0, 0.0), ("crown", 1.0, 0.0), ("heavy", 1.5, 0.5))
CHUNKS = 4


def around(angle, r, y):
    return (math.cos(angle) * r, y, math.sin(angle) * r)


def pieces():
    rng = random.Random(4417)
    out = {}
    up = (0.0, 0.0, 1.0)  # the crystals' own "up" (for their roll) when they stand along Y
    out["core"] = (L.crystal(rng, (0.0, -0.6, 0.0), (0.06, 2.45, 0.04), 0.56, 0.34, sides=6, roll=0.2, twist=0.22, tip1=0.95,
                             tip_shift=(0.05, 0.03), jitter=0.1, up=up)
                   + L.crystal(rng, (0.24, -0.6, 0.12), (0.38, 1.25, 0.18), 0.32, 0.2, sides=5, roll=0.9, tip1=0.55,
                               jitter=0.1, up=up)
                   + L.crystal(rng, (-0.2, -0.6, -0.18), (-0.3, 0.85, -0.3), 0.28, 0.18, sides=5, roll=2.1, tip1=0.42,
                               jitter=0.1, up=up))
    crown = []
    for k, (angle, height, lean) in enumerate(((0.5, 1.45, 1.0), (2.6, 1.1, 1.08), (4.4, 1.3, 1.02))):
        crown += L.crystal(rng, around(angle, 0.3, -0.5), around(angle + 0.12, lean, height), 0.22, 0.13, sides=5,
                           roll=k * 0.8, tip1=0.45, jitter=0.12, up=up)
    out["crown"] = crown
    ring = []
    for k in range(7):
        angle = 2 * math.pi * (k + rng.uniform(-0.25, 0.25)) / 7 + 0.3
        reach = rng.uniform(0.95, 1.12)
        rise = rng.uniform(0.35, 0.62)
        ring += L.crystal(rng, around(angle, 0.42, -0.35), around(angle, reach, rise), 0.13, 0.085, sides=4,
                          roll=rng.uniform(0, 3), tip1=rng.uniform(0.22, 0.34), jitter=0.14, up=up)
    out["ring"] = ring
    heavy = []
    for k, (angle, height, lean) in enumerate(((1.55, 1.75, 0.95), (5.45, 1.55, 1.0))):
        heavy += L.crystal(rng, around(angle, 0.32, -0.55), around(angle, lean, height), 0.27, 0.16, sides=6,
                           roll=k * 1.3, tip1=0.55, jitter=0.1, up=up)
    for k in range(4):
        angle = 2 * math.pi * (k + 0.5) / 4 + rng.uniform(-0.3, 0.3)
        heavy += L.crystal(rng, around(angle, 0.6, -0.3), around(angle, rng.uniform(1.15, 1.32), rng.uniform(0.3, 0.5)),
                           0.12, 0.08, sides=4, roll=rng.uniform(0, 3), tip1=0.28, jitter=0.14, up=up)
    out["heavy"] = heavy
    sizes = ((0.32, 0.16, 5), (0.45, 0.2, 6), (0.26, 0.13, 4), (0.38, 0.18, 5))
    for i, (half, r, sides) in enumerate(sizes):
        out[f"chunk_{i}"] = L.crystal(rng, (0, -half * 0.4, 0), (0, half * 0.4, 0), r, r * 0.78, sides=sides, roll=i * 0.7,
                                      twist=0.35, tip0=half * 0.5, tip1=half * 0.7, jitter=0.15, up=up)
    return out
