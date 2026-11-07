"""Rime Shell's plates: solid slabs of the lance's ice hugging the body, in blocks, each in the space of the body part it sits on
(Blockbench's axes about that part's pivot: Y up, the player's right +X, the front -Z), so the game draws each with its part and it
follows every pose.

A plate is one convex slab (lance_shapes.hull): an irregular outline lying just inside the skin, the same outline a little smaller
standing off it, and a crest along its length standing off a little more. So every face is a flat plane and every edge a ridge,
like the lance's crystals, and the face the world sees is the wide one, navy in the middle fading to pale frost at its ridges.

Parts, about their pivots (the player model's boxes, in pixels): body x -4..4, y -12..0, z -2..2 (pivot at the neck); right arm
x -1..3, y -10..2, z -2..2 (wide arms; a slim arm is a pixel narrower on its outside, which the game shifts for); right leg x -2..2,
y -12..0, z -2..2. The left arm and leg are the right's, mirrored.

Pieces, in the order they form (PLATES): torso_*, right_arm_*, left_arm_*, right_leg_*, left_leg_*.
"""
import math
import random

import lance_shapes as L

PX = 1 / 16
#: How far into the skin each plate's inner face sits, so no seam shows between them.
SINK = 0.15 * PX


def plate(rng, centre, normal, along, w, h, thick=0.8, crest=0.7, bevel=0.1, jitter=0.08, point=0.0):
    """A slab on the surface at `centre` (pixels), facing `normal`, `h` long along `along` and `w` across (pixels): a six-sided
    outline with a crest from its top corner to its bottom one standing `crest` proud of the rest, so the face is two broad facets
    meeting in a ridge, like a shield of ice. `point` draws the top corner out further, into a spike."""
    n = L.norm(normal)
    v = L.norm(L.sub(along, L.mul(n, L.dot(along, n))))
    u = L.cross(v, n)
    j = lambda: 1 + rng.uniform(-jitter, jitter)  # noqa: E731
    # Each side's two corners equally far out, so each side of the crest is one flat facet (one plane, one ridge round it).
    top, bottom = (0.0, h / 2 * (1 + point)), (0.0, -h / 2 * j())
    right, left = w / 2 * j(), -w / 2 * j()
    sides = [(right, h * 0.2 * j()), (right, -h * 0.24 * j()), (left, -h * 0.24 * j()), (left, h * 0.2 * j())]
    c = L.mul(centre, PX)

    def at(x, y, lift, shrink=1.0):
        flat = L.add(L.mul(u, x * PX * shrink), L.mul(v, y * PX * shrink))
        return L.add(c, L.add(flat, L.mul(n, lift * PX)))
    pts = [at(x, y, -SINK / PX) for x, y in [top] + sides + [bottom]]
    pts += [at(x, y, thick, 1 - bevel) for x, y in sides]
    pts += [at(x, y, thick + crest, 1 - bevel) for x, y in (top, bottom)]
    return L.hull(pts)


def right_arm(rng):
    return [
        # the shoulder: a cap over the top and a plate down the outside
        plate(rng, (1.2, 1.75, 0.0), (0.35, 1.0, 0.0), (0, 0, 1), 4.4, 5.0, thick=0.8, crest=0.8),
        plate(rng, (3.0, -0.9, 0.0), (1.0, 0.3, 0.0), (0, 1, 0.1), 4.8, 4.8, thick=0.9, crest=0.8),
        # the forearm: a bracer on the outside, a smaller one on the front
        plate(rng, (3.0, -6.8, -0.1), (1.0, -0.05, -0.12), (0, 1, 0), 4.6, 4.8, thick=0.8, crest=0.7),
        plate(rng, (1.0, -7.4, -2.0), (0.25, 0.0, -1.0), (0, 1, 0), 3.2, 4.0, thick=0.7, crest=0.5),
    ]


def right_leg(rng):
    return [
        plate(rng, (0.1, -3.6, -2.0), (0.15, 0.08, -1.0), (0, 1, 0), 4.4, 5.4, thick=0.8, crest=0.8),
        plate(rng, (2.0, -4.6, 0.2), (1.0, 0.05, 0.0), (0, 1, 0), 3.8, 4.8, thick=0.7, crest=0.6),
        plate(rng, (0.0, -9.1, -2.0), (0.0, -0.1, -1.0), (0, 1, 0), 4.2, 5.0, thick=0.8, crest=0.7),
    ]


def torso(rng):
    out = [
        plate(rng, (2.1, -3.2, -2.0), (0.2, 0.1, -1.0), (0.12, 1, 0), 5.2, 6.0, thick=0.9, crest=0.9),
        plate(rng, (-2.1, -3.0, -2.0), (-0.2, 0.1, -1.0), (-0.12, 1, 0), 5.2, 5.8, thick=0.9, crest=0.9),
        plate(rng, (0.0, -3.4, 2.0), (0.0, 0.05, 1.0), (0, 1, 0), 7.8, 6.4, thick=0.9, crest=0.9),
        plate(rng, (0.2, -8.4, 2.0), (0.0, -0.05, 1.0), (0, 1, 0), 6.8, 4.8, thick=0.8, crest=0.7),
        plate(rng, (0.2, -8.0, -2.0), (0.0, -0.06, -1.0), (0, 1, 0), 6.4, 4.8, thick=0.8, crest=0.7),
    ]
    for s in (1, -1):
        # a shard either side of the neck, angled up into a point; a plate down each side
        out.append(plate(rng, (s * 2.6, -0.5, -1.3), (s * 0.3, 0.8, -0.55), (s * 0.2, 0.3, 1), 2.6, 3.0, thick=0.7, crest=0.6, point=0.5))
        out.append(plate(rng, (s * 4.0, -6.4, 0.0), (s * 1.0, 0.0, 0.05), (0, 1, 0), 3.9, 5.6, thick=0.7, crest=0.6))
    return out


def pieces():
    rng = random.Random(7731)
    parts = {"torso": torso(rng), "right_arm": right_arm(rng), "right_leg": right_leg(rng)}
    parts["left_arm"] = [L.mirror_x(p) for p in right_arm(random.Random(7741))]
    parts["left_leg"] = [L.mirror_x(p) for p in right_leg(random.Random(7751))]
    out = {}
    for part in ("torso", "right_arm", "left_arm", "right_leg", "left_leg"):
        for i, polys in enumerate(parts[part]):
            out[f"{part}_{i}"] = polys
    return out


#: Every plate, in the order they form when the shell goes up (and break off, the other way, when it comes down).
PLATES = list(pieces())
