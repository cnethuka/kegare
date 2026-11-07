"""Glacial Lance's shapes, in blocks: the lance's chunks and the shards it breaks into, each a cluster of convex crystals.

A crystal is the convex hull of two rings of points and its terminations, so every face is a flat plane and every edge a sharp
ridge. The lance lies along +Z with its tip forward, its blade wide along X, and the fist round the origin. Pieces are in the
order they snap into place while it charges (Java's LanceShape keeps the same names and thresholds).

Everything is seeded, so a run always makes the same shapes. build_glacial_lance.py lays the faces out in a texture atlas,
paints it, and sends the meshes to Blockbench.
"""
from __future__ import annotations

import math
import random
from itertools import combinations

EPS = 1e-7


# ---------------------------------------------------------------------------------------------- vectors

def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def norm(a):
    n = length(a)
    return (a[0] / n, a[1] / n, a[2] / n)


# ---------------------------------------------------------------------------------------------- hulls

def hull(points):
    """Convex hull as planar polygons, each wound counter-clockwise seen from outside. Brute force: fine for a few dozen points."""
    planes = {}
    n = len(points)
    for i, j, k in combinations(range(n), 3):
        normal = cross(sub(points[j], points[i]), sub(points[k], points[i]))
        if length(normal) < 1e-9:
            continue
        normal = norm(normal)
        d = dot(normal, points[i])
        above = below = False
        for m in range(n):
            s = dot(normal, points[m]) - d
            if s > 1e-6:
                above = True
            elif s < -1e-6:
                below = True
            if above and below:
                break
        if above and below:
            continue
        if above:
            normal, d = mul(normal, -1), -d
        key = (round(normal[0], 4), round(normal[1], 4), round(normal[2], 4), round(d, 4))
        if key in planes:
            continue
        on = [m for m in range(n) if abs(dot(normal, points[m]) - d) < 1e-6]
        planes[key] = (normal, on)
    polygons = []
    for normal, on in planes.values():
        centre = mul(sum_points([points[m] for m in on]), 1 / len(on))
        u = norm(sub(points[on[0]], centre))
        v = cross(normal, u)
        ordered = sorted(on, key=lambda m: math.atan2(dot(sub(points[m], centre), v), dot(sub(points[m], centre), u)))
        polygon = [points[m] for m in ordered]
        polygons.append(drop_collinear(polygon))
    return polygons


def sum_points(pts):
    out = (0.0, 0.0, 0.0)
    for p in pts:
        out = add(out, p)
    return out


def drop_collinear(polygon):
    out = []
    for i, p in enumerate(polygon):
        a, b = polygon[i - 1], polygon[(i + 1) % len(polygon)]
        if length(cross(sub(p, a), sub(b, p))) > 1e-9:
            out.append(p)
    return out


def polygon_normal(polygon):
    total = (0.0, 0.0, 0.0)
    for i in range(1, len(polygon) - 1):
        total = add(total, cross(sub(polygon[i], polygon[0]), sub(polygon[i + 1], polygon[0])))
    return norm(total)


# ---------------------------------------------------------------------------------------------- crystals

def crystal(rng, start, end, r0, r1, sides=6, flat=1.0, roll=0.0, twist=0.0, tip0=0.0, tip1=0.25, jitter=0.12,
            tip_shift=(0.0, 0.0), up=(0.0, 1.0, 0.0)):
    """A crystal along start -> end: a ring of `sides` points at each end (radii r0 and r1), pointed `tip0` / `tip1` blocks past
    each end (0 cuts it off flat), squashed to `flat` across its own up so a blade comes out thin, the far ring turned by `twist` (which breaks its sides into triangles)."""
    axis = norm(sub(end, start))
    side = cross(axis, up)
    if length(side) < 1e-6:
        side = cross(axis, (1.0, 0.0, 0.0))
    side = norm(side)
    upv = cross(side, axis)
    # One irregular ring, used at both ends: untwisted, every side between them is one long flat plane.
    ring = [(2 * math.pi * (i + rng.uniform(-jitter, jitter)) / sides, 1 + rng.uniform(-jitter, jitter)) for i in range(sides)]
    pts = []
    for centre, r, turn in ((start, r0, roll), (end, r1, roll + twist)):
        for a, k in ring:
            pts.append(add(centre, add(mul(side, math.cos(a + turn) * r * k), mul(upv, math.sin(a + turn) * r * k * flat))))
    if tip0 > 0:
        pts.append(add(start, mul(axis, -tip0)))
    if tip1 > 0:
        pts.append(add(add(end, mul(axis, tip1)), add(mul(side, tip_shift[0]), mul(upv, tip_shift[1]))))
    return hull(pts)


def mirror_x(polygons):
    return [[(-p[0], p[1], p[2]) for p in reversed(poly)] for poly in polygons]


# ---------------------------------------------------------------------------------------------- the lance

#: (name, charge it snaps in at, as a fraction of a full charge). The fist is round the origin; the tip points along +Z.
STAGES = (("grip", 0.0), ("fore", 0.2), ("aft", 0.35), ("head", 0.55), ("tail", 0.75), ("spurs", 1.0))
SHARDS = 4


def pieces():
    """name -> list of polygons, for every chunk of the lance and every shard."""
    rng = random.Random(1937)
    out = {}
    # The stub: a double-ended crystal through the fist.
    out["grip"] = crystal(rng, (0, 0, -0.2), (0, 0, 0.24), 0.078, 0.084, sides=6, roll=0.3, tip0=0.12, tip1=0.16)
    # The javelin: a shaft forward to a point and a shaft back to a point, overlapping round the fist so they grow out of each
    # other with no seam, and a splinter riding the front half.
    out["fore"] = (crystal(rng, (0, 0, -0.08), (0, 0, 0.86), 0.108, 0.096, sides=6, roll=0.1, tip0=0.1, tip1=0.42,
                           tip_shift=(0.015, 0.02))
                   + crystal(rng, (0.05, 0.04, 0.4), (0.14, 0.11, 0.7), 0.048, 0.036, sides=4, roll=0.6, tip1=0.17, jitter=0.08))
    out["aft"] = crystal(rng, (0, 0, 0.18), (0, 0, -0.52), 0.1, 0.09, sides=6, roll=0.62, tip0=0.08, tip1=0.32,
                         tip_shift=(-0.012, 0.018))
    # The full head: a broad blade, razor along X, with a barb swept back off each shoulder.
    blade = crystal(rng, (0, 0, 0.92), (0, 0, 1.24), 0.13, 0.28, sides=4, flat=0.38, tip1=0.72, jitter=0.04, tip_shift=(0.025, 0.0))
    barb = crystal(rng, (0.13, 0.0, 1.2), (0.25, 0.0, 1.04), 0.055, 0.036, sides=4, flat=0.55, tip1=0.2, jitter=0.05)
    barb2 = crystal(rng, (0.12, 0.0, 1.17), (0.235, 0.01, 1.02), 0.05, 0.033, sides=4, flat=0.55, tip1=0.16, jitter=0.05)
    out["head"] = blade + barb + mirror_x(barb2)
    # The tail: the shaft's end splits into a cluster of splinters.
    out["tail"] = (crystal(rng, (0, 0, -0.45), (0.025, 0.035, -0.85), 0.08, 0.06, sides=5, roll=0.2, tip1=0.26)
                   + crystal(rng, (0, 0, -0.42), (-0.09, -0.05, -0.74), 0.06, 0.045, sides=4, roll=0.7, tip1=0.22)
                   + crystal(rng, (0, 0, -0.55), (0.08, -0.07, -0.76), 0.05, 0.038, sides=4, roll=0.1, tip1=0.16))
    # Full charge: jagged growths breaking out of the shaft.
    out["spurs"] = (crystal(rng, (0.02, 0.06, 0.3), (0.06, 0.24, 0.58), 0.06, 0.045, sides=5, roll=0.4, tip1=0.24)
                    + crystal(rng, (-0.03, -0.05, -0.1), (-0.15, -0.18, -0.32), 0.055, 0.04, sides=4, roll=0.2, tip1=0.2)
                    + crystal(rng, (-0.06, 0.0, 0.6), (-0.19, -0.05, 0.84), 0.045, 0.034, sides=4, roll=0.9, tip1=0.16))
    # Shards for a break, about the origin, along Z.
    sizes = ((0.14, 0.04, 4), (0.2, 0.05, 5), (0.1, 0.034, 3), (0.17, 0.055, 4))
    for i, (half, r, sides) in enumerate(sizes):
        out[f"shard_{i}"] = crystal(rng, (0, 0, -half * 0.35), (0, 0, half * 0.35), r, r * 0.8, sides=sides, roll=i * 0.7, twist=0.3,
                                    tip0=half * 0.55, tip1=half * 0.75, jitter=0.15)
    return out


def triangles(polygon):
    """A convex polygon as a fan of triangles (Blockbench's faces are three or four corners; the game draws triangles)."""
    return [(polygon[0], polygon[i], polygon[i + 1]) for i in range(1, len(polygon) - 1)]
