"""Rime's marks on blocks: pixel art on the blocks' own grid, 16 texels a block, hard pixels, so the block itself looks frozen.

    python3 -I tools/blockbench/build_block_marks.py            write them
    python3 -I tools/blockbench/build_block_marks.py --preview  also run/marks_preview/<name>.png: on stone at 8x, as it spreads

Writes, in src/main/resources/assets/kegare/textures/marks/
    <name>.png        the art: every texel is one of the few palette colours below (alpha too), nothing blended or smoothed
    <name>_order.png  when each texel comes and goes: grey 0 forms first and melts last, 255 forms last and melts first
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
PALETTE = (NONE, FILM, FROST, RIME, GLINT)

# 16 headings, so every line is one of the clean pixel slopes (flat, 1:2, 1:1, 2:1) instead of a ragged stair.
HEADINGS = [i * math.pi / 8 for i in range(16)]


class Canvas:
    """Tones (indices into PALETTE) on a square grid; a texel keeps the strongest tone put on it."""

    def __init__(self, size):
        self.size = size
        self.tone = [[0] * size for _ in range(size)]

    def put(self, x, y, t):
        if 0 <= x < self.size and 0 <= y < self.size and self.tone[y][x] < t:
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


def save(name, canvas, order):
    size = canvas.size
    art = Image.new("RGBA", (size, size), NONE)
    when = Image.new("L", (size, size), 0)
    for (x, y), o in order.items():
        art.putpixel((x, y), PALETTE[canvas.tone[y][x]])
        when.putpixel((x, y), o)
    OUT.mkdir(parents=True, exist_ok=True)
    art.save(OUT / f"{name}.png", optimize=True)
    when.save(OUT / f"{name}_order.png", optimize=True)
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


PATTERNS = {"lance_frost": lance_frost}


def main(args):
    white()
    for name, make in PATTERNS.items():
        canvas, order = make()
        art, when = save(name, canvas, order)
        if "--preview" in args:
            preview(name, art, when)


if __name__ == "__main__":
    main(sys.argv[1:])
