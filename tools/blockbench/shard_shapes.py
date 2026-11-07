"""Frost Shard's and Splinter's shapes: small thrown crystals of the same ice as the lance, in blocks, pointing along +Z (the way they
fly), about their own middle.

Made of the lance's convex crystals (lance_shapes.crystal): every face a flat plane, every edge a ridge.

Pieces
    frost_shard  one sharp six-sided crystal, about 0.58 blocks long, with a little one grown into its back: small but solid
    splinter     a slimmer, shorter four-sided sliver, about 0.38 long: clearly the lighter of the two
    chip_*       what either breaks into when it hits, a few hundredths of a block across
"""
import random

import lance_shapes as L

CHIPS = 4


def pieces():
    rng = random.Random(2203)
    out = {}
    out["frost_shard"] = (L.crystal(rng, (0, 0, -0.18), (0, 0, 0.12), 0.058, 0.07, sides=6, roll=0.25, tip0=0.08, tip1=0.2,
                                    tip_shift=(0.004, 0.0), jitter=0.1)
                          + L.crystal(rng, (0.02, 0.012, -0.13), (0.068, 0.03, 0.0), 0.028, 0.024, sides=5, roll=1.1, tip1=0.06,
                                      jitter=0.12))
    out["splinter"] = L.crystal(rng, (0, 0, -0.12), (0, 0, 0.07), 0.034, 0.04, sides=4, flat=0.75, roll=0.6, tip0=0.05, tip1=0.15,
                                jitter=0.1)
    for i in range(CHIPS):
        half = 0.045 + 0.012 * i
        out[f"chip_{i}"] = L.crystal(rng, (0, 0, -half * 0.4), (0, 0, half * 0.4), 0.022 + 0.004 * i, 0.018 + 0.004 * i,
                                     sides=4 + i % 2, roll=i * 0.9, tip0=half * 0.5, tip1=half * 0.6, jitter=0.15)
    return out
