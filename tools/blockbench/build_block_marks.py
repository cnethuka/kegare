"""Rime's marks on blocks: pixel art on the blocks' own grid, 16 texels a block, hard pixels, so the block itself looks frozen.

    python3 -I tools/blockbench/build_block_marks.py            write them
    python3 -I tools/blockbench/build_block_marks.py --preview  also run/marks_preview/<name>.png: on stone at 8x, as it spreads

Writes, in src/main/resources/assets/kegare/textures/marks/
    <name>.png        the art: every texel is one of the few palette colours below (alpha too), nothing blended or smoothed
    <name>_order.png  when each texel comes and goes: grey 0 forms first and melts last, 255 forms last and melts first
    <name>_glow.png   (only for a mark with some glow) how much each texel glows, 0 to 15 (stored x17): drawn at least that bright
                      whatever the world's light, so it reads in the dark, but it lights nothing round it
    white.png         what the game draws them on (each texel's colour goes on its vertices)

The game (BlockMarks.java) lays a pattern on the faces of the blocks with its centre snapped to their texel grid, so one texel of the
art is exactly one texel of the block under it, at any distance. Nothing in the world changes, and every texel melts on its own.
How far a mark reaches is how much of its order it shows (a light hit shows the inner part of the same frost), so a pattern is drawn
at its biggest. Both PNGs are nearest-filtered (no .mcmeta): the pixels must stay hard.

The Python is the source. Change a pattern here and run it again.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/kegare/textures/marks"
PREVIEW = ROOT / "run/marks_preview"

# The palette, faintest to brightest. Alpha is part of each colour: the faint ones let the block's own texture through, so it reads as
# the block gone cold, not as something lying on it.
NONE = (0, 0, 0, 0)
FILM = (176, 212, 246, 100)    # a cold sheen over the block
FROST = (204, 230, 255, 178)   # frost
RIME = (230, 244, 255, 230)    # thick rime: the spines
GLINT = (255, 255, 255, 250)   # where it's thickest, and the odd sparkle
FISSURE = (22, 38, 90, 232)    # a crack in the ground: deep navy, nearly solid
EDGE = (128, 180, 240, 214)    # the lit lip along a crack
ICE = (112, 172, 234, 150)     # a glaze of ice: the block's own texture still shows through, gone blue
DEEP = (64, 122, 204, 164)     # where the glaze is thicker
SHINE = (196, 238, 255, 255)   # light in a crack: the warning's lines, solid
HALO = (120, 196, 255, 110)    # the faint glow along them, the block showing through
PALETTE = (NONE, FILM, FROST, RIME, GLINT, FISSURE, EDGE, ICE, DEEP, SHINE, HALO)
# How much each tone glows (a minimum light, 0 to 15): only the warning's lines and the glow along them; everything else is lit by
# the world. A mark can also have a faint glow all over (GLOW_FLOOR below).
GLOW = {9: 15, 10: 11}
# Strength, for a texel that two strokes cross: a crack cuts through frost, its lip shows over the sheen. In game the more opaque
# colour wins where two marks meet, so a glaze (less opaque than frost) never covers frost, and the sheen never covers the glaze.
STRENGTH = {0: 0, 1: 1, 10: 2, 7: 3, 8: 4, 6: 5, 2: 6, 3: 7, 4: 8, 5: 9, 9: 10}

# 16 headings, so every line is one of the clean pixel slopes (flat, 1:2, 1:1, 2:1) instead of a ragged stair.
HEADINGS = [i * math.pi / 8 for i in range(16)]


class Canvas:
    """Tones (indices into PALETTE) on a square grid; a texel keeps the strongest tone put on it."""

    def __init__(self, size):
        self.size = size
        self.tone = [[0] * size for _ in range(size)]

    def put(self, x, y, t):
        if 0 <= x < self.size and 0 <= y < self.size and STRENGTH[self.tone[y][x]] < STRENGTH[t]:
            self.tone[y][x] = t

    def get(self, x, y):
        return self.tone[y][x] if 0 <= x < self.size and 0 <= y < self.size else 0

    def filled(self):
        return [(x, y) for y in range(self.size) for x in range(self.size) if self.tone[y][x]]


def line(x0, y0, x1, y1):
    """Bresenham: the texels from one point to the other, both ends included."""
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return pts
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def ray(cx, cy, heading, length):
    return line(cx, cy, cx + round(math.cos(heading) * length), cy + round(math.sin(heading) * length))


def side_of(heading):
    """The texel step straight across a line going this way (so the line can be made thicker on one side)."""
    return round(-math.sin(heading)), round(math.cos(heading))


def geodesic_order(canvas, centre, loose=1.15):
    """Each texel's distance from the centre, walking only through frost (8-connected, diagonals cost more): so frost grows along its
    spines, as frost does. Texels it can't reach (a lone speck) take their straight distance, a little later. 0..255."""
    import heapq
    cx, cy = centre
    dist = {}
    heap = []
    for x, y in canvas.filled():
        if abs(x - cx) <= 1 and abs(y - cy) <= 1:
            dist[(x, y)] = 0.0
            heapq.heappush(heap, (0.0, x, y))
    while heap:
        d, x, y = heapq.heappop(heap)
        if d > dist.get((x, y), 1e9):
            continue
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                if (ox or oy) and canvas.get(x + ox, y + oy):
                    nd = d + (1.414 if ox and oy else 1.0)
                    if nd < dist.get((x + ox, y + oy), 1e9):
                        dist[(x + ox, y + oy)] = nd
                        heapq.heappush(heap, (nd, x + ox, y + oy))
    for x, y in canvas.filled():
        dist.setdefault((x, y), math.hypot(x - cx, y - cy) * loose)
    top = max(dist.values())
    return {k: min(255, round(v / top * 255)) for k, v in dist.items()}


def save(name, canvas, order, floor=0):
    size = canvas.size
    art = Image.new("RGBA", (size, size), NONE)
    when = Image.new("L", (size, size), 0)
    glow = Image.new("L", (size, size), 0)
    for (x, y), o in order.items():
        tone = canvas.tone[y][x]
        art.putpixel((x, y), PALETTE[tone])
        when.putpixel((x, y), o)
        glow.putpixel((x, y), max(floor, GLOW.get(tone, 0)) * 17)
    OUT.mkdir(parents=True, exist_ok=True)
    art.save(OUT / f"{name}.png", optimize=True)
    when.save(OUT / f"{name}_order.png", optimize=True)
    if glow.getbbox():
        glow.save(OUT / f"{name}_glow.png", optimize=True)
    else:
        (OUT / f"{name}_glow.png").unlink(missing_ok=True)
    print(f"{name}: {size}x{size}, {len(order)} texels")
    return art, when


# ---------------------------------------------------------------------------------------------------- the patterns


def fern(c, rng, x0, y0, heading, length, start=3, every=2, reach=4.0, spine=3):
    """One frost spine the way frost grows on a cold surface: a straight line on a clean slope, rime at the root thinning to frost and
    then sheen at the tip, with short feathers off it at 45 degrees forward, alternating sides, shorter towards the tip."""
    pts = ray(x0, y0, heading, length)
    n = len(pts)
    side = rng.choice((-1, 1))
    for i, (x, y) in enumerate(pts):
        f = i / max(1, n - 1)
        c.put(x, y, spine if f < 0.65 else 2 if f < 0.92 else 1)
        if i >= start and (i - start) % every == 0 and f < 0.9:
            side = -side
            feather = ray(x, y, heading + side * math.pi / 4, max(1.0, reach * (1 - f) + rng.uniform(-0.6, 0.6)))
            for j, (fx, fy) in enumerate(feather[1:]):
                c.put(fx, fy, 2 if j < len(feather) - 2 or len(feather) <= 2 else 1)
    return pts


def lance_frost(seed=11, size=64):
    """Where a Glacial Lance comes down: frost bursting out from the point it struck, over the block's own face. A ragged crust of
    rime with a few glints, spines of uneven length on the clean slopes, feathered like frost on glass, a dithered cold sheen
    between them near the middle, and loose specks thrown clear. At its fullest it reaches about a block and a half (24 texels)
    from the centre; a lighter hit shows only the inner part."""
    rng = random.Random(seed)
    c = Canvas(size)
    cx = cy = size // 2
    # The sheen first (everything stronger overwrites it): a checkerboard round the core that thins out, raggedly, with distance.
    edge = [rng.uniform(8, 13) for _ in range(16)]
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx, y - cy)
            a = (math.atan2(y - cy, x - cx) / (2 * math.pi)) % 1.0
            r = edge[int(a * 16) % 16]
            if (x + y) % 2 == 0 and d < r and rng.random() < 1.25 - d / r:
                c.put(x, y, 1)
    # The spines: eleven of the sixteen headings, long and short mixed so it bursts unevenly, not like a snowflake.
    headings = sorted(rng.sample(HEADINGS, 11))
    for i, h in enumerate(headings):
        long = (i % 2 == 0) == (rng.random() < 0.8)
        length = rng.uniform(17, 24) if long else rng.uniform(7, 13)
        fern(c, rng, cx, cy, h + rng.choice((0, 0, 0.12, -0.12)), length, start=rng.choice((4, 5)), every=rng.choice((2, 2, 3)),
             reach=4.2 if long else 2.6)
    # The crust: rime and frost in a ragged patch, not a disc; a handful of glints in it.
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d < 2.6 + rng.uniform(-0.8, 1.2):
                c.put(x, y, 3 if rng.random() < 0.75 else 2)
            elif d < 4.8 + rng.uniform(-1.6, 1.2) and rng.random() < 0.7:
                c.put(x, y, 2)
    for _ in range(7):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(0, 3.2)
        c.put(round(cx - 0.5 + math.cos(a) * d), round(cy - 0.5 + math.sin(a) * d), 4)
    # Specks thrown clear: single texels between the spines, and a glint or two out along them.
    for _ in range(30):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(6, 21)
        c.put(round(cx + math.cos(a) * d), round(cy + math.sin(a) * d), rng.choice((1, 1, 2)))
    return c, geodesic_order(c, (cx, cy))


def crack(c, rng, x0, y0, heading, length, lip=True, wander=0.35, fork=0.0):
    """A crack: a one-texel fissure that runs on a heading, kinking to a neighbouring clean slope every few texels, with a lit lip on
    one side (the light comes from the same side everywhere, so the ground reads as split, not drawn on). Returns where it ends."""
    x, y = x0, y0
    run = 0
    lipside = side_of(math.pi * 0.25 + math.pi)  # light from the upper left
    gone = 0.0
    while gone < length:
        step = rng.randint(2, 5)
        h = heading + rng.choice((0, 0, 1, -1)) * math.pi / 8 * (1 if rng.random() < wander * 2 else 0)
        seg = ray(x, y, h, step)
        for px, py in seg[1:]:
            c.put(px, py, 5)
            if lip and rng.random() < 0.8:
                c.put(px + lipside[0], py + lipside[1], 6)
            run += 1
        x, y = seg[-1]
        gone += step
        if fork and rng.random() < fork and gone < length * 0.8:
            crack(c, rng, x, y, heading + rng.choice((-1, 1)) * math.pi / 4, (length - gone) * rng.uniform(0.3, 0.6), lip,
                  wander, 0.0)
    return x, y


def spire_crack(seed=23, size=64, radius=24):
    """Glacial Spire's warning: it has to be seen from 15 blocks, by day and at night, since it's the cue to get out. Light breaks
    through the ground: a ring of it on the reach of the hit (1.5 blocks, 24 texels), a band all the way round, a bright core three
    texels wide with dark edges and a glow either side (about eight texels in all, as wide as the old Photon ring looked), and lit fissures racing out to it from where the spike will rise. The bright
    lines and the glow along them glow (GLOW), so they read at night; the dark edges keep them sharp on pale ground by day. Grows
    from the middle; SpireClient spreads it in the first part of the warning, so the whole circle is there to dodge."""
    rng = random.Random(seed)
    c = Canvas(size)
    cx = cy = size // 2
    # Crushed ground in the middle, light coming through it.
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d < 1.6:
                c.put(x, y, 9)
            elif d < 2.6:
                c.put(x, y, 5)
            elif d < 3.6 and rng.random() < 0.5:
                c.put(x, y, 10)
    # Fissures out to the ring, evenly round so the circle reads, each its own way: a lit core, a dark lip, a glow beside them.
    n = 8
    for k in range(n):
        h = HEADINGS[(2 * k + rng.choice((0, 0, 1))) % 16]
        lit_crack(c, rng, cx, cy, h, radius + 2, fork=0.22, within=radius - 2.1)
    # The ring, all the way round, never a ruled circle: a texel or so in and out.
    for y in range(size):
        for x in range(size):
            dx, dy = x - cx + 0.5, y - cy + 0.5
            d, a = math.hypot(dx, dy), math.atan2(dy, dx)
            r = radius + 0.6 * math.sin(a * 5 + 1.3) + 0.4 * math.sin(a * 11)
            off = d - r
            if -1.5 <= off < 1.5:
                c.put(x, y, 9)
            elif -2.5 <= off < -1.5 or 1.5 <= off < 2.5:
                c.put(x, y, 5)
            elif 2.5 <= off < 4.0 or (-3.5 <= off < -2.5 and rng.random() < 0.85):
                c.put(x, y, 10)
    return c, geodesic_order(c, (cx, cy))


def lit_crack(c, rng, x0, y0, heading, length, wander=0.35, fork=0.0, within=None):
    """A crack with light coming up through it: a lit core, a dark lip on the side away from the light, and a faint glow beside
    it, on the clean slopes like `crack`. With `within`, it stops that far from where it started (where it meets the ring)."""
    x, y = x0, y0
    ox, oy = x0, y0
    gone = 0.0
    lipside = side_of(math.pi * 0.25 + math.pi)
    while gone < length:
        step = rng.randint(2, 5)
        h = heading + rng.choice((0, 0, 1, -1)) * math.pi / 8 * (1 if rng.random() < wander * 2 else 0)
        seg = ray(x, y, h, step)
        across = side_of(h)
        for px, py in seg[1:]:
            if within is not None and math.hypot(px - ox, py - oy) > within:
                return x, y
            c.put(px, py, 9)
            c.put(px - lipside[0], py - lipside[1], 5)
            for s in (1, -1):
                if rng.random() < 0.6:
                    c.put(px + across[0] * s * 2, py + across[1] * s * 2, 10)
            c.put(px + across[0], py + across[1], 10)
        x, y = seg[-1]
        gone += step
        if fork and rng.random() < fork and gone < length * 0.8:
            lit_crack(c, rng, x, y, heading + rng.choice((-1, 1)) * math.pi / 4, (length - gone) * rng.uniform(0.3, 0.6), wander, 0.0,
                      None if within is None else within - math.hypot(x - ox, y - oy))
    return x, y


def spire_burst(seed=31, size=80):
    """Where the spike came up: the ground round its foot shattered and frosted. A ragged crust of rime round the foot, short heavy
    cracks broken out of it in every direction, frost thinning out between them. A full charge shows it out to about two blocks
    (32 texels); less, only the inner part."""
    rng = random.Random(seed)
    c = Canvas(size)
    cx = cy = size // 2
    edge = [rng.uniform(7, 11) for _ in range(16)]
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            a = (math.atan2(y - cy, x - cx) / (2 * math.pi)) % 1.0
            r = edge[int(a * 16) % 16]
            if d < r:
                c.put(x, y, 3 if rng.random() < 0.55 else 2)
            elif d < r + 6 and (x + y) % 2 == 0 and rng.random() < 1.1 - (d - r) / 6:
                c.put(x, y, 1)
    for k in range(14):
        h = 2 * math.pi * (k + rng.uniform(-0.3, 0.3)) / 14
        h = min(HEADINGS + [2 * math.pi], key=lambda q: abs(q - h)) % (2 * math.pi)
        start = (round(cx + math.cos(h) * 6), round(cy + math.sin(h) * 6))
        crack(c, rng, start[0], start[1], h, rng.uniform(10, 26), fork=0.3)
    for _ in range(40):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(9, 30)
        c.put(round(cx + math.cos(a) * d), round(cy + math.sin(a) * d), rng.choice((1, 1, 2)))
    for _ in range(9):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(0, 8)
        c.put(round(cx + math.cos(a) * d), round(cy + math.sin(a) * d), 4)
    return c, geodesic_order(c, (cx, cy))


def field_frost(seed=17, size=52):
    """Frost Field's frost: one patch of the frost that races over the ground in the cone. The game lays many of them, overlapping,
    turned every way, so it is a mat, not a burst: a dithered cold sheen with ragged edges, feathered spines grown from several
    points (not all from the middle) on the clean slopes, rime only at a few roots, a glint or two and loose specks. About a block
    and a half across at its fullest."""
    rng = random.Random(seed)
    c = Canvas(size)
    cx = cy = size // 2
    edge = [rng.uniform(14, 20) for _ in range(16)]
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            a = (math.atan2(y - cy, x - cx) / (2 * math.pi)) % 1.0
            r = edge[int(a * 16) % 16]
            if (x + y) % 2 == 0 and d < r and rng.random() < 0.7 - 0.55 * d / r:
                c.put(x, y, 1)
    # Spines from all over the patch, each its own way (not out from the middle: that's a burst, a star), crossing each other.
    for k in range(10):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(2, 13)
        x0, y0 = round(cx + math.cos(a) * d), round(cy + math.sin(a) * d)
        fern(c, rng, x0, y0, rng.choice(HEADINGS), rng.uniform(7, 15), start=rng.choice((2, 3)), every=rng.choice((2, 3)),
             reach=rng.uniform(2.2, 3.4), spine=3 if k < 2 else 2)
    for _ in range(4):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(0, 12)
        c.put(round(cx + math.cos(a) * d), round(cy + math.sin(a) * d), 4)
    for _ in range(28):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(5, 22)
        c.put(round(cx + math.cos(a) * d), round(cy + math.sin(a) * d), rng.choice((1, 1, 2)))
    return c, geodesic_order(c, (cx, cy))


def value_noise(rng, size, cell):
    """Smooth blobs: random values on a coarse grid, mixed between (0..1)."""
    n = size // cell + 2
    grid = [[rng.random() for _ in range(n)] for _ in range(n)]

    def at(x, y):
        gx, gy = x / cell, y / cell
        ix, iy = int(gx), int(gy)
        fx, fy = gx - ix, gy - iy
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        top = grid[iy][ix] * (1 - fx) + grid[iy][ix + 1] * fx
        bottom = grid[iy + 1][ix] * (1 - fx) + grid[iy + 1][ix + 1] * fx
        return top * (1 - fy) + bottom * fy
    return at


def path_ice(seed=41, size=32):
    """Ice Path's ice: one patch of the glaze laid under the slider's feet. The game lays one every half a block or so as you slide,
    so they run together into a band about the trail's width (0.9 blocks either side of the middle): a blue glaze over the block,
    thicker in places, a frosted ragged rim, and the shine of ice, short streaks all one way on the diagonal (the game only turns it
    the ways that keep that diagonal), a glint at the top of each, and now and then a hairline crack. Sparse, since the patches
    overlap two or three deep and the more opaque texels of each all show."""
    rng = random.Random(seed)
    c = Canvas(size)
    cx = cy = size // 2
    edge = [rng.uniform(12.5, 14.5) for _ in range(12)]
    deep = value_noise(rng, size, 4)

    def radius(x, y):
        a = (math.atan2(y - cy, x - cx) / (2 * math.pi)) % 1.0
        f = a * 12
        i = int(f) % 12
        return edge[i] * (1 - (f - int(f))) + edge[(i + 1) % 12] * (f - int(f))
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            r = radius(x, y)
            if d < r - 0.5:
                c.put(x, y, 8 if deep(x, y) > 0.74 and d < r - 2.5 else 7)
            elif d < r + 1.8 and (x + y) % 2 == 0 and rng.random() < 0.75:
                c.put(x, y, 1)
    # The shine: short streaks down the diagonal (heading 3/4 pi: up-right to down-left on the canvas), a glint at the top end.
    for _ in range(2):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(3, 10)
        x0, y0 = round(cx + math.cos(a) * d), round(cy + math.sin(a) * d)
        pts = ray(x0, y0, 3 * math.pi / 4, rng.choice((3, 4)))
        for i, (x, y) in enumerate(pts):
            c.put(x, y, 3 if i else 4)
    # Now and then a hairline crack through the glaze, on the clean slopes.
    for _ in range(rng.choice((0, 1))):
        a, d = rng.uniform(0, 2 * math.pi), rng.uniform(2, 7)
        x0, y0 = round(cx + math.cos(a) * d), round(cy + math.sin(a) * d)
        h = rng.choice([h for h in HEADINGS if abs(math.sin(h - 3 * math.pi / 4)) > 0.5])
        for x, y in ray(x0, y0, h, rng.uniform(4, 7)):
            c.put(x, y, 2)
    return c, geodesic_order(c, (cx, cy))


def shatter_ring(seed=61, radius=72):
    """Shatter Shell's ring: where the burst reached, frost blown out over the ground to a clear ring at the edge of the knockback
    (4.5 blocks by default, 72 texels; the game moves it in or out to the configured radius). A crust of rime round the feet where
    the shell burst, faint dashed streaks of frost blown out from it, and the ring itself: unbroken, two texels of thick rime with a
    glint now and then, frost either side, short feathers of frost off it, outward. The order is the straight distance out, so it
    races out with the shock."""
    rng = random.Random(seed)
    margin = 10
    size = 2 * (radius + margin)
    c = Canvas(size)
    cx = cy = size // 2
    # The crust round the feet.
    for y in range(cy - 8, cy + 8):
        for x in range(cx - 8, cx + 8):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d < 3.5 and rng.random() < 0.7:
                c.put(x, y, 3 if rng.random() < 0.5 else 2)
            elif d < 7 and (x + y) % 2 == 0 and rng.random() < 1.1 - d / 7:
                c.put(x, y, 1)
    # Streaks blown out: sheen in dashes, a fleck of frost now and then, broken off before the ring.
    n = 11
    for k in range(n):
        h = 2 * math.pi * (k + rng.uniform(-0.35, 0.35)) / n
        h = min(HEADINGS + [2 * math.pi], key=lambda q: abs(q - h)) % (2 * math.pi)
        start, end = rng.uniform(10, 22), radius - rng.uniform(5, 22)
        on = True
        left = rng.randint(1, 4)
        for x, y in ray(cx, cy, h, end):
            left -= 1
            if left <= 0:
                on = not on
                left = rng.randint(2, 5) if on else rng.randint(1, 4)
            if on and math.hypot(x - cx, y - cy) >= start:
                c.put(x, y, 2 if rng.random() < 0.12 else 1)
    # The ring: unbroken, a texel or so in and out, never a ruled circle.
    wobble = [(rng.uniform(0.25, 0.5), rng.randint(3, 9), rng.uniform(0, 2 * math.pi)) for _ in range(3)]
    steps = int(2 * math.pi * radius * 3)
    for i in range(steps):
        a = 2 * math.pi * i / steps
        r = radius + sum(amp * math.sin(a * f + ph) for amp, f, ph in wobble)
        for dr, tone, keep in ((-3.6, 1, 0.3), (-2.6, 1, 0.45), (-1.6, 2, 0.85), (-0.6, 3, 1.0), (0.4, 3, 1.0), (1.4, 2, 0.75),
                               (2.4, 1, 0.3)):
            x, y = round(cx - 0.5 + math.cos(a) * (r + dr)), round(cy - 0.5 + math.sin(a) * (r + dr))
            if keep >= 1.0 or (rng.random() < keep and (tone != 1 or (x + y) % 2 == 0)):
                c.put(x, y, tone)
    # Glints along it, and short feathers of frost off it, outward, on the clean slopes.
    for _ in range(26):
        a = rng.uniform(0, 2 * math.pi)
        c.put(round(cx - 0.5 + math.cos(a) * radius), round(cy - 0.5 + math.sin(a) * radius), 4)
    for k in range(36):
        a = 2 * math.pi * (k + rng.uniform(-0.3, 0.3)) / 36
        h = min(HEADINGS + [2 * math.pi], key=lambda q: abs(q - a)) % (2 * math.pi)
        x0, y0 = round(cx - 0.5 + math.cos(a) * (radius + 1.5)), round(cy - 0.5 + math.sin(a) * (radius + 1.5))
        for j, (x, y) in enumerate(ray(x0, y0, h, rng.uniform(1.5, 5))):
            c.put(x, y, 2 if j < 2 else 1)
    top = (radius + margin) * 1.0
    order = {(x, y): min(255, round(math.hypot(x - cx + 0.5, y - cy + 0.5) / top * 255)) for x, y in c.filled()}
    return c, order


def white():
    OUT.mkdir(parents=True, exist_ok=True)
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(OUT / "white.png", optimize=True)


# ---------------------------------------------------------------------------------------------------- preview


def stone_tile():
    """A stand-in for stone at 16 texels: grey noise, so the preview shows how the frost sits on a textured block."""
    rng = random.Random(3)
    t = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            g = rng.choice((112, 120, 126, 126, 134, 140))
            t.putpixel((x, y), (g, g, g, 255))
    return t


def preview(name, art, when, reaches=(0.37, 0.65, 1.0), steps=(0.25, 0.5, 1.0)):
    """On stone, 8x nearest: across, three reaches (light to full); down, the spread at each."""
    size = art.width
    tile = stone_tile()
    panels = []
    for reach in reaches:
        col = []
        for s in steps:
            ground = Image.new("RGBA", (size, size))
            for y in range(0, size, 16):
                for x in range(0, size, 16):
                    ground.paste(tile, (x, y))
            layer = Image.new("RGBA", (size, size), NONE)
            for y in range(size):
                for x in range(size):
                    o = when.getpixel((x, y)) / 255
                    if art.getpixel((x, y))[3] and o <= reach * s:
                        layer.putpixel((x, y), art.getpixel((x, y)))
            col.append(Image.alpha_composite(ground, layer).resize((size * 8, size * 8), Image.NEAREST))
        panels.append(col)
    sheet = Image.new("RGB", (len(panels) * (size * 8 + 8), len(steps) * (size * 8 + 8)), (24, 24, 28))
    for i, col in enumerate(panels):
        for j, p in enumerate(col):
            sheet.paste(p.convert("RGB"), (i * (size * 8 + 8), j * (size * 8 + 8)))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW / f"{name}.png")
    print("preview", (PREVIEW / f"{name}.png").relative_to(ROOT))


PATTERNS = {"lance_frost": lance_frost, "spire_crack": spire_crack, "spire_burst": spire_burst, "field_frost": field_frost,
            "field_frost_b": lambda: field_frost(seed=29), "path_ice": path_ice, "path_ice_b": lambda: path_ice(seed=53),
            "shatter_ring": shatter_ring}


# A faint glow all over a mark (a minimum light): Ice Path's ice slows whoever steps on it, so it has to be seen on dark ground at
# night, but it must not look like a light source.
GLOW_FLOOR = {"path_ice": 6, "path_ice_b": 6}


def main(args):
    white()
    for name, make in PATTERNS.items():
        canvas, order = make()
        art, when = save(name, canvas, order, GLOW_FLOOR.get(name, 0))
        if "--preview" in args:
            preview(name, art, when)


if __name__ == "__main__":
    main(sys.argv[1:])
