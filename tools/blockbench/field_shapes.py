"""Frost Field's crystals: small clusters of ice jutting up out of the frosted ground for a moment, in blocks, Y up, the ground at
y = 0 and their feet sunk below it (so they can rise out of the ground in the game without a seam showing).

Made of the lance's convex crystals (lance_shapes.crystal): every face a flat plane, every edge a ridge. Small and sharp, like
frost grown fast: each cluster is a main crystal and one to three lesser ones from the same root, leaning out.

Pieces
    cluster_0..3  2 to 4 crystals each, the tallest 0.4 to 0.7 blocks out of the ground
"""
import math
import random

import lance_shapes as L

CLUSTERS = 4
#: How deep their feet go below the ground.
FOOT = 0.22


def around(angle, r, y):
    return (math.cos(angle) * r, y, math.sin(angle) * r)


def pieces():
    rng = random.Random(5521)
    up = (0.0, 0.0, 1.0)  # the crystals' own "up" (for their roll) when they stand along Y
    out = {}
    for i in range(CLUSTERS):
        n = (2, 3, 4, 3)[i]
        polys = []
        base = rng.uniform(0, 2 * math.pi)
        for k in range(n):
            angle = base + 2 * math.pi * k / n + rng.uniform(-0.4, 0.4)
            main = k == 0
            lean = rng.uniform(0.02, 0.08) if main else rng.uniform(0.14, 0.26)
            high = rng.uniform(0.42, 0.62) if main else rng.uniform(0.2, 0.36)
            r = rng.uniform(0.09, 0.11) if main else rng.uniform(0.055, 0.07)
            polys += L.crystal(rng, around(angle, 0.02, -FOOT), around(angle, lean, high), r, r * 0.72,
                               sides=rng.choice((4, 5, 5, 6)), roll=rng.uniform(0, 3), tip1=rng.uniform(0.12, 0.17) * (1.25 if main else 1),
                               tip_shift=(rng.uniform(-0.01, 0.01), 0.0), jitter=0.12, up=up)
        out[f"cluster_{i}"] = polys
    return out
