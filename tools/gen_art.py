#!/usr/bin/env python3
"""Generate Kegare's GUI and item art: hand-drawn-looking pixel art, made procedurally so it can be regenerated.

Usage (from anywhere):  python3 tools/gen_art.py [--preview out.png]
Requires Pillow. Writes into src/main/resources/assets/kegare/textures/.

Art direction ("defiled ink"):
  - frames: blackened cursed iron, lit from the top-left with a violet rim, mottled like hand-placed pixels
  - surfaces: smoky plum-black ink, seamless (nine-slice edges/centres TILE in 1.21.1)
  - accents: Malice violet, ofuda paper and a red seal
  - rarity cards: iron / blue steel / amethyst / gold leaf, ornate corners growing with rarity
  - emblems & icons: one hue each, dark vignette, ink outline, bright rim light, brush strokes with wobble
Techniques (not looks) studied from other JJK-inspired mods' GUIs. Every asset here is original.
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "kegare" / "textures"


# ---------------------------------------------------------------------------------------------------- colors

def hexc(h: str, a: int = 255) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def ramp(*hexes: str) -> list[tuple[int, int, int, int]]:
    return [hexc(h) for h in hexes]


INK = hexc("#0b0810")
CLEAR = (0, 0, 0, 0)
PLUM = ramp("#0f0a13", "#140e19", "#19121f", "#1e1625", "#251b2d")
IRON = ramp("#141019", "#211a28", "#2f2538", "#40334c", "#5a4a6c", "#7d6a94", "#ab98c4")
MALICE = ramp("#1d0a2b", "#36124f", "#55207c", "#7a32ad", "#9d4edd", "#c48cf5", "#ecd6ff")
GOLD = ramp("#2a1806", "#4f300b", "#80520f", "#b87c1a", "#e5ac35", "#ffd76e", "#fff4c8")
STEEL = ramp("#0a1424", "#132a47", "#1e4370", "#2f66a3", "#4c8fd0", "#86bdf0", "#d8eeff")
SILVER = ramp("#141416", "#24242a", "#36363e", "#4d4d57", "#6c6c78", "#9a9aa6", "#d6d6de")
JADE = ramp("#06160f", "#0d2a1d", "#16442f", "#226646", "#3a9468", "#72c99a", "#cff5df")
PAPER = ramp("#b8a77c", "#cdbd93", "#ddd0ad", "#ebe1c4", "#f6efdb")
SEAL = ramp("#5c0f0c", "#8c1a14", "#b3261e", "#d94a3a", "#f08a76")
ICE = ramp("#081428", "#0f2d55", "#1a5590", "#3a8fcc", "#79c8ec", "#c2eefa", "#f2fdff")  # Rime: deep blue to white


def pick(r: list, t: float):
    """Nearest color on a ramp for t in [0,1]."""
    t = min(max(t, 0.0), 1.0)
    return r[min(len(r) - 1, int(round(t * (len(r) - 1))))]


BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def dither(r: list, t: float, x: int, y: int):
    """Ordered-dither between ramp steps: the classic hand-pixelled gradient."""
    t = min(max(t, 0.0), 1.0) * (len(r) - 1)
    lo = int(math.floor(t))
    frac = t - lo
    if lo >= len(r) - 1:
        return r[-1]
    return r[lo + 1] if frac > (BAYER4[y % 4][x % 4] + 0.5) / 16 else r[lo]


# ---------------------------------------------------------------------------------------------------- noise

class PeriodicNoise:
    """Value noise that tiles with the given period (so nine-slice edges and centres repeat seamlessly)."""

    def __init__(self, seed: int, period_x: int, period_y: int, cell: int = 4):
        self.rng = random.Random(seed)
        self.px, self.py, self.cell = period_x, period_y, cell
        self.gx = max(1, period_x // cell)
        self.gy = max(1, period_y // cell)
        self.grid = [[self.rng.random() for _ in range(self.gx)] for _ in range(self.gy)]

    def at(self, x: float, y: float) -> float:
        fx = (x % self.px) / self.px * self.gx
        fy = (y % self.py) / self.py * self.gy
        x0, y0 = int(fx) % self.gx, int(fy) % self.gy
        x1, y1 = (x0 + 1) % self.gx, (y0 + 1) % self.gy
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = self.grid[y0][x0] * (1 - tx) + self.grid[y0][x1] * tx
        b = self.grid[y1][x0] * (1 - tx) + self.grid[y1][x1] * tx
        return a * (1 - ty) + b * ty


def fbm(layers: list[PeriodicNoise], x: float, y: float) -> float:
    total, weight, amp = 0.0, 0.0, 1.0
    for layer in layers:
        total += layer.at(x, y) * amp
        weight += amp
        amp *= 0.5
    return total / weight


def noise_stack(seed: int, px: int, py: int, cells=(8, 4, 2)) -> list[PeriodicNoise]:
    return [PeriodicNoise(seed + i * 101, px, py, c) for i, c in enumerate(cells)]


# ---------------------------------------------------------------------------------------------------- image helpers

def new(w: int, h: int, fill=CLEAR) -> Image.Image:
    return Image.new("RGBA", (w, h), fill)


def put(img: Image.Image, x: int, y: int, c) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def opaque(img: Image.Image, x: int, y: int) -> bool:
    return 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] > 0


def outline(img: Image.Image, color=INK) -> Image.Image:
    """1px outline around every opaque shape (outside it)."""
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if opaque(img, x, y):
                continue
            if any(opaque(img, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), color)
    return out


def rim_light(img: Image.Image, color, light=(-1, -1)) -> Image.Image:
    """Brighten the edge pixels facing the light (top-left by default): the 'neon rim' read."""
    out = img.copy()
    lx, ly = light
    for y in range(img.height):
        for x in range(img.width):
            if not opaque(img, x, y) or img.getpixel((x, y)) == INK:
                continue
            nx, ny = x + lx, y + ly
            if not opaque(img, nx, ny) or img.getpixel((nx, ny)) == INK:
                out.putpixel((x, y), color)
    return out


def paste(dst: Image.Image, src: Image.Image, ox: int = 0, oy: int = 0) -> None:
    dst.alpha_composite(src, (ox, oy))


def from_grid(grid: list[str], palette: dict) -> Image.Image:
    bad = [i for i, row in enumerate(grid) if len(row) != len(grid[0])]
    assert not bad, f"grid rows {bad} are not {len(grid[0])} wide"
    img = new(len(grid[0]), len(grid))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img


# ---------------------------------------------------------------------------------------------------- brush

def brush(img: Image.Image, pts: list[tuple[float, float]], width: float, colors: list, seed: int,
          taper: tuple[float, float] = (0.35, 0.25), dry: float = 0.0, wobble: float = 0.0) -> None:
    """A tapered ink stroke along a polyline, with optional hand wobble and dry-brush gaps.
    colors: a ramp from stroke edge (index 0) to core (last)."""
    rng = random.Random(seed)
    wob = PeriodicNoise(seed + 7, 64, 64, 8)
    gaps = PeriodicNoise(seed + 13, 32, 32, 2)
    # Resample into a dense list of (x, y, t).
    dense = []
    total = sum(math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1)) or 1.0
    acc = 0.0
    for i in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        seg = math.dist(pts[i], pts[i + 1])
        steps = max(1, int(seg * 3))
        for s in range(steps):
            f = s / steps
            t = (acc + seg * f) / total
            jx = (wob.at(t * 60, 3) - 0.5) * 2 * wobble
            jy = (wob.at(5, t * 60) - 0.5) * 2 * wobble
            dense.append((x0 + (x1 - x0) * f + jx, y0 + (y1 - y0) * f + jy, t))
        acc += seg
    dense.append((*pts[-1], 1.0))
    xs = [p[0] for p in dense]
    ys = [p[1] for p in dense]
    pad = width + 2
    for y in range(max(0, int(min(ys) - pad)), min(img.height, int(max(ys) + pad) + 1)):
        for x in range(max(0, int(min(xs) - pad)), min(img.width, int(max(xs) + pad) + 1)):
            best, bt = 1e9, 0.0
            for (px, py, t) in dense[::2]:
                d = (px - x - 0.5 + 0.5) ** 2 + (py - y) ** 2
                if d < best:
                    best, bt = d, t
            dist = math.sqrt(best)
            w = width
            if bt < taper[0]:
                w *= 0.35 + 0.65 * (bt / taper[0])
            if bt > 1 - taper[1]:
                w *= 0.35 + 0.65 * ((1 - bt) / taper[1])
            if dist > w:
                continue
            if dry and gaps.at(x * 1.7 + bt * 40, y * 1.7) < dry and dist > w * 0.25:
                continue
            core = 1 - dist / max(w, 0.01)
            put(img, x, y, pick(colors, core + (rng.random() - 0.5) * 0.15))


def ring(img: Image.Image, cx: float, cy: float, r: float, width: float, colors: list, seed: int,
         start: float = 0, sweep: float = 360, dry: float = 0.0, wobble: float = 0.6) -> None:
    pts = []
    n = max(12, int(r * sweep / 30))
    for i in range(n + 1):
        a = math.radians(start + sweep * i / n)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    brush(img, pts, width, colors, seed, taper=(0.12, 0.12), dry=dry, wobble=wobble)


def vignette(size: int, r: list, seed: int, center=(0.45, 0.42)) -> Image.Image:
    """Square emblem background: dark corners, a soft glow toward the upper-middle, dithered."""
    img = new(size, size)
    n = noise_stack(seed, size, size, (8, 4))
    for y in range(size):
        for x in range(size):
            dx = x / size - center[0]
            dy = y / size - center[1]
            t = 1 - min(1.0, math.hypot(dx, dy) * 1.55)
            t = t * 0.62 + (fbm(n, x, y) - 0.5) * 0.25
            img.putpixel((x, y), dither(r[:5], t, x, y))
    return img


# ---------------------------------------------------------------------------------------------------- frames

def frame(w: int, h: int, band: int, metal: list, interior, seed: int, tile_x: int, tile_y: int,
          rim=None, rivets: bool = False, corner_ornament=None, glints: bool = True) -> Image.Image:
    """A beveled metal frame around an interior, seamless along the nine-slice tile period.
    band: frame thickness (excluding the 1px ink outline and inner ink line)."""
    img = new(w, h)
    mottle = noise_stack(seed, tile_x, tile_y, (4, 2))
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            # rounded outer corners
            if (x in (0, w - 1)) and (y in (0, h - 1)):
                continue
            if d == 0:
                img.putpixel((x, y), INK)
                continue
            if d <= band:
                top_left = (y <= x and y <= h - 1 - y and y < w - 1 - x) or (x < y and x <= w - 1 - x and x < h - 1 - y)
                m = fbm(mottle, x, y)
                t = 0.42 + (m - 0.5) * 0.55
                if d == 1:
                    t = 0.82 if top_left else 0.16      # outer bevel: lit top-left, shadow bottom-right
                elif d == band:
                    t = 0.24 if top_left else 0.12      # inner lip
                elif top_left:
                    t += 0.12
                else:
                    t -= 0.12
                c = dither(metal, t, x, y)
                if rim is not None and d == 1 and top_left and (x + y) % 7 != 0:
                    c = rim
                img.putpixel((x, y), c)
                continue
            if d == band + 1:
                img.putpixel((x, y), INK)
                continue
            img.putpixel((x, y), interior(x, y))
    if glints:
        # short diagonal highlight streaks on the top band (period = tile_x so they tile)
        g = random.Random(seed + 3)
        for k in range(max(1, tile_x // 24)):
            gx = band + 2 + g.randrange(max(1, tile_x - 4)) + k * 24
            for i in range(min(band - 1, 3)):
                for rep in range(0, w, tile_x):
                    if 2 <= rep + gx + i <= w - 3:  # never over the ink outline or the rounded corners
                        put(img, rep + gx + i, 1 + i, metal[-1])
    if rivets:
        for cx, cy in ((band // 2 + 1, band // 2 + 1), (w - band // 2 - 2, band // 2 + 1),
                       (band // 2 + 1, h - band // 2 - 2), (w - band // 2 - 2, h - band // 2 - 2)):
            for dx, dy, c in ((0, 0, metal[5]), (1, 0, metal[3]), (0, 1, metal[3]), (1, 1, metal[1]),
                              (-1, 0, INK), (0, -1, INK), (2, 0, INK), (0, 2, INK), (1, -1, INK), (-1, 1, INK),
                              (2, 1, INK), (1, 2, INK)):
                put(img, cx + dx, cy + dy, c)
    if corner_ornament:
        corner_ornament(img)
    return img


def plum_interior(seed: int, tile_x: int, tile_y: int, r=PLUM, contrast: float = 0.55):
    n = noise_stack(seed, tile_x, tile_y, (8, 4, 2))

    def f(x: int, y: int):
        return dither(r, 0.42 + (fbm(n, x, y) - 0.5) * contrast * 2, x, y)
    return f


# ---------------------------------------------------------------------------------------------------- GUI sprites

def panel() -> Image.Image:
    size, border = 48, 12
    tile = size - 2 * border
    img = frame(size, size, 4, IRON, plum_interior(11, tile, tile), seed=5, tile_x=tile, tile_y=tile,
                rim=MALICE[4], rivets=True)
    return img


def card(metal: list, accent, seed: int, ornate: int) -> Image.Image:
    """Rarity card. ornate: 0 = plain corners .. 3 = gold-leaf flourishes and gems.
    Every ornament stays inside the 12px nine-slice corners (edges and centre tile, so anything there repeats)."""
    w, h, border = 36, 48, 12
    tx, ty = w - 2 * border, h - 2 * border
    body = noise_stack(seed + 9, tx, ty, (4, 2))

    def interior(x: int, y: int):
        t = 0.55 - (y / h) * 0.35 + (fbm(body, x, y) - 0.5) * 0.3
        return dither(PLUM, t, x, y)

    def corners(img: Image.Image) -> None:
        if ornate == 0:
            return
        hi, mid = metal[-1], metal[4]
        for (cx, cy, sx, sy) in ((5, 5, 1, 1), (w - 6, 5, -1, 1), (5, h - 6, 1, -1), (w - 6, h - 6, -1, -1)):
            # hand-drawn L bracket with a curl
            arm = 3 + ornate  # the curl ends at cx + arm <= 11, inside the corner
            for i in range(arm):
                put(img, cx + sx * i, cy, mid if i else hi)
                put(img, cx, cy + sy * i, mid if i else hi)
            put(img, cx + sx * arm, cy + sy, hi)
            put(img, cx + sx, cy + sy * arm, hi)
            if ornate >= 2:
                put(img, cx + sx, cy + sy, accent)
            if ornate >= 3:
                put(img, cx + sx * 2, cy + sy * 2, accent)
                put(img, cx + sx * 2, cy + sy, metal[5])
                put(img, cx + sx, cy + sy * 2, metal[5])

    return frame(w, h, 3, metal, interior, seed=seed, tile_x=tx, tile_y=ty, rim=metal[-1] if ornate >= 2 else None,
                 corner_ornament=corners, glints=ornate >= 1)


def tab(selected: bool) -> Image.Image:
    w, h, border = 24, 16, 5
    r = ramp("#2a1f33", "#33263e", "#3d2e4a", "#463554", "#4f3c5e") if selected else PLUM
    img = frame(w, h, 2, IRON, plum_interior(21 if selected else 23, w - 2 * border, h - 2 * border, r, 0.3),
                seed=17, tile_x=w - 2 * border, tile_y=h - 2 * border, rim=MALICE[5] if selected else None, glints=False)
    if selected:
        # Malice underline and a small red seal on the left edge (both outside the tiled middle or uniform)
        for x in range(3, w - 3):
            put(img, x, h - 4, MALICE[4])
        for dx, dy, c in ((3, 4, SEAL[2]), (4, 4, SEAL[3]), (3, 5, SEAL[1]), (4, 5, SEAL[2])):
            put(img, dx, dy, c)
    return img


def radial_slot(selected: bool) -> Image.Image:
    size = 24
    metal = GOLD if selected else IRON
    img = new(size, size)
    gloss = noise_stack(31, size, size, (4,))
    for y in range(size):
        for x in range(size):
            # octagon: cut the corners
            cx, cy = min(x, size - 1 - x), min(y, size - 1 - y)
            if cx + cy < 4:
                continue
            d = min(cx, cy, cx + cy - 4)
            top_left = x + y < size
            if d == 0:
                c = INK
            elif d <= 2:
                c = metal[5] if (d == 1 and top_left) else metal[3] if top_left else metal[1]
            elif d == 3:
                c = INK
            else:
                # glossy dark interior: a soft highlight arc top-left
                g = 1 - math.hypot(x - 8, y - 8) / 18
                t = 0.25 + g * 0.35 + (fbm(gloss, x, y) - 0.5) * 0.15
                c = dither(MALICE[:4] if selected else PLUM, t, x, y)
            img.putpixel((x, y), c)
    for (x, y) in ((6, 7), (7, 6), (8, 6)):  # specular glint
        put(img, x, y, (255, 255, 255, 120))
    return img


def sparkle() -> Image.Image:
    grid = [
        "....W....",
        "....W....",
        "...WYW...",
        "..WYYYW..",
        "WWYYWYYWW",
        "..WYYYW..",
        "...WYW...",
        "....W....",
        "....W....",
    ]
    return from_grid(grid, {"W": (255, 255, 255, 255), "Y": (255, 255, 255, 190)})


def severed_emblem() -> Image.Image:
    size = 48
    img = new(size, size)
    ash = ramp("#2a2a2e", "#55555c", "#8d8d95", "#c2c2c9", "#e8e8ee")
    # two broken arcs of dry-brush ink, a gap where Malice would have flowed
    ring(img, 23.5, 23.5, 16.5, 3.2, ash, seed=41, start=-60, sweep=150, dry=0.33)
    ring(img, 23.5, 23.5, 16.5, 3.2, ash, seed=43, start=118, sweep=128, dry=0.33)
    # a thin cut through the ring
    brush(img, [(8, 40), (40, 8)], 0.9, [SEAL[1], SEAL[2], SEAL[3]], seed=47, taper=(0.4, 0.4))
    # ink splatter
    rng = random.Random(49)
    for _ in range(14):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(19, 23)
        put(img, int(23.5 + math.cos(a) * r), int(23.5 + math.sin(a) * r), ash[rng.randrange(1, 3)])
    # the empty core: a dull seal dot
    for y in range(21, 27):
        for x in range(21, 27):
            if math.hypot(x - 23.5, y - 23.5) <= 2.6:
                put(img, x, y, SEAL[1] if (x + y) % 3 else SEAL[2])
    return outline(img)


def divider() -> Image.Image:
    """A horizontal ink brush stroke (3-slice: 10px caps, the middle 44px tiles seamlessly).
    Left cap: the blot where the brush landed. Right cap: the stroke lifts off to a point."""
    w, h, cap = 64, 7, 10
    mid = w - 2 * cap
    img = new(w, h)
    wob = PeriodicNoise(141, mid, 8, 11)
    dry = PeriodicNoise(143, mid, 8, 3)
    cy = 3.0
    for x in range(w):
        if x < cap:
            thick = 1.1 + 1.3 * (1 - abs(x - 3.5) / 6.5) if x < 8 else 1.25
        elif x >= w - cap:
            thick = 1.25 * max(0.0, (w - 1 - x) / cap) ** 0.7
        else:
            thick = 1.0 + 0.6 * wob.at(x - cap, 0)
        centre = cy + (wob.at(x - cap if cap <= x < w - cap else 0, 4) - 0.5) * 0.6
        for y in range(h):
            dist = abs(y - centre)
            if dist > thick:
                continue
            core = 1 - dist / max(thick, 0.01)
            if cap <= x < w - cap and dist > thick * 0.3 and dry.at(x - cap, y * 2) < 0.18:
                continue  # dry-brush streaks
            img.putpixel((x, y), pick(MALICE[2:], 0.25 + core * 0.75))
    # a drop of ink flicked off the blot
    put(img, 1, 6, MALICE[2])
    return img


def reveal_bg() -> Image.Image:
    """128x128 seamless smoky ink for full-screen backgrounds (very low contrast so text stays readable).
    Broad soft clouds plus faint wisps; no hard edges, so the tiling doesn't read as wallpaper."""
    size = 128
    img = new(size, size)
    n = noise_stack(61, size, size, (64, 32, 16, 8))
    wisp = noise_stack(67, size, size, (32, 16))
    r = ramp("#0a070d", "#0e0a12", "#120c17", "#17101d", "#1d1425", "#26193a")
    for y in range(size):
        for x in range(size):
            t = 0.33 + (fbm(n, x, y) - 0.5) * 1.0
            v = fbm(wisp, x + 7, y)
            t += max(0.0, 0.06 - abs(v - 0.5)) * 2.5  # wisps: a soft ridge, not a line
            img.putpixel((x, y), dither(r, t, x, y))
    return img


def button(state: str) -> Image.Image:
    """Nine-slice button (border 4): 'normal' cursed iron, 'highlighted' gold-lit with a lighter well,
    'disabled' dull and unlit."""
    w, h, border = 40, 20, 4
    tx, ty = w - 2 * border, h - 2 * border
    if state == "highlighted":
        well = ramp("#2a1f33", "#33263e", "#3d2e4a", "#463554", "#4f3c5e")
        img = frame(w, h, 2, GOLD, plum_interior(161, tx, ty, well, 0.35), seed=163, tile_x=tx, tile_y=ty,
                    rim=GOLD[6], glints=False)
    elif state == "disabled":
        img = frame(w, h, 2, SILVER[:5], plum_interior(165, tx, ty, PLUM[:3], 0.2), seed=167, tile_x=tx, tile_y=ty,
                    glints=False)
    else:
        img = frame(w, h, 2, IRON, plum_interior(169, tx, ty, PLUM, 0.45), seed=171, tile_x=tx, tile_y=ty,
                    rim=MALICE[4], glints=False)
    return img


# ---------------------------------------------------------------------------------------------------- HUD

def malice_bar() -> Image.Image:
    """32x30, three rows of 10. 5px caps; the middle 22px tiles horizontally.

    Rows 0-9: the empty bar (frame, studs and the dark well). Rows 10-19: the fill's shading in greyscale, only inside
    the well (transparent elsewhere); the game tints it with the current innate technique's Malice color. Rows 20-29:
    a white highlight (top shine and drifting wisps) drawn untinted over the fill, so every color keeps its gloss.
    """
    w, h, cap = 32, 30, 5
    img = new(w, h)
    fill = noise_stack(71, w - 2 * cap, 10, (6, 3))
    grey = [(v, v, v, 255) for v in (150, 172, 194, 214, 234, 255)]
    for y in range(10):
        for x in range(w):
            d = min(x, y, w - 1 - x, 9 - y)
            if (x in (0, w - 1)) and (y in (0, 9)):
                continue
            if d == 0:
                c = INK
            elif d == 1:
                c = IRON[5] if (y == 1 or x == 1) else IRON[2]
            elif d == 2:
                c = INK
            else:
                c = dither(PLUM, 0.2 + (y / 10) * 0.2, x, y)
            img.putpixel((x, y), c)
            if d >= 3:
                # Fill shading: bright top, deeper bottom, wisps drifting through (as greyscale for tinting)
                n = fbm(fill, x - cap, y)
                t = 0.95 - (y - 3) / 4 * 0.6 + (n - 0.5) * 0.5
                img.putpixel((x, 10 + y), dither(grey, t, x, y))
                shine = 0.55 if y == 3 else 0.2 if y == 4 else 0.0
                if n > 0.68:
                    shine = max(shine, 0.22)
                if shine > 0:
                    img.putpixel((x, 20 + y), (255, 255, 255, int(255 * shine)))
    # cap ornaments: a small diamond stud on each end (frame only)
    for sx in (2, w - 3):
        for dy, c in ((3, IRON[6]), (4, IRON[4]), (5, IRON[4]), (6, IRON[2])):
            put(img, sx, dy, c)
    return img


def hud_slot(selected: bool) -> Image.Image:
    """20x20: ink outline + 1px metal bevel, then a glossy 16x16 well the ability icon is drawn into."""
    size = 20
    metal = GOLD if selected else IRON
    img = new(size, size)
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if (x in (0, size - 1)) and (y in (0, size - 1)):
                continue
            top_left = x + y < size
            if d == 0:
                c = INK
            elif d == 1:
                c = metal[5] if top_left else metal[2]
            elif d == 2:
                c = metal[1] if selected else INK
            else:
                g = 1 - math.hypot(x - 6, y - 6) / 16
                c = dither(MALICE[:3] if selected else PLUM, 0.15 + g * 0.5, x, y)
            img.putpixel((x, y), c)
    put(img, 4, 4, (255, 255, 255, 90))
    put(img, 5, 4, (255, 255, 255, 60))
    return img


# ---------------------------------------------------------------------------------------------------- icons (hand-pixelled 16x16)

def icon(grid: list[str], r: list) -> Image.Image:
    pal = {str(i): r[i] for i in range(len(r))}
    pal["K"] = INK
    return from_grid(grid, pal)


ICON_DASH = [
    "................",
    "................",
    "........KK......",
    "........K5K.....",
    "..K3333.K66K....",
    "........K656K...",
    ".K444444K6656K..",
    "..K55555K66665K.",
    ".K444444K6656K..",
    "........K656K...",
    "..K3333.K66K....",
    "........K5K.....",
    "........KK......",
    "................",
    "................",
    "................",
]
ICON_BURST = [
    "................",
    ".......KK.......",
    "..K....K6K...K..",
    ".K6K...K5K..K6K.",
    "..K5K..K4K.K5K..",
    "...K4KKK4KKK4K..",
    "....KK6665KK....",
    ".KKKK656665KKKK.",
    "K6544666665K456K",
    ".KKKK565556KKKK.",
    "....KK5555KK....",
    "...K4KKK4KKK4K..",
    "..K5K..K4K..K5K.",
    ".K6K...K5K...K6K",
    "..K....K6K....K.",
    ".......KK.......",
]
ICON_HOVER = [
    ".......KK.......",
    "......K66K......",
    ".....K6556K.....",
    "....K655556K....",
    "...KKK6556KKK...",
    ".....K6556K.....",
    "....KK6556KK....",
    "...K66K55K66K...",
    "..K6556KK6556K..",
    ".K655556K55556K.",
    "..KKK55K..K5KKK.",
    "....K55K..K55K..",
    "....K44K..K44K..",
    "...K3333KK3333K.",
    "..K33..33K3..33K",
    "...KK..KK.KK.KK.",
]
ICON_AURA = [
    "........K.......",
    ".......K6K......",
    "......K656K..K..",
    "..K...K565K.K6K.",
    ".K6K.K45654KK5K.",
    ".K5KK4556554K5K.",
    ".K45K556665K54K.",
    "..K4556666655K..",
    "..K4556666655K..",
    ".K455566665554K.",
    ".K445556665544K.",
    "..K44555555444K.",
    "...K334444433K..",
    "....KK33333KK...",
    "......KKKKK.....",
    "................",
]
ICON_SHOCKWAVE = [
    "....KKKKKKKK....",
    "..KK55555555KK..",
    ".K55KKKKKKKK55K.",
    "K5KK.KKKKKK.KK5K",
    "K5K.K666666K.K5K",
    "K5KK66KKKK66KK5K",
    "K5K66K5555K66K5K",
    "K5K6K566665K6K5K",
    "K5K6K566665K6K5K",
    "K5K66K5555K66K5K",
    "K5KK66KKKK66KK5K",
    "K5K.K666666K.K5K",
    "K5KK.KKKKKK.KK5K",
    ".K55KKKKKKKK55K.",
    "..KK55555555KK..",
    "....KKKKKKKK....",
]


def talisman_item() -> Image.Image:
    grid = [
        "...KKKKKKKKKK...",
        "...K44333344K...",
        "...K4i4ii4i4K...",
        "...K44iiii44K...",
        "...K4ii44ii4K...",
        "...K44i44i44K...",
        "...K44rRRr44K...",
        "...K4rRWWRr4K...",
        "...K4rRWWRr4K...",
        "...K44rRRr44K...",
        "...K4i44i4i4K...",
        "...K44iiii44K...",
        "...K4i4ii4i4K...",
        "...K444ii444K...",
        "...K3K3333K3K...",
        "...KK.KKKK.KK...",
    ]
    pal = {"K": hexc("#2b2129"), "3": PAPER[1], "4": PAPER[3], "i": hexc("#1c1a1f"),
           "r": SEAL[1], "R": SEAL[2], "W": SEAL[4]}
    img = from_grid(grid, pal)
    rng = random.Random(81)
    for y in range(img.height):  # paper fibres
        for x in range(img.width):
            if img.getpixel((x, y)) == PAPER[3] and rng.random() < 0.22:
                img.putpixel((x, y), PAPER[2])
    return img


# ---------------------------------------------------------------------------------------------------- innate technique emblems (48x48 card art)

def emblem_kinetic() -> Image.Image:
    img = vignette(48, SILVER, seed=101)
    fg = new(48, 48)
    for i, x0 in enumerate((10, 19, 28)):  # three brush chevrons
        brush(fg, [(x0, 12), (x0 + 10, 24), (x0, 36)], 2.6 - i * 0.2, SILVER[3:], seed=103 + i, wobble=0.5)
    for y, x1 in ((15, 9), (24, 6), (33, 9)):  # speed lines
        brush(fg, [(2, y), (x1, y)], 0.8, SILVER[2:5], seed=111 + y, taper=(0.6, 0.1))
    paste(img, rim_light(outline(fg), SILVER[-1]))
    return img


def emblem_flux() -> Image.Image:
    img = vignette(48, STEEL, seed=121)
    fg = new(48, 48)
    pts = []
    for i in range(70):  # inward spiral
        a = i * 0.18
        r = 18 - i * 0.22
        pts.append((24 + math.cos(a) * r, 24 + math.sin(a) * r * 0.85))
    brush(fg, pts, 2.8, STEEL[3:], seed=123, taper=(0.15, 0.5), dry=0.12, wobble=0.4)
    for (x, y) in ((8, 10), (38, 36), (40, 12)):
        brush(fg, [(x, y), (x + 5, y - 2)], 0.8, STEEL[3:6], seed=127 + x)
    paste(img, rim_light(outline(fg), STEEL[-1]))
    return img


def emblem_prism() -> Image.Image:
    img = vignette(48, MALICE, seed=141)
    fg = new(48, 48)
    poly = [(24, 5), (35, 16), (31, 40), (17, 40), (13, 16)]

    def inside(px: float, py: float, p: list) -> bool:
        c = False
        j = len(p) - 1
        for i in range(len(p)):
            (xi, yi), (xj, yj) = p[i], p[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                c = not c
            j = i
        return c

    for y in range(48):
        for x in range(48):
            if inside(x + 0.5, y + 0.5, poly):
                # facets: left lit, centre bright, right dark
                if x < 20:
                    t = 0.82
                elif x < 26:
                    t = 0.98 if y < 22 else 0.7
                else:
                    t = 0.45
                fg.putpixel((x, y), dither(MALICE, t - (y / 48) * 0.25, x, y))
    for y in range(8, 40):  # facet edges, slightly wobbly
        put(fg, 20 + (1 if y % 9 == 0 else 0), y, MALICE[6])
        put(fg, 26, y, MALICE[3])
    paste(img, rim_light(outline(fg), MALICE[-1]))
    for (x, y) in ((9, 9), (39, 30), (36, 7)):
        paste(img, sparkle().resize((5, 5), Image.NEAREST), x, y)
    return img


def emblem_apex() -> Image.Image:
    img = vignette(48, GOLD, seed=161)
    fg = new(48, 48)
    for k in range(12):  # rays
        a = k * math.tau / 12 - math.pi / 2
        r0, r1 = 11, 21 if k % 2 == 0 else 16
        brush(fg, [(24 + math.cos(a) * r0, 24 + math.sin(a) * r0), (24 + math.cos(a) * r1, 24 + math.sin(a) * r1)],
              2.2 if k % 2 == 0 else 1.4, GOLD[3:], seed=163 + k, taper=(0.1, 0.7))
    for y in range(48):  # core
        for x in range(48):
            d = math.hypot(x - 23.5, y - 23.5)
            if d <= 8.5:
                t = 0.95 - d / 8.5 * 0.5 + (0.12 if x + y < 46 else -0.1)
                fg.putpixel((x, y), dither(GOLD, t, x, y))
    paste(img, rim_light(outline(fg), GOLD[-1]))
    return img


# ---------------------------------------------------------------------------------------------------- Rime

ICON_FROST_SHARD = [
    "................",
    "............KK..",
    "..........KK66K.",
    ".........K6665K.",
    "........K66654K.",
    ".......K66654K..",
    "......K66543K...",
    ".....K6654K.....",
    "....K6543K......",
    "...K5432K.......",
    "..K5433K........",
    "..K433K.........",
    ".K33KK..........",
    ".KKK............",
    "................",
    "................",
]
ICON_GLACIAL_LANCE = [
    "................",
    "...........KKKK.",
    "..........K6666K",
    ".........K66665K",
    "........K666654K",
    "........K66654K.",
    ".......K6654KK..",
    "......K654K.....",
    ".....K543K......",
    "....K543K.......",
    "...K432K........",
    "..K432K.........",
    ".K32KK..........",
    ".K2K............",
    "..K.............",
    "................",
]
ICON_FROST_FIELD = [
    "................",
    "..........3..5..",
    "......4.....6...",
    "...K....5.4...6.",
    "..K6K.6...5..3..",
    ".K665K..4...6...",
    ".K6665K...6...5.",
    ".K66665K5.4.6...",
    ".K6665K...6...5.",
    ".K665K..4...6...",
    "..K6K.6...5..3..",
    "...K....5.4...6.",
    "......4.....6...",
    "..........3..5..",
    "................",
    "................",
]
ICON_RIME_SHELL = [
    "................",
    "......KKKK......",
    "....KK6666KK....",
    "...K66555566K...",
    "..K6554444556K..",
    "..K6544444456K..",
    ".K654443344456K.",
    ".K654433334456K.",
    ".K654433334456K.",
    ".K654443344456K.",
    "..K6544444456K..",
    "..K6554444556K..",
    "...K66555566K...",
    "....KK6666KK....",
    "......KKKK......",
    "................",
]


def emblem_rime() -> Image.Image:
    """A six-armed snowflake of brush strokes with crystal branches over a deep-blue vignette."""
    img = vignette(48, ICE, seed=301)
    fg = new(48, 48)
    cx, cy = 23.5, 23.5
    for k in range(6):
        a = k * math.pi / 3 - math.pi / 2
        tip = (cx + math.cos(a) * 19, cy + math.sin(a) * 19)
        brush(fg, [(cx, cy), tip], 1.7, ICE[3:], seed=303 + k, taper=(0.1, 0.55), wobble=0.25)
        for side in (-1, 1):
            for at, length in ((10.5, 6.0), (15.0, 3.5)):
                b0 = (cx + math.cos(a) * at, cy + math.sin(a) * at)
                ab = a + side * 0.8
                brush(fg, [b0, (b0[0] + math.cos(ab) * length, b0[1] + math.sin(ab) * length)], 0.95, ICE[3:],
                      seed=311 + k * 4 + (side > 0) * 2 + int(at), taper=(0.2, 0.6))
    ring(fg, cx, cy, 4.0, 1.25, ICE[4:], seed=331, wobble=0.1)
    paste(img, rim_light(outline(fg), ICE[-1]))
    for (x, y) in ((8, 7), (38, 34), (37, 8)):
        paste(img, sparkle().resize((5, 5), Image.NEAREST), x, y)
    return img


def ultimate_slot() -> Image.Image:
    """26x26 gold-framed slot for the ultimate on the ability bar (a 16x16 icon sits in the middle)."""
    size = 26
    img = new(size, size)
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            if (x in (0, size - 1)) and (y in (0, size - 1)):
                continue
            top_left = x + y < size
            if d == 0:
                c = INK
            elif d <= 2:
                c = GOLD[5] if (d == 1 and top_left) else GOLD[3] if top_left else GOLD[1]
            elif d == 3:
                c = INK
            else:
                g = 1 - math.hypot(x - 8, y - 8) / 20
                c = dither(MALICE[:3], 0.12 + g * 0.45, x, y)
            img.putpixel((x, y), c)
    for (x, y) in ((5, 5), (6, 5), (5, 6)):
        put(img, x, y, (255, 255, 255, 90))
    return img


def special_badge(lit: bool) -> Image.Image:
    """9x9 "R" badge on an ability slot: lit gold when the ability has a special (R), grey when not."""
    grid = [
        ".KKKKKKK.",
        "KbbbbbbbK",
        "KbRRRbbbK",
        "KbRbbRbbK",
        "KbRRRbbbK",
        "KbRbRbbbK",
        "KbRbbRbbK",
        "KbbbbbbbK",
        ".KKKKKKK.",
    ]
    if lit:
        pal = {"K": INK, "b": GOLD[5], "R": INK}
    else:
        pal = {"K": INK, "b": IRON[2], "R": IRON[4]}
    return from_grid(grid, pal)


def env_cold() -> Image.Image:
    grid = [
        "....K.K....",
        "...KWKWK...",
        ".KK.KWK.KK.",
        "KWWKKWKKWWK",
        ".KKWKWKWKK.",
        "KWWWWCWWWWK",
        ".KKWKWKWKK.",
        "KWWKKWKKWWK",
        ".KK.KWK.KK.",
        "...KWKWK...",
        "....K.K....",
    ]
    return from_grid(grid, {"K": INK, "W": ICE[5], "C": ICE[6]})


def env_hot() -> Image.Image:
    grid = [
        ".....K.....",
        "....KRK....",
        "...KROK....",
        "...KROOK...",
        "..KROOYOK..",
        "..KROYYOK..",
        ".KROYYYYOK.",
        ".KROYWWYOK.",
        ".KROYWWYOK.",
        "..KROOOOK..",
        "...KKKKK...",
    ]
    return from_grid(grid, {"K": INK, "R": hexc("#9c2a12"), "O": hexc("#f07a22"), "Y": hexc("#ffd04a"), "W": hexc("#fff4c8")})


def lock_icon() -> Image.Image:
    grid = [
        "...KKKKK...",
        "..K55555K..",
        "..K5K.K5K..",
        "..K5K.K5K..",
        ".KKKKKKKKK.",
        ".K6666666K.",
        ".K6665666K.",
        ".K665K566K.",
        ".K6665666K.",
        ".K4444444K.",
        ".KKKKKKKKK.",
    ]
    return from_grid(grid, {"K": INK, "4": IRON[4], "5": IRON[5], "6": IRON[6]})


def freeze_pip(full: bool) -> Image.Image:
    grid = [
        "...K...",
        "..KaK..",
        ".KabcK.",
        "KabbcdK",
        ".KcdeK.",
        "..KeK..",
        "...K...",
    ]
    if full:
        pal = {"K": INK, "a": ICE[6], "b": ICE[5], "c": ICE[4], "d": ICE[3], "e": ICE[2]}
    else:
        pal = {"K": INK, "a": PLUM[4], "b": PLUM[3], "c": PLUM[2], "d": PLUM[1], "e": PLUM[1]}
    return from_grid(grid, pal)


# ---------------------------------------------------------------------------------------------------- output

def save(img: Image.Image, relative: str) -> Path:
    path = TEXTURES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"wrote {path.relative_to(ROOT)}")
    return path


def nine_slice(path: Path, w: int, h: int, border) -> None:
    if isinstance(border, int):
        b = str(border)
    else:
        b = '{"left": %d, "top": %d, "right": %d, "bottom": %d}' % border
    meta = path.with_suffix(".png.mcmeta")
    meta.write_text('{\n  "gui": {\n    "scaling": {\n      "type": "nine_slice",\n'
                    f'      "width": {w},\n      "height": {h},\n      "border": {b}\n    }}\n  }}\n}}\n')
    print(f"wrote {meta.relative_to(ROOT)}")


def sprite(img: Image.Image, name: str, border=None) -> None:
    path = save(img, f"gui/sprites/{name}.png")
    meta = path.with_suffix(".png.mcmeta")
    if border is not None:
        nine_slice(path, img.width, img.height, border)
    elif meta.exists():
        meta.unlink()


def build() -> dict[str, Image.Image]:
    out = {}
    out["panel"] = panel()
    out["card_common"] = card(SILVER, SILVER[-1], 201, 0)
    out["card_rare"] = card(STEEL, STEEL[-1], 203, 1)
    out["card_epic"] = card(MALICE, MALICE[-1], 205, 2)
    out["card_legendary"] = card(GOLD, hexc("#ff5a4e"), 207, 3)
    out["tab"] = tab(False)
    out["tab_selected"] = tab(True)
    out["radial_slot"] = radial_slot(False)
    out["radial_slot_selected"] = radial_slot(True)
    out["sparkle"] = sparkle()
    out["severed_emblem"] = severed_emblem()
    out["divider"] = divider()
    out["button"] = button("normal")
    out["button_highlighted"] = button("highlighted")
    out["button_disabled"] = button("disabled")
    out["env_cold"] = env_cold()
    out["env_hot"] = env_hot()
    out["lock"] = lock_icon()
    out["freeze_pip"] = freeze_pip(True)
    out["freeze_pip_empty"] = freeze_pip(False)
    out["badge_special"] = special_badge(True)
    out["badge_special_none"] = special_badge(False)
    return out


def main() -> None:
    sprites = build()
    borders = {"panel": 12, "card_common": 12, "card_rare": 12, "card_epic": 12, "card_legendary": 12,
               "tab": 5, "tab_selected": 5, "radial_slot": 7, "radial_slot_selected": 7, "divider": (10, 0, 10, 0),
               "button": 4, "button_highlighted": 4, "button_disabled": 4}
    for name, img in sprites.items():
        sprite(img, name, borders.get(name))
    stale = TEXTURES / "gui" / "sprites" / "card.png"  # the old greyscale, tinted placeholder
    for p in (stale, stale.with_suffix(".png.mcmeta")):
        if p.exists():
            p.unlink()
            print(f"removed {p.relative_to(ROOT)}")

    save(reveal_bg(), "gui/reveal_bg.png")
    save(malice_bar(), "gui/malice_bar.png")
    save(hud_slot(False), "gui/ability_slot.png")
    save(hud_slot(True), "gui/ability_slot_selected.png")
    save(ultimate_slot(), "gui/ultimate_slot.png")
    save(talisman_item(), "item/purification_talisman.png")
    # Rime's projectiles render as these items in flight
    save(icon(ICON_FROST_SHARD, ICE), "item/frost_shard.png")
    save(icon(ICON_GLACIAL_LANCE, ICE), "item/glacial_lance.png")

    icons = {"test_dash": icon(ICON_DASH, MALICE), "test_burst": icon(ICON_BURST, MALICE),
             "test_hover": icon(ICON_HOVER, MALICE), "test_aura": icon(ICON_AURA, MALICE),
             "test_shockwave": icon(ICON_SHOCKWAVE, MALICE),
             "frost_shard": icon(ICON_FROST_SHARD, ICE), "glacial_lance": icon(ICON_GLACIAL_LANCE, ICE),
             "frost_field": icon(ICON_FROST_FIELD, ICE), "rime_shell": icon(ICON_RIME_SHELL, ICE)}
    for name, img in icons.items():
        save(img, f"gui/ability/{name}.png")
    emblems = {"test_kinetic": emblem_kinetic(), "test_flux": emblem_flux(),
               "test_prism": emblem_prism(), "test_apex": emblem_apex(), "rime": emblem_rime()}
    for name, img in emblems.items():
        save(img, f"gui/emblem/{name}.png")

    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        items = list(sprites.values()) + [reveal_bg(), malice_bar(), hud_slot(False), hud_slot(True), talisman_item()] \
            + list(icons.values()) + list(emblems.values())
        scale = 4
        cols = 8
        cell = 48 * scale + 12
        rows = (len(items) + cols - 1) // cols
        sheet = new(cols * cell, rows * cell, hexc("#3a3a3a"))
        for i, img in enumerate(items):
            big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
            if big.width > cell - 8 or big.height > cell - 8:
                f = min((cell - 8) / big.width, (cell - 8) / big.height)
                big = img.resize((max(1, int(img.width * scale * f)), max(1, int(img.height * scale * f))), Image.NEAREST)
            sheet.alpha_composite(big, ((i % cols) * cell + 6, (i // cols) * cell + 6))
        sheet.save(out)
        print(f"preview {out}")


if __name__ == "__main__":
    main()
