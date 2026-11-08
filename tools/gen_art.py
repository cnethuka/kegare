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
HEAL = ramp("#06210f", "#0c3d1c", "#15652d", "#22a046", "#4ed873", "#a6f5b8", "#f2fff4")  # Reverse Malice Technique
ICE = ramp("#081428", "#0f2d55", "#1a5590", "#3a8fcc", "#79c8ec", "#c2eefa", "#f2fdff")  # Rime: deep blue to white
TEAL = ramp("#03201d", "#063b36", "#0b5f57", "#0f8a7e", "#14b8a6", "#5eead4", "#ccfbf1")  # Swap: teal (with Malice violet)
LAVENDER = ramp("#1a1233", "#2c1f57", "#45338a", "#6450be", "#9886ee", "#c4b5fd", "#f1edff")  # Puppet Manipulation
WOOD = ramp("#1f1208", "#3a220f", "#5c3818", "#83532a", "#a8723e", "#c99a62", "#ead0a6")  # a marionette's control bar


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


def swap_anchor_item() -> Image.Image:
    """16x16: Swap's anchor, a teal pearl lit from the top-left with a violet core and an ink outline."""
    img = new(16, 16)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d > 5.6:
                continue
            if d > 4.6:
                img.putpixel((x, y), INK)
                continue
            light = ((cx - x) + (cy - y)) / 9.0
            t = 0.55 + 0.45 * light - 0.3 * (d / 4.6)
            img.putpixel((x, y), TEAL[max(1, min(5, round(t * 6)))])
    for p in ((7, 7), (8, 7), (7, 8), (8, 8)):
        img.putpixel(p, MALICE[4])
    for p in ((7, 6), (6, 7)):
        img.putpixel(p, MALICE[5])
    for p in ((5, 4), (4, 5), (5, 5)):
        img.putpixel(p, TEAL[6])
    return img


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


# Reverse Malice Technique: a plus inside a ring that turns back on itself (green and white, never Malice violet).
ICON_RMT = [
    "................",
    "......KKKK......",
    "....KK4444KK....",
    "...K44KKKK44K...",
    "..K4K..KK..K4K..",
    "..K4K.K66K..KK..",
    ".K4K.KK66KK.K5K.",
    ".K4KK666666KK54K",
    ".K4KK655556K.K5K",
    ".K4K.KK66KK..KK.",
    "..K4K.K66K......",
    "..K4K..KK..K4K..",
    "...K44KKKK44K...",
    "....KK4444KK....",
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


# ---- Swap: ability icons (shape rules with a bevel and ink outline) and the emblem

def shaped_icon(fill) -> "Image.Image":
    """16x16 icon from a shape rule: fill(x, y) -> a color ramp (or None). Pixels get a bevel (lit top edge, shaded
    bottom edge), then the ink outline."""
    mask = [[fill(x, y) for x in range(16)] for y in range(16)]
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            r = mask[y][x]
            if r is None:
                continue
            up = y == 0 or mask[y - 1][x] is None
            down = y == 15 or mask[y + 1][x] is None
            left = x == 0 or mask[y][x - 1] is None
            shade = 6 if up and left else 5 if up or left else 3 if down else 4
            img.putpixel((x, y), r[shade])
    return outline(img)


def _arrow(x, y, x0, x1, cy, right: bool, shaft=1.0, head=3.2, head_len=4):
    """Inside a horizontal arrow from x0 to x1 at row cy (pointing right or left)."""
    tip = x1 if right else x0
    base = tip - head_len if right else tip + head_len
    if (right and base <= x <= tip) or (not right and tip <= x <= base):
        return abs(y - cy) <= head * abs(tip - x) / head_len + 0.01
    lo, hi = (x0, base) if right else (base, x1)
    return lo <= x <= hi and abs(y - cy) <= shaft


def icon_positions():
    def f(x, y):
        if _arrow(x, y, 1, 13, 4.5, True, shaft=0.6, head=3.0):
            return TEAL
        if _arrow(x, y, 2, 14, 10.5, False, shaft=0.6, head=3.0):
            return MALICE
        return None
    return shaped_icon(f)


def icon_switchback():
    def f(x, y):
        # out along the top, round the bend on the right, back along the bottom to an arrowhead
        if 2 <= x <= 9 and 3.4 <= y <= 5.6:
            return TEAL
        d = math.hypot(x - 9.5, y - 8.0)
        if x >= 9.5 and 2.6 <= d <= 4.7:
            return TEAL if y < 8 else MALICE
        if _arrow(x, y, 2, 9, 11.0, False, shaft=1.0, head=3.2, head_len=4):
            return MALICE
        return None
    return shaped_icon(f)

def _mask(x, y):
    """A domino mask with two eye holes: True inside the mask (holes excluded)."""
    inside = ((x - 7.5) / 7.3) ** 2 + ((y - 7.5) / 4.3) ** 2 <= 1.0 and not (y > 9.5 and 6 <= x <= 9)
    hole = any(((x - hx) / 1.9) ** 2 + ((y - 7.0) / 1.4) ** 2 <= 1.0 for hx in (4.3, 10.7))
    return inside and not hole


def icon_awareness():
    return shaped_icon(lambda x, y: MALICE if _mask(x, y) else None)

def icon_mimic():
    def f(x, y):
        # half a mask (violet) and its teal reflection beyond a mirror line
        if x == 8 and 1 <= y <= 14:
            return SILVER
        if _mask(x, y):
            return MALICE if x < 8 else TEAL
        return None
    return shaped_icon(f)

def icon_anchor():
    def f(x, y):
        if math.hypot(x - 10.0, y - 5.5) <= 3.6:
            return TEAL
        # the throw: a dotted arc up from the bottom-left
        t = (x - 1) / 7.0
        if 0 <= t <= 1 and x % 2 == 1:
            cy = 13.5 - 7.5 * math.sin(t * math.pi / 2)
            if abs(y - cy) <= 0.6:
                return MALICE
        return None
    return shaped_icon(f)


def icon_send_off():
    def f(x, y):
        if math.hypot(x - 11.5, y - 7.5) <= 3.2:
            return TEAL
        if _arrow(x, y, 1, 7, 7.5, True, shaft=0.6, head=3.0, head_len=3):
            return MALICE
        return None
    return shaped_icon(f)


def icon_exchange():
    def f(x, y):
        if math.hypot(x - 4.5, y - 4.5) <= 3.0:
            return TEAL
        if math.hypot(x - 11.5, y - 11.5) <= 3.0:
            return MALICE
        # two small arrows crossing between them
        if _arrow(x, y, 8, 13, 4.5, True, shaft=0.5, head=2.0, head_len=2):
            return MALICE
        if _arrow(x, y, 2, 7, 11.5, False, shaft=0.5, head=2.0, head_len=2):
            return TEAL
        return None
    return shaped_icon(f)


def icon_momentum_exchange():
    def f(x, y):
        # chevrons: teal rushing right on top, violet rushing left below
        for cy, tips, right, r in ((4.0, (8, 13), True, TEAL), (11.0, (2, 7), False, MALICE)):
            if abs(y - cy) > 3.2:
                continue
            for tip in tips:
                edge = tip - abs(y - cy) if right else tip + abs(y - cy)
                if abs(x - edge) <= 1.3:
                    return r
        return None
    return shaped_icon(f)

def icon_total_swap():
    def f(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        a = math.atan2(y - 7.5, x - 7.5)
        if 4.8 <= d <= 6.6:
            if -2.6 <= a <= -0.3:
                return TEAL
            if 0.55 <= a <= 2.85:
                return MALICE
        # arrowheads at the ends of the arcs
        for tipa, r in ((-0.3, TEAL), (2.85, MALICE)):
            tx, ty = 7.5 + math.cos(tipa) * 5.7, 7.5 + math.sin(tipa) * 5.7
            if math.hypot(x - tx, y - ty) <= 2.0 and ((x - tx) * math.cos(tipa) + (y - ty) * math.sin(tipa)) <= 0.6:
                return r
        if d <= 1.6:
            return GOLD
        return None
    return shaped_icon(f)


def emblem_swap():
    """Two brush-stroke arrows chasing each other round a circle (teal and Malice violet), a pearl in the middle."""
    img = vignette(48, TEAL, seed=401)
    fg = new(48, 48)
    cx, cy, r = 23.5, 23.5, 15.5
    for k, (pal, seed) in enumerate(((TEAL, 403), (MALICE, 409))):
        a0 = math.pi * (1.12 + k)
        a1 = a0 + math.pi * 0.7
        pts = [(cx + math.cos(a0 + (a1 - a0) * i / 16) * r, cy + math.sin(a0 + (a1 - a0) * i / 16) * r) for i in range(17)]
        brush(fg, pts, 2.2, pal[3:], seed=seed, taper=(0.25, 0.0), wobble=0.2)
        tip = pts[-1]
        heading = a1 + math.pi / 2
        for side in (-1, 1):
            back = heading + math.pi + side * 0.62
            brush(fg, [tip, (tip[0] + math.cos(back) * 7, tip[1] + math.sin(back) * 7)], 1.8, pal[3:],
                  seed=seed + 2 + (side > 0), taper=(0.05, 0.55))
    ring(fg, cx, cy, 4.2, 1.3, TEAL[4:], seed=421, wobble=0.1)
    ring(fg, cx, cy, 1.6, 1.2, MALICE[4:], seed=423, wobble=0.05)
    paste(img, rim_light(outline(fg), TEAL[-1]))
    for (x, y) in ((7, 8), (37, 36)):
        paste(img, sparkle().resize((5, 5), Image.NEAREST), x, y)
    return img


# ---- Puppet Manipulation: ability icons, order glyphs and the emblem (lavender, with wood for the control bar)

def _doll(x, y, cx, top, scale=1.0):
    """Inside a little doll (round head, body, arms out, two legs) whose head top is at (cx, top)."""
    s = scale
    if math.hypot(x - cx, y - (top + 2.2 * s)) <= 2.2 * s:
        return True
    body_top, body_bot = top + 4.4 * s, top + 8.6 * s
    if abs(x - cx) <= 1.4 * s and body_top <= y <= body_bot:
        return True
    if abs(y - (body_top + 1.0 * s)) <= 0.6 * s and abs(x - cx) <= 3.6 * s:
        return True
    return body_bot < y <= top + 11.4 * s and 0.4 * s <= abs(x - cx) <= 1.5 * s


def icon_puppet_creation():
    def f(x, y):
        if _doll(x, y, 7.5, 2.0):
            return LAVENDER
        if (x == 13 and 1 <= y <= 5) or (y == 3 and 11 <= x <= 15):  # a sparkle: freshly made
            return GOLD
        return None
    return shaped_icon(f)


def icon_possess():
    def f(x, y):
        if _doll(x, y, 11.0, 3.5, 0.85):
            return LAVENDER
        # the mind leaving: a wisp arcing in from the lower left to the doll's head
        t = (x - 1.0) / 8.0
        if 0 <= t <= 1:
            cy = 12.5 - 8.0 * math.sin(t * math.pi / 2)
            if abs(y - cy) <= 0.75 + 0.5 * t:
                return MALICE
        return None
    return shaped_icon(f)


def icon_directive():
    def f(x, y):
        if 2 <= y <= 3 and 1 <= x <= 14:  # the control bar
            return WOOD
        if 7 <= x <= 8 and 0 <= y <= 5:
            return WOOD
        for sx in (2, 7.5, 13):
            if abs(x - sx) <= 0.3 and 4 <= y <= 10:  # strings
                return SILVER
            if math.hypot(x - sx, y - 12) <= 1.8:  # heads at the ends
                return LAVENDER
        return None
    return shaped_icon(f)


def icon_focus():
    def f(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if 5.2 <= d <= 6.7:
            return LAVENDER
        if d <= 1.6:
            return MALICE
        # four ticks pointing in
        if (abs(x - 7.5) <= 0.5 and (y <= 3 or y >= 12)) or (abs(y - 7.5) <= 0.5 and (x <= 3 or x >= 12)):
            return LAVENDER
        return None
    return shaped_icon(f)


def _burst(x, y, cx, cy, r_out, r_in, points=8, phase=0.0):
    a = math.atan2(y - cy, x - cx) + phase
    d = math.hypot(x - cx, y - cy)
    k = abs(math.cos(a * points / 2))
    return d <= r_in + (r_out - r_in) * k ** 3


def icon_curio_burst():
    def f(x, y):
        if math.hypot(x - 7.5, y - 7.5) <= 2.0:
            return GOLD
        if _burst(x, y, 7.5, 7.5, 7.3, 2.6):
            return LAVENDER
        return None
    return shaped_icon(f)


def icon_chain_burst():
    def f(x, y):
        for (cx, cy, ro, ri) in ((3.0, 12.5, 3.0, 1.2), (7.5, 8.0, 3.6, 1.4), (12.5, 3.0, 4.2, 1.6)):
            if math.hypot(x - cx, y - cy) <= ri * 0.8:
                return GOLD
            if _burst(x, y, cx, cy, ro, ri, points=6):
                return LAVENDER
        return None
    return shaped_icon(f)


def _seg(x, y, ax, ay, bx, by, w=0.72):
    """On the thick line from (ax, ay) to (bx, by)."""
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(x - (ax + t * dx), y - (ay + t * dy)) <= w


def _hoop(x, y, cx, cy, r, w=0.8):
    return abs(math.hypot(x - cx, y - cy) - r) <= w


def _chevron(x, y, tip_x, cy, facing, size=2):
    """A little arrowhead pointing left (-1) or right (+1)."""
    return abs(y - cy) <= size and abs(abs(y - cy) - (tip_x - x) * facing) <= 0.5


def icon_strings():
    """A loose, wavy thread running up to a knot and a small loop."""
    def f(x, y):
        if _hoop(x, y, 12.3, 3.4, 2.1, 0.7):
            return LAVENDER
        if math.hypot(x - 9.6, y - 6.0) <= 1.5:
            return GOLD
        t = (x - 1.0) / 8.6
        if 0.0 <= t <= 1.0 and abs(y - (14.0 - 8.0 * t + 1.7 * math.sin(t * 2.0 * math.pi))) <= 0.9:
            return LAVENDER
        return None
    return shaped_icon(f)


def icon_zip_leash():
    """The special's two halves: you (a doll), a string up and ahead with an arrow on it (Zip), and a second string (Leash)."""
    def f(x, y):
        if _doll(x, y, 4.0, 3.0, 0.85):
            return LAVENDER
        if _chevron(x, y, 13.0, 3.5, 1):
            return GOLD
        if _seg(x, y, 6.0, 7.0, 12.0, 3.5, 0.6):
            return SILVER
        if _seg(x, y, 6.0, 8.0, 13.5, 12.5, 0.6) or math.hypot(x - 13.5, y - 12.5) <= 1.5:
            return MALICE
        return None
    return shaped_icon(f)


def icon_strings_reel():
    """Reel In: the target comes to you, along the string."""
    def f(x, y):
        if _doll(x, y, 12.0, 2.5, 0.85):
            return MALICE
        if _chevron(x, y, 2.0, 8.0, -1) or _chevron(x, y, 5.5, 8.0, -1):
            return GOLD
        if _seg(x, y, 2.0, 8.0, 10.0, 8.0, 0.5):
            return LAVENDER
        return None
    return shaped_icon(f)


def icon_strings_zip():
    """Zip: you go to the target, along the string."""
    def f(x, y):
        if _doll(x, y, 4.0, 2.5, 0.85):
            return LAVENDER
        if _hoop(x, y, 13.0, 8.0, 1.6, 0.7):
            return SILVER
        if _chevron(x, y, 9.5, 8.0, 1) or _chevron(x, y, 12.0, 8.0, 1, 1):
            return GOLD
        if _seg(x, y, 6.0, 8.0, 11.5, 8.0, 0.5):
            return SILVER
        return None
    return shaped_icon(f)


def icon_strings_tie():
    """Tie: two figures, one knot between them."""
    def f(x, y):
        if _doll(x, y, 3.0, 4.0, 0.7) or _doll(x, y, 13.0, 4.0, 0.7):
            return MALICE
        if math.hypot(x - 8.0, y - 7.5) <= 1.8:
            return GOLD
        if _seg(x, y, 4.5, 7.5, 11.5, 7.5, 0.5):
            return LAVENDER
        return None
    return shaped_icon(f)


def icon_strings_leash():
    """Leash: you in the middle, a string to each of two others."""
    def f(x, y):
        if _doll(x, y, 8.0, 5.0, 0.85):
            return LAVENDER
        if math.hypot(x - 2.0, y - 2.5) <= 1.8 or math.hypot(x - 14.0, y - 2.5) <= 1.8:
            return MALICE
        if _seg(x, y, 8.0, 8.0, 2.0, 2.5, 0.5) or _seg(x, y, 8.0, 8.0, 14.0, 2.5, 0.5):
            return SILVER
        return None
    return shaped_icon(f)


def icon_understudy():
    def f(x, y):
        if _doll(x, y, 3.5, 5.0, 0.8):
            return LAVENDER
        if _doll(x, y, 12.0, 3.5, 1.0):
            return MALICE
        # the swap: a little arc over both
        d = math.hypot(x - 7.8, y - 7.0)
        if 6.2 <= d <= 7.2 and y <= 3.5 and 4 <= x <= 12:
            return SILVER
        return None
    return shaped_icon(f)


def icon_stand_in():
    def f(x, y):
        if _doll(x, y, 4.0, 8.0, 0.6):
            return LAVENDER
        if _doll(x, y, 10.5, 1.0, 1.25):
            return MALICE
        return None
    return shaped_icon(f)


def order_glyph(kind: str) -> "Image.Image":
    """7x7 glyph of a Directive order, drawn on the Directive slot: follow (a chevron), stay (a bar), guard (a shield),
    go (a flag)."""
    grids = {
        "follow": [".......", "..X....", "...X...", "....X..", "...X...", "..X....", "......."],
        "stay": [".......", ".......", ".XXXXX.", ".XXXXX.", ".XXXXX.", ".......", "......."],
        "guard": [".XXXXX.", ".XXXXX.", ".XXXXX.", ".XXXXX.", "..XXX..", "...X...", "......."],
        "go": [".X.....", ".XXXX..", ".XXXXX.", ".XXX...", ".X.....", ".X.....", ".X....."],
    }
    img = new(7, 7)
    for y, row in enumerate(grids[kind]):
        for x, c in enumerate(row):
            if c == "X":
                img.putpixel((x, y), LAVENDER[6] if y < 2 else LAVENDER[5])
    return outline(img) if False else img


def emblem_puppet_manipulation():
    """A marionette: the wooden control cross at the top, three strings down to a little lavender doll with its arms
    out (mid kuru kuru), over a lavender vignette."""
    img = vignette(48, LAVENDER, seed=611)
    fg = new(48, 48)
    # the control cross
    brush(fg, [(9, 9), (39, 9)], 1.9, WOOD[2:], seed=613, taper=(0.15, 0.15), wobble=0.15)
    brush(fg, [(24, 3), (24, 16)], 1.8, WOOD[2:], seed=617, taper=(0.15, 0.2), wobble=0.12)
    # strings
    for (x0, y0, x1, y1, sd) in ((10, 10, 15, 27, 619), (38, 10, 33, 27, 621), (24, 16, 24, 20, 623)):
        brush(fg, [(x0, y0), (x1, y1)], 0.55, SILVER[4:], seed=sd, taper=(0.0, 0.0), wobble=0.05)
    # the doll
    for r, w in ((2.6, 1.6), (1.2, 1.4)):  # a filled round head
        ring(fg, 24, 24.0, r, w, LAVENDER[4:], seed=631, wobble=0.05)
    brush(fg, [(24, 28), (24, 36)], 2.2, LAVENDER[3:], seed=633, taper=(0.1, 0.25), wobble=0.15)
    brush(fg, [(15, 28), (33, 28)], 1.4, LAVENDER[3:], seed=637, taper=(0.3, 0.3), wobble=0.2)
    brush(fg, [(24, 35), (20, 43)], 1.3, LAVENDER[3:], seed=641, taper=(0.1, 0.5), wobble=0.2)
    brush(fg, [(24, 35), (28, 43)], 1.3, LAVENDER[3:], seed=643, taper=(0.1, 0.5), wobble=0.2)
    paste(img, rim_light(outline(fg), LAVENDER[-1]))
    for (x, y) in ((6, 30), (39, 35), (37, 19)):
        paste(img, sparkle().resize((5, 5), Image.NEAREST), x, y)
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
    # A dark iron plate with the letter in gold (lit) or dull iron (no special): readable, but it doesn't shout.
    if lit:
        pal = {"K": INK, "b": IRON[1], "R": GOLD[5]}
    else:
        pal = {"K": INK, "b": PLUM[2], "R": IRON[3]}
    return from_grid(grid, pal)


# ---------------------------------------------------------------------------------------------------- combat HUD
# Technique Mode's combat panel: it replaces the hotbar while the mode is on (the hotbar moves to a column on the left
# edge): a meter housing with the number on a centre plate, and a
# tray of numbered slots under it; every pixel here is our own: cursed iron, plum-black ink, accents tinted with the
# innate technique's Malice color at draw time.

PANEL_W, PANEL_H = 250, 50
CREST_TOP, FRAME_TOP, FRAME_BOTTOM, TRAY_BOTTOM = 0, 6, 23, 49
PLATE_W = 48            # the centre plate (number window), centred
PLATE_X0 = (PANEL_W - PLATE_W) // 2
PLATE_X1 = PLATE_X0 + PLATE_W - 1
CREST_HALF = 33         # the crest's top half-width (it widens one pixel per row)
PIP_HALF = 25           # the freeze-pip slot's half-width


def _shade(r: list, t: float, x: int, y: int):
    return dither(r, t, x, y)


def combat_panel() -> Image.Image:
    """250x50. Rows 0-6: the crest (a raised plate for freeze pips). Rows 6-23: the meter housing, with a recessed
    well (rows 9-20; cells at rows 11-18) and a raised centre plate for the number. Rows 23-49: the tray for nine
    20px ability slots and the 24px ultimate (rows 23-46), narrowing toward the bottom."""
    w, h = PANEL_W, PANEL_H
    img = new(w, h)
    grain = noise_stack(907, w, h, (12, 6, 3))

    # ---- tray (drawn first: the housing overlaps its top)
    for y in range(FRAME_BOTTOM - 1, TRAY_BOTTOM + 1):
        inset = 5 + (y - FRAME_BOTTOM) // 4
        x0, x1 = inset, w - 1 - inset
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y == TRAY_BOTTOM:
                c = INK
            elif y >= TRAY_BOTTOM - 2:  # lip
                c = IRON[3] if y == TRAY_BOTTOM - 2 else IRON[1]
            elif x in (x0 + 1, x1 - 1):  # inner edge: lit on the left, shadowed on the right
                c = IRON[4] if x == x0 + 1 else IRON[1]
            else:
                depth = (y - FRAME_BOTTOM) / (TRAY_BOTTOM - FRAME_BOTTOM)
                t = 0.15 + depth * 0.35 + (fbm(grain, x, y) - 0.5) * 0.25
                c = _shade(PLUM, t, x, y)
                if y <= FRAME_BOTTOM + 1:
                    c = INK  # shadow under the housing
            put(img, x, y, c)

    # ---- meter housing (rows FRAME_TOP..FRAME_BOTTOM), 2px chamfers
    for y in range(FRAME_TOP, FRAME_BOTTOM + 1):
        for x in range(w):
            dy = min(y - FRAME_TOP, FRAME_BOTTOM - y)
            dx = min(x, w - 1 - x)
            if dx + dy < 2:
                continue  # chamfered corner
            edge = dx == 0 or dy == 0 or dx + dy == 2
            if edge:
                c = INK
            elif y == FRAME_TOP + 1:
                c = IRON[6]
            elif y == FRAME_TOP + 2:
                c = IRON[5]
            elif y == FRAME_BOTTOM - 1:
                c = IRON[1]
            elif y == FRAME_BOTTOM - 2:
                c = IRON[2]
            elif x == 1:
                c = IRON[5]
            elif x == w - 2:
                c = IRON[2]
            else:
                t = 0.45 + (fbm(grain, x, y) - 0.5) * 0.35 - (y - FRAME_TOP) / 40
                c = _shade(IRON[2:6], t, x, y)
            put(img, x, y, c)
    # the recessed well for the cells
    wx0, wx1, wy0, wy1 = 4, w - 5, FRAME_TOP + 3, FRAME_BOTTOM - 3
    for y in range(wy0, wy1 + 1):
        for x in range(wx0, wx1 + 1):
            if x in (wx0, wx1) or y in (wy0, wy1):
                c = INK
            elif y == wy0 + 1:
                c = PLUM[0]
            else:
                c = _shade(PLUM, 0.1 + (y - wy0) / (wy1 - wy0) * 0.35, x, y)
            put(img, x, y, c)
    # rivets on the housing's ends
    for rx in (2, w - 3):
        for (dx, dy, c) in ((0, -1, IRON[6]), (0, 0, IRON[5]), (0, 1, IRON[3])):
            put(img, rx, (FRAME_TOP + FRAME_BOTTOM) // 2 + dy, c)

    # ---- centre plate (raised, number window)
    px0, px1, py0, py1 = PLATE_X0, PLATE_X1, FRAME_TOP - 1, FRAME_BOTTOM + 1
    for y in range(py0, py1 + 1):
        for x in range(px0, px1 + 1):
            dy = min(y - py0, py1 - y)
            dx = min(x - px0, px1 - x)
            if dx + dy < 2:
                continue
            if dx == 0 or dy == 0 or dx + dy == 2:
                c = INK
            elif y == py0 + 1:
                c = IRON[6]
            elif y == py1 - 1:
                c = IRON[1]
            elif x == px0 + 1:
                c = IRON[5]
            elif x == px1 - 1:
                c = IRON[2]
            else:
                c = _shade(IRON[2:5], 0.5 - (y - py0) / 30, x, y)
            put(img, x, y, c)
    nx0, nx1, ny0, ny1 = px0 + 4, px1 - 4, wy0, wy1
    for y in range(ny0, ny1 + 1):
        for x in range(nx0, nx1 + 1):
            c = INK if (x in (nx0, nx1) or y in (ny0, ny1)) else _shade(PLUM[:3], 0.2 + (y - ny0) / 30, x, y)
            put(img, x, y, c)

    # ---- crest (rows 0..6): a raised trapezoid plate with a slot for freeze pips
    cx = (w - 1) / 2
    for y in range(CREST_TOP, FRAME_TOP + 1):
        x0, x1 = int(cx - CREST_HALF) - y, int(cx + CREST_HALF + 0.5) + y
        for x in range(x0, x1 + 1):
            if x in (x0, x1) or y == CREST_TOP:
                c = INK
            elif y == CREST_TOP + 1:
                c = IRON[6]
            else:
                c = _shade(IRON[2:6], 0.55 - y / 14, x, y)
            put(img, x, y, c)
    for y in range(CREST_TOP + 2, FRAME_TOP + 1):  # pip slot
        for x in range(int(cx - PIP_HALF), int(cx + PIP_HALF + 0.5) + 1):
            put(img, x, y, INK if y == CREST_TOP + 2 else PLUM[1])
    return img


def combat_panel_accent() -> Image.Image:
    """250x50, white on clear: the lines tinted with the innate technique's Malice color (tray edges, crest rim,
    the well's lower lip)."""
    img = new(PANEL_W, PANEL_H)
    for y in range(FRAME_BOTTOM + 1, TRAY_BOTTOM - 2):
        inset = 5 + (y - FRAME_BOTTOM) // 4
        a = int(255 * (0.55 + 0.45 * (y - FRAME_BOTTOM) / (TRAY_BOTTOM - FRAME_BOTTOM)))
        put(img, inset + 1, y, (255, 255, 255, a))
        put(img, PANEL_W - 2 - inset, y, (255, 255, 255, a))
    cx = (PANEL_W - 1) / 2
    for x in range(int(cx - CREST_HALF) + 2, int(cx + CREST_HALF + 0.5) - 1):  # crest rim glow
        put(img, x, CREST_TOP + 1, (255, 255, 255, 150))
    for x in range(5, PANEL_W - 5):  # the well's lower lip
        if PLATE_X0 <= x <= PLATE_X1:
            continue
        put(img, x, FRAME_BOTTOM - 4, (255, 255, 255, 70))
    return img


def meter_cells() -> Image.Image:
    """33x8 atlas: an empty cell (x 0-10), a full cell in greyscale for tinting (11-21), its white shine (22-32).
    Each cell is 10 wide plus a 1px gap. Slanted streaks run through both, so a full meter reads as flowing energy
    and an empty one as etched iron."""
    img = new(33, 8)
    for y in range(8):
        for x in range(10):
            streak = (x + y) % 4 == 0
            edge_l, edge_t, edge_b = x == 0, y == 0, y == 7
            # empty: dark etched iron
            if edge_t:
                c = IRON[2]
            elif edge_b:
                c = INK
            elif edge_l:
                c = IRON[1]
            else:
                c = IRON[1] if streak else PLUM[2]
            put(img, x, y, c)
            # full: bright top, deeper bottom, lighter streaks
            v = 250 if edge_t else int(225 - y * 15)
            if streak and not edge_t:
                v = min(255, v + 30)
            if edge_l:
                v = int(v * 0.8)
            if edge_b:
                v = int(v * 0.6)
            put(img, 11 + x, y, (v, v, v, 255))
            # shine: the top rim and the streaks' upper half
            a = 170 if edge_t else 70 if y == 1 else (60 if streak and y < 4 else 0)
            if a:
                put(img, 22 + x, y, (255, 255, 255, a))
    return img


def combat_slot(kind: str) -> Image.Image:
    """20x20 recessed slot ('normal', 'selected'): an ink edge and a 1px rim around a 16x16 well for the icon.
    Or the 24x24 ultimate slot ('ultimate'): ink, iron and gold rings around the same 16x16 well."""
    size = 24 if kind == "ultimate" else 20
    rings = 4 if kind == "ultimate" else 2  # pixels from the edge to the icon well
    img = new(size, size)
    grain = noise_stack(613, size, size, (6, 3))
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            corner = min(x, size - 1 - x) + min(y, size - 1 - y)
            if corner < 2:
                continue
            lit = x < size - 1 - x and y < size - 1 - y
            if d == 0 or corner == 2:
                c = INK
            elif kind == "ultimate" and d == 1:
                c = IRON[6] if lit else IRON[3]
            elif kind == "ultimate" and d == 2:
                c = GOLD[3] if lit else GOLD[1]
            elif kind == "ultimate" and d == 3:
                c = INK
            elif d == 1:
                if kind == "selected":
                    c = GOLD[5] if (y < size // 2 or x < size // 2) and not (y > size - 4 or x > size - 4) else GOLD[3]
                else:
                    # recessed: the top-left rim in shadow, the bottom-right lit
                    c = IRON[1] if (x <= y and x < size - 1 - y) or (y < x and y < size - 1 - x) else IRON[4]
            else:
                fy = (y - rings) / 16
                cx = abs(x - (size - 1) / 2) / (size / 2)
                t = 0.15 + fy * 0.45 - cx * 0.15 + (fbm(grain, x, y) - 0.5) * 0.2
                c = dither(PLUM, t, x, y)
                if kind == "selected" and d == 2:
                    c = GOLD[1]  # a faint warm inner line on the lit slot
            put(img, x, y, c)
    if kind == "ultimate":  # gold studs on the corners
        for (sx, sy) in ((2, 2), (size - 3, 2), (2, size - 3), (size - 3, size - 3)):
            put(img, sx, sy, GOLD[6])
    return img

def env_mini(cold: bool) -> Image.Image:
    """5x5 glyph in the corner of the combat panel's number plate: a snowflake (cheaper here) or a flame (dearer)."""
    if cold:
        grid = ["..a..", "a.b.a", ".bbb.", "a.b.a", "..a.."]
        pal = {"a": ICE[5], "b": ICE[6]}
    else:
        grid = ["..a..", ".aa..", ".aba.", "abbba", ".bcb."]
        pal = {"a": hexc("#c2410c"), "b": hexc("#fb923c"), "c": hexc("#fde68a")}
    return from_grid(grid, pal)


# ---------------------------------------------------------------------------------------------------- Sorcerer screen
# One window in the combat panel's language: a heavy cursed-iron frame, recessed boxes, category tabs on the left
# edge and view buttons on the right edge (our own art).

def menu_frame() -> Image.Image:
    """64x64 nine-slice (border 10): the Sorcerer window. Heavy bevelled iron, rivets, a near-black ink interior."""
    size, border = 64, 10
    tile = size - 2 * border
    return frame(size, size, 7, IRON, plum_interior(23, tile, tile, r=PLUM[:3], contrast=0.35), seed=17,
                 tile_x=tile, tile_y=tile, rim=IRON[6], rivets=True)


def menu_well() -> Image.Image:
    """16x16 nine-slice (border 4): a recessed box inside the window (shadowed top-left, lit bottom-right rim)."""
    size = 16
    img = new(size, size)
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            top_left = (x <= y and x < size - 1 - y) or (y < x and y < size - 1 - x)
            if d == 0:
                c = INK
            elif d == 1:
                c = IRON[0] if top_left else IRON[3]
            elif d == 2:
                c = INK if top_left else PLUM[1]
            else:
                c = dither(PLUM[:3], 0.25 + (y / size) * 0.25, x, y)
            put(img, x, y, c)
    return img


def menu_tab(selected: bool) -> Image.Image:
    """24x24 tab/button on the window's edge: an iron plate around a dark well for a 16px icon; lit gold when selected."""
    size = 24
    img = new(size, size)
    for y in range(size):
        for x in range(size):
            d = min(x, y, size - 1 - x, size - 1 - y)
            corner = min(x, size - 1 - x) + min(y, size - 1 - y)
            if corner < 2:
                continue
            if d == 0 or corner == 2:
                c = INK
            elif d == 1:
                lit = x < size - 1 - x and y < size - 1 - y
                c = (GOLD[5] if lit else GOLD[3]) if selected else (IRON[5] if lit else IRON[2])
            elif d == 2:
                c = (GOLD[2] if selected else IRON[3])
            elif d == 3:
                c = INK
            else:
                c = dither(IRON[1:4] if selected else PLUM, 0.3 + y / size * 0.3, x, y)
            put(img, x, y, c)
    return img


ICON_VIEW_TECHNIQUE = [  # a spiral: the innate technique
    "................",
    "....KKKKKKK.....",
    "...K6666666K....",
    "..K66KKKKK66K...",
    ".K66K55555K66K..",
    ".K6K55KKK55K6K..",
    ".K6K5K444K5K6K..",
    ".K6K5K4KK45K6K..",
    ".K6K5K4K.K5K6K..",
    ".K6K5KK..K5K6K..",
    ".K6K55KKK55K6K..",
    ".K66K55555KK6K..",
    "..K66KKKKK.K6K..",
    "...K66666666K...",
    "....KKKKKKKK....",
    "................",
]
ICON_VIEW_TRAITS = [  # an eye: traits
    "................",
    "................",
    "................",
    ".....KKKKKK.....",
    "...KK666666KK...",
    "..K666KKKK666K..",
    ".K66KK5555KK66K.",
    "K66K55KKKK55K66K",
    "K66K55K66K55K66K",
    ".K66KK5555KK66K.",
    "..K666KKKK666K..",
    "...KK666666KK...",
    ".....KKKKKK.....",
    "................",
    "................",
    "................",
]
ICON_VIEW_SETTINGS = [  # a gear: settings
    "................",
    ".......KK.......",
    "...KK.K66K.KK...",
    "..K66K6556K66K..",
    "..K6555555556K..",
    "...K55KKKK55K...",
    ".KK55K....K55KK.",
    "K6655K....K5566K",
    "K6655K....K5566K",
    ".KK55K....K55KK.",
    "...K55KKKK55K...",
    "..K6555555556K..",
    "..K66K5445K66K..",
    "...KK.K44K.KK...",
    ".......KK.......",
    "................",
]
ICON_VIEW_PROGRESS = [  # steps climbing to the right: the ladder
    "................",
    "............KKK.",
    "...........K655K",
    "...........K544K",
    "........KKKK544K",
    ".......K6554544K",
    ".......K5444544K",
    "....KKKK5444544K",
    "...K655454445 4K".replace(" ", "4"),
    "...K544454445 4K".replace(" ", "4"),
    "KKKK544454445 4K".replace(" ", "4"),
    "K655544454445 4K".replace(" ", "4"),
    "K544544454445 4K".replace(" ", "4"),
    "K544544454445 4K".replace(" ", "4"),
    "KKKKKKKKKKKKKKKK",
    "................",
]

ICON_VIEW_PITY = [  # a four-pointed star: luck and pity
    "................",
    ".......KK.......",
    ".......K6K......",
    "......K66K......",
    "......K656K.....",
    ".....KK656KK....",
    "..KKKK66566KKK..",
    ".K6666655566666K",
    "..KKKK66566KKK..",
    ".....KK656KK....",
    "......K656K.....",
    "......K66K......",
    ".......K6K......",
    ".......KK.......",
    "................",
    "................",
]


def category_shared() -> Image.Image:
    """16x16 tab icon for the Shared ability category: a silver ring around a pale diamond core."""
    grid = [
        "................",
        ".....KKKKKK.....",
        "...KKSSSSSSKK...",
        "..KSShhhhhhSSK..",
        "..KSK......KSK..",
        ".KShK..WW..KhSK.",
        ".KShK.WPPW.KhSK.",
        ".KShKWPPPPWKhSK.",
        ".KShKWPPPPWKhSK.",
        ".KShK.WPPW.KhSK.",
        ".KShK..WW..KhSK.",
        "..KSK......KSK..",
        "..KSShhhhhhSSK..",
        "...KKSSSSSSKK...",
        ".....KKKKKK.....",
        "................",
    ]
    return from_grid(grid, {"K": INK, "S": SILVER[3], "h": SILVER[5], "W": MALICE[5], "P": MALICE[6]})


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

def string_braid() -> Image.Image:
    """Strings' cord and knot (art/blockbench/string_*.bbmodel use it). 32x32, on the block atlas. The top half tiles along u:
    two strands crossing in a herringbone, near-white on the crown of each strand and deep violet where they dive under.
    The bottom half is the knot: the same cord wound tight, in close bands. Drawn full-bright, so its shading is painted in."""
    tones = [hexc("#f5f3ff"), hexc("#ddd6fe"), hexc("#c4b5fd"), hexc("#c4b5fd"), hexc("#a78bfa"), hexc("#a78bfa"), hexc("#7c3aed"), hexc("#5b21b6")]
    img = Image.new("RGBA", (32, 32), CLEAR)
    for y in range(16):
        for x in range(32):
            over = ((x // 4) + (y // 4)) % 2 == 0
            img.putpixel((x, y), tones[((x + y) if over else (x - y)) % 8])
    for y in range(16, 32):
        for x in range(32):
            band = (y + (x // 8)) % 4
            img.putpixel((x, y), (tones[0], tones[1], tones[4], tones[7])[band])
    return img


# ---------------------------------------------------------------------------------------------------- the roll reveal

def reveal_talisman() -> Image.Image:
    """The ofuda that burns in the Omen scene: 40x80 of aged paper, ink bands and script, and a red seal. RevealScreen burns it
    column by column, so nothing here knows about fire."""
    w, h = 40, 80
    img = new(w, h)
    n = noise_stack(901, w, h, (20, 10, 5))
    edge = random.Random(903)
    left = [3 + (edge.random() < 0.3) for _ in range(h)]
    right = [w - 4 - (edge.random() < 0.3) for _ in range(h)]
    top = [2 + (edge.random() < 0.25) for _ in range(w)]
    bottom = [h - 3 - (edge.random() < 0.45) - (edge.random() < 0.2) for _ in range(w)]
    for y in range(h):
        for x in range(w):
            if not (left[y] <= x <= right[y] and top[x] <= y <= bottom[x]):
                continue
            d = min(x - left[y], right[y] - x, y - top[x], bottom[x] - y)
            t = 0.62 + (fbm(n, x, y) - 0.5) * 1.1 - (0.3 if d == 0 else 0.14 if d == 1 else 0.0)
            put(img, x, y, dither(PAPER, t, x, y))
    ink = [hexc("#2b2129"), hexc("#1c1a1f"), hexc("#141216")]
    for y0 in (7, 10, 69, 72):  # ink bands, dry-brushed
        brush(img, [(7, y0), (32, y0 + (1 if y0 % 2 else 0))], 0.9 if y0 in (7, 72) else 0.55, ink, seed=905 + y0, taper=(0.06, 0.06), dry=0.2, wobble=0.3)
    # the script: three stacked marks above the seal, three below
    strokes = [
        ([(13, 15), (27, 15)], 1.0), ([(20, 13), (20, 24)], 1.2), ([(14, 20), (19, 24)], 0.8), ([(26, 20), (21, 24)], 0.8),
        ([(12, 28), (28, 27)], 0.9), ([(16, 25), (15, 31)], 0.7), ([(24, 25), (25, 31)], 0.7),
        ([(13, 51), (27, 50)], 1.0), ([(20, 49), (20, 58)], 1.1), ([(14, 55), (26, 54)], 0.8),
        ([(15, 60), (25, 60)], 0.8), ([(13, 66), (20, 60)], 0.8), ([(27, 66), (20, 60)], 0.8),
    ]
    for i, (pts, width) in enumerate(strokes):
        brush(img, pts, width, ink, seed=921 + i, taper=(0.2, 0.3), dry=0.12, wobble=0.35)
    # the seal
    ring(img, 20, 40, 7.2, 1.5, [SEAL[0], SEAL[1], SEAL[2]], seed=941, wobble=0.25, dry=0.1)
    for y in range(35, 46):
        for x in range(15, 26):
            if math.hypot(x - 20, y - 40) <= 3.3:
                put(img, x, y, SEAL[2] if (x * 3 + y) % 5 else SEAL[3])
    brush(img, [(15, 35), (25, 45)], 0.55, [SEAL[1], SEAL[2]], seed=943, taper=(0.3, 0.3))
    brush(img, [(25, 35), (15, 45)], 0.55, [SEAL[1], SEAL[2]], seed=947, taper=(0.3, 0.3))
    return outline(img, hexc("#2b2129"))


def reveal_flames(frames: int = 8) -> list[Image.Image]:
    """Flame frames, 48x64, looping: noise scrolling upward through a teardrop, which it eats away toward the top, so the fire
    stands up in tongues. White and greys only: the Omen tints it with the rarity's color and draws it additively."""
    w, h = 48, 64
    layers = noise_stack(911, w, h, (6, 3, 2))
    out = []
    for f in range(frames):
        phase = f / frames
        img = new(w, h)
        for y in range(h):
            up = (h - 1 - y) / (h - 1)  # 0 at the base, 1 at the tip
            for x in range(w):
                u = abs(x - (w - 1) / 2) / (w / 2)
                body = max(0.0, 1 - u ** 1.5 - up ** 1.25 * (0.55 + 0.45 * u))
                sway = 3.5 * up * math.sin(up * 4.5 - phase * math.tau)
                n = fbm(layers, x + sway, y + phase * h)
                v = body * 1.65 - (1 - n) * (0.52 + 0.95 * up)
                if v < 0.03:
                    continue
                if v > 0.86:
                    c = (255, 255, 255, 255)
                elif v > 0.60:
                    c = (226, 226, 226, 255)
                elif v > 0.36:
                    c = (172, 172, 172, 240)
                elif v > 0.17:
                    c = (112, 112, 112, 205)
                else:
                    c = (62, 62, 62, 140)
                put(img, x, y, c)
        out.append(img)
    return out


def reveal_glow() -> Image.Image:
    """A soft white disc in hard steps (64x64). Tinted and drawn additively behind things."""
    size = 64
    img = new(size, size)
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - 31.5, y - 31.5) / 32
            if d >= 1:
                continue
            a = (1 - d) ** 2
            a = math.floor(a * 18) / 18
            put(img, x, y, (255, 255, 255, int(a * 255)))
    return img


def reveal_ring() -> Image.Image:
    """A thin shockwave ring (128x128), white. It's drawn large, so it needs the extra pixels."""
    size = 128
    img = new(size, size)
    for y in range(size):
        for x in range(size):
            d = abs(math.hypot(x - 63.5, y - 63.5) - 60)
            if d < 3:
                put(img, x, y, (255, 255, 255, 255 if d < 1 else 130 if d < 2 else 45))
    return img


def reveal_ray() -> Image.Image:
    """One light ray (16x128), its narrow bright end at the top: fans out and fades along its length. White."""
    w, h = 16, 128
    img = new(w, h)
    for y in range(h):
        half = 0.8 + 6.6 * (y / (h - 1))
        for x in range(w):
            d = abs(x - 7.5) / half
            if d >= 1:
                continue
            a = (1 - d) * (1 - y / h) ** 1.4
            put(img, x, y, (255, 255, 255, int(math.floor(a * 16) / 16 * 255)))
    return img


def _point_in(poly: list[tuple[float, float]], x: float, y: float) -> bool:
    inside = False
    for i in range(len(poly)):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % len(poly)]
        if (y0 > y) != (y1 > y) and x < (x1 - x0) * (y - y0) / (y1 - y0) + x0:
            inside = not inside
    return inside


def reveal_star() -> Image.Image:
    """A five-pointed star, 16x16, in white and greys with an ink outline, lit from the top-left. Tinted with the rarity's color."""
    size = 16
    img = new(size, size)
    cx, cy = 7.5, 8.2
    r_out, r_in = 7.4, 3.0
    poly = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = r_out if i % 2 == 0 else r_in
        poly.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    for y in range(size):
        for x in range(size):
            if not _point_in(poly, x, y):
                continue
            v = int(min(255, 206 + 60 * ((cx - x) + (cy - y)) / (2 * r_out)))
            put(img, x, y, (v, v, v, 255))
    return outline(img)


def reveal_star_small() -> Image.Image:
    """The same star, 9x9 and placed by hand, for the name card's row."""
    grid = [
        "....W....",
        "....W....",
        "...WWW...",
        "WWWWWWWWG",
        ".WWWWWGG.",
        "..WWWGG..",
        "..WWGGG..",
        ".WWG.GGG.",
        ".G.....G.",
    ]
    return from_grid(grid, {"W": (255, 255, 255, 255), "G": (205, 205, 205, 255)})


def reveal_eye(frames: int = 6) -> list[Image.Image]:
    """The blue eye of the Six Eyes scene (72x36), closed to fully open. Painted in its own colors, never tinted."""
    w, h = 72, 36
    cx, cy = 35.5, 18.5
    iris_ramp = ramp("#072a78", "#0f5ad0", "#1f8fff", "#39c0ff", "#8fe6ff", "#e6fbff")
    lash = hexc("#eaf7ff")
    out = []
    for f in range(frames):
        o = (f / (frames - 1)) ** 0.9
        img = new(w, h)
        def upper(u: float) -> float:
            return cy - 0.8 - o * 12.5 * max(0.0, 1 - abs(u) ** 2.1) ** 0.85 + 2.2 * u * u
        def lower(u: float) -> float:
            return cy + 0.8 + o * 8.0 * max(0.0, 1 - abs(u) ** 2.4) ** 0.9 + 2.2 * u * u
        for y in range(h):
            for x in range(w):
                u = (x - cx) / 31
                if abs(u) >= 1:
                    continue
                top, bot = upper(u), lower(u)
                if top <= y <= bot:
                    # the white of the eye, in the lid's shadow near the top and toward the corners
                    shade = 1.0 - 0.34 * abs(u) ** 2.4 - 0.62 * max(0.0, 1 - (y - top) / 3.6)
                    v = max(0.22, shade)
                    c = (int(232 * v), int(242 * v), int(255 * v), 255)
                    d = math.hypot(x - cx, (y - cy + 1) * 1.04)
                    if d <= 11.2 and o > 0.02:
                        if d <= 2.7:
                            c = hexc("#04102e")
                        elif d <= 3.7:
                            c = hexc("#e6fbff")
                        else:
                            t = 1 - (d - 3.7) / 7.5
                            spoke = 0.13 * math.sin(math.atan2(y - cy, x - cx) * 12)
                            c = pick(iris_ramp, t + spoke)
                            if y - top < 2.6:
                                c = pick(iris_ramp, (t + spoke) * 0.45)
                    put(img, x, y, c)
        # the lids: an ink line, then the pale lash line over the upper lid
        for x in range(w):
            u = (x - cx) / 31
            if abs(u) >= 1:
                continue
            t = int(round(upper(u)))
            b = int(round(lower(u)))
            put(img, x, t, INK)
            put(img, x, t - 1, lash)
            put(img, x, b, hexc("#5f88b5"))
        # long lashes, fanning out from the upper lid
        for i in (-4, -3, -2, -1, 1, 2, 3, 4):
            u = (i - (0.5 if i > 0 else -0.5)) / 4.6
            bx = cx + u * 31
            by = upper(u) - 1
            angle = -math.pi / 2 + u * 1.05
            length = (8.5 - abs(i) * 0.75) * (0.3 + 0.7 * o)
            for step in range(int(length * 2)):
                d = step / 2
                sweep = d * d * 0.07 * (1 if u >= 0 else -1)
                put(img, int(round(bx + math.cos(angle) * d + sweep)), int(round(by + math.sin(angle) * d)), lash)
        if o > 0.3:  # the glint
            for (gx, gy, a) in ((31, 13, 255), (32, 13, 255), (31, 14, 255), (32, 14, 200), (30, 13, 130), (31, 12, 130), (40, 22, 170)):
                put(img, gx, gy, (255, 255, 255, a))
        out.append(img)
    return out


def reveal_ash() -> Image.Image:
    """What's left of the talisman when you're born Severed: a low heap of grey ash (48x18), with a few scraps of charred paper."""
    w, h = 48, 18
    img = new(w, h)
    grey = ramp("#1f1f22", "#35353a", "#505057", "#6e6e76", "#90909a", "#b4b4bd")
    n = noise_stack(951, w, h, (12, 6, 3))
    for x in range(w):
        u = abs(x - 23.5) / 23.5
        crest = h - 1 - (h - 5) * (1 - u ** 2.8) * (0.42 + 0.95 * fbm(n, x, 2))
        for y in range(h):
            if y < crest:
                continue
            depth = (y - crest) / max(1.0, h - crest)
            t = 0.75 - 0.75 * depth + (fbm(n, x, y) - 0.5) * 0.8
            put(img, x, y, dither(grey, t, x, y))
    rng = random.Random(953)
    for _ in range(9):
        x, y = rng.randrange(6, 42), rng.randrange(7, 15)
        if opaque(img, x, y):
            put(img, x, y, hexc("#141216"))
            put(img, x + 1, y, hexc("#2a2024"))
    return outline(img, hexc("#0e0d10"))


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
    out["tab"] = tab(False)
    out["tab_selected"] = tab(True)
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
    out["category_shared"] = category_shared()
    out["menu_frame"] = menu_frame()
    out["menu_well"] = menu_well()
    out["menu_tab"] = menu_tab(False)
    out["menu_tab_selected"] = menu_tab(True)
    out["view_technique"] = icon(ICON_VIEW_TECHNIQUE, MALICE)
    out["view_progress"] = icon(ICON_VIEW_PROGRESS, TEAL)
    out["view_traits"] = icon(ICON_VIEW_TRAITS, ICE)
    out["view_pity"] = icon(ICON_VIEW_PITY, GOLD)
    out["view_settings"] = icon(ICON_VIEW_SETTINGS, SILVER)
    out["env_cold_mini"] = env_mini(True)
    out["env_hot_mini"] = env_mini(False)
    out["reveal/talisman"] = reveal_talisman()
    for i, img in enumerate(reveal_flames()):
        out[f"reveal/flame_{i}"] = img
    out["reveal/glow"] = reveal_glow()
    out["reveal/ring"] = reveal_ring()
    out["reveal/ray"] = reveal_ray()
    out["reveal/star"] = reveal_star()
    out["reveal/star_small"] = reveal_star_small()
    for i, img in enumerate(reveal_eye()):
        out[f"reveal/eye_{i}"] = img
    out["reveal/ash"] = reveal_ash()
    return out



# ---------------------------------------------------------------------------------------------------- Puppet Manipulation

def puppet_glow() -> "Image.Image":
    """The puppets' glow shell (entity texture, drawn additively and tinted with the Malice color, scrolling slowly): hard
    one-pixel threads woven across a black tile, four each way, gently waving, wrapping seamlessly both ways. Where two cross,
    one passes over the other in turn, like cloth: the one underneath stops a pixel short on each side. No soft edge, no
    noise: crisp at any size (the tiers differ by how bright the layer draws it, PuppetGlowLayer)."""
    size = 64
    weft_level, warp_level = 255, 200
    weft: dict[tuple[int, int], int] = {}  # pixel -> which thread
    warp: dict[tuple[int, int], int] = {}
    for i in range(4):
        for x in range(size):  # the waves' slopes stay under 1, so a pixel per column is a joined line
            y = round(8 + 16 * i + 3.0 * math.sin(2 * math.pi * 2 * x / size + 1.3 * i)) % size
            weft[(x, y)] = i
        for y in range(size):
            x = round(8 + 16 * i + 3.0 * math.sin(2 * math.pi * 2 * y / size + 0.9 * i + 0.5)) % size
            warp[(x, y)] = i
    level = {p: weft_level for p in weft}
    for p, j in warp.items():
        level.setdefault(p, warp_level)
    for (x, y), j in warp.items():
        i = weft.get((x, y))
        if i is None:
            continue
        if (i + j) % 2 == 0:  # the weft goes over: the warp stops short above and below
            level[(x, y)] = weft_level
            for dy in (-1, 1):
                q = (x, (y + dy) % size)
                if q in warp and q not in weft:
                    level[q] = 0
        else:  # the warp goes over: the weft stops short left and right
            level[(x, y)] = warp_level
            for dx in (-1, 1):
                q = ((x + dx) % size, y)
                if q in weft and q not in warp:
                    level[q] = 0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    for (x, y), c in level.items():
        img.putpixel((x, y), (c, c, c, 255))
    return img


FLAME_FRAMES = 8
FLAME_SIZE = 14  # one texel is one pixel of a player's skin: as chunky as the arm it burns on
WISP_SIZE = 7
FLAME_BLACK = 24  # tinted, this is near-black: it's what holds the shape against a bright sky of nearly the same color


def flame_inside(t: int, tongues, *, snake: float) -> "list[list[bool]]":
    """Which texels are fire, for one frame. The tongues make a skyline over a base that's rounded like a cup, with nothing hanging below the
    hand. Then each row slides sideways by a wave running up, in whole cycles so the last frame runs into the first. Sliding by rows keeps
    everything joined, and tips swing more than roots, so a tongue snakes and licks where a straight one would end in a hard point."""
    size = FLAME_SIZE
    tau = 2 * math.pi
    tops = [0.0] * size
    for x in range(size):
        for centre, width, height, k, p in tongues:
            top = height * (0.82 + 0.18 * math.sin(tau * (k * t / FLAME_FRAMES + p)))
            across = abs(x - centre + 0.6 * math.sin(tau * (t / FLAME_FRAMES + p + 0.3))) / width
            if across < 1:
                tops[x] = max(tops[x], top * (1 - across * across) ** 0.55)  # blunt: a dome, not a spike
    inside = [[False] * size for _ in range(size)]
    for y in range(size):
        ny = (size - 1 - y) / (size - 1)
        slide = round(snake * (0.25 + ny) ** 1.4 * math.sin(tau * (1.15 * ny - t / FLAME_FRAMES + tongues[0][4])))
        cup = 2.6 + 16 * ny if ny < 0.3 else 7.0  # how far from the middle the base reaches, round underneath
        for x in range(size):
            if tops[x] > ny and abs(x - (size - 1) / 2) <= cup:
                if 0 <= x + slide < size:
                    inside[y][x + slide] = True
    return inside


def flame_frame(t: int, inside, *, values, hot_rows: float, black: float) -> "Image.Image":
    """Flat shades, hard edges and no soft alpha, hottest low down. Along the outer edge near-black texels mix in: only there, and not along the
    bottom row, where a run of them reads as a dark bar. So the shape holds and the inside stays clean. Tops of tongues stay in color, so tips look lit and not outlined."""
    size = FLAME_SIZE
    shade, body, hot = values
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    fire = lambda x, y: 0 <= x < size and 0 <= y < size and inside[y][x]
    for y in range(size):
        row = [x for x in range(size) if fire(x, y)]
        for x in row:
            left, right = not fire(x - 1, y), not fire(x + 1, y)
            above, below = not fire(x, y - 1), not fire(x, y + 1)
            ny = (size - 1 - y) / (size - 1)
            v = hot if ny < hot_rows else body
            if left and right:
                v = body  # a thin lick: all of it in color
            elif (x == row[0] or x == row[-1] or below) and not above and y < size - 1 and ny < 0.8:
                # A broken line, not an outline. Which texels go dark drifts with the frame, so it seethes a little.
                v = FLAME_BLACK if ((x * 5 + y * 3 + (t // 2) * 7) % 10) / 10 < black else shade
            elif left or right:
                v = shade
            elif above:
                v = body
            img.putpixel((x, y), (v, v, v, 255))
    return img


WISP_FRAMES = (
    ("...#...", "..##...", "..#+...", "..+#...", "...##..", "...##..", "...#..."),  # a tip that's just come off the fire
    (".......", "...##..", "..#....", "..#+...", "...#+..", "...#...", "......."),  # thinning into a curl
    (".......", ".......", "...##..", "..#....", "..+#...", ".......", "......."),  # a small hook
    (".......", ".......", ".......", "...#...", "...+...", ".......", "......."),  # going out
)


# Tongues: centre (in texels), half width, height, cycles a loop, phase. The arm covers texels 5 to 8, and the tall ones flank it.
FLAME_MASS = ((3.4, 2.7, 0.84, 2, 0.40), (9.8, 2.7, 0.96, 1, 0.15), (6.5, 3.2, 0.60, 1, 0.70), (1.7, 1.4, 0.40, 1, 0.05), (11.6, 1.4, 0.50, 2, 0.55))
FLAME_LICKS = ((3.6, 1.5, 0.62, 2, 0.25), (9.6, 1.4, 0.74, 1, 0.80))
FLAME_HEART = ((3.5, 0.8, 0.34, 2, 0.60), (9.7, 0.8, 0.42, 1, 0.10))


def fist_flame() -> "Image.Image":
    """A ready fist's Malice fire, greyscale for the Malice color at runtime, sharp so it stays chunky and reads against a bright sky.
    Rows of frames: the mass behind the arm with near-black mixed into its edge, the licks that cross in front (open in the middle so the
    fist shows through), the hot heart (drawn paler), and the little curls that come off it."""
    size = FLAME_SIZE
    img = Image.new("RGBA", (FLAME_FRAMES * size, 3 * size + WISP_SIZE), (255, 255, 255, 0))
    for t in range(FLAME_FRAMES):
        img.paste(flame_frame(t, flame_inside(t, FLAME_MASS, snake=1.5), values=(148, 215, 250), hot_rows=0.25, black=0.68), (t * size, 0))
        img.paste(flame_frame(t, flame_inside(t, FLAME_LICKS, snake=1.8), values=(170, 232, 255), hot_rows=0.2, black=0.0), (t * size, size))
        heart = flame_inside(t, FLAME_HEART, snake=1.2)
        heart[size - 1] = heart[size - 2] = [False] * size  # it sits by the knuckles, not under them
        img.paste(flame_frame(t, heart, values=(235, 250, 255), hot_rows=0.2, black=0.0), (t * size, 2 * size))
    shades = {"#": 255, "+": 170}
    for f, rows in enumerate(WISP_FRAMES):
        for y, row in enumerate(rows):
            for x, c in enumerate(row):
                if c in shades:
                    v = shades[c]
                    img.putpixel((f * WISP_SIZE + x, 3 * size + y), (v, v, v, 255))
    return img


# --- A ready fist's fire on a body: two layers round the arm, each a greyscale unwrap with real holes in it. ---
FIRE_FRAMES = 8
# Texels across one face of the arm, and rows from the fist up. One texel is about one pixel of skin, on either layer.
FIRE_OUTER = (7, 10)
FIRE_INNER = (5, 8)
FIRE_GRIP = (14, 16)
FIRE_HOT, FIRE_BODY, FIRE_SHADE = 255, 218, 160
FIRE_SHEET_ROWS = {"outer": 0, "inner": 10, "heart": 18, "outer_up": 26, "inner_up": 36, "heart_up": 44, "grip_back": 52, "grip_front": 68}
FIRE_SHEET_SIZE = (FIRE_FRAMES * (4 * FIRE_OUTER[0] + FIRE_OUTER[0]), 84)
# Tongues: column, half width, height in rows, beats a loop, phase. On the outer layer the tall ones stand on the arm's corners, so that from any
# side they're the ones past its outline, and short ones leave the middle of each face open. The inner layer is the other way about, between them.
FIRE_OUTER_TONGUES = ((0.3, 1.9, 7.2, 2, 0.10), (3.5, 1.2, 3.2, 1, 0.65), (7.2, 1.8, 5.8, 1, 0.55), (10.6, 1.2, 3.8, 2, 0.05),
                      (13.8, 2.0, 7.6, 1, 0.30), (17.4, 1.2, 3.0, 2, 0.45), (21.2, 1.8, 6.4, 2, 0.80), (24.6, 1.2, 3.6, 1, 0.25))
FIRE_INNER_TONGUES = ((1.6, 0.83, 5.4, 1, 0.7),
                      (4.8, 0.72, 3.2, 2, 0.35),
                      (7.6, 0.86, 4.6, 2, 0.2),
                      (11.4, 0.83, 6.2, 1, 0.05),
                      (14.6, 0.72, 3.4, 2, 0.85),
                      (17.6, 0.86, 4.8, 2, 0.6))
FIRE_GRIP_BACK = ((4.6, 1.6, 12.5, 1, 0.15), (8.6, 1.6, 14.0, 2, 0.45))
FIRE_GRIP_FRONT = ((2.4, 1.5, 8.5, 2, 0.60), (6.4, 1.8, 15.0, 1, 0.20), (10.2, 1.6, 11.5, 2, 0.90), (12.6, 0.9, 5.0, 1, 0.35))


def fire_hash(*key: int) -> float:
    h = 2166136261
    for k in key:
        h = ((h ^ (k & 0xFFFF)) * 16777619) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 1274126177) & 0xFFFFFFFF
    h ^= h >> 16
    return h / 2 ** 32


def fire_tongues(t: int, around: int, rows: int, tongues, *, snake: float, wraps: bool = True):
    """Which tongue each texel belongs to and how far across it, row 0 at the bottom: narrow where it starts, widest low down, coming to a
    point, swelling and swaying, with a bend that runs up it. None is a hole."""
    tau = 2 * math.pi
    cells = [[None] * around for _ in range(rows)]
    for index, (centre, half, height, beats, phase) in enumerate(tongues):
        top = height * (0.8 + 0.2 * math.sin(tau * (beats * t / FIRE_FRAMES + phase)))
        sway = 0.6 * math.sin(tau * (t / FIRE_FRAMES + phase + 0.3))
        for y in range(rows):
            if y >= top:
                continue
            along = y / top
            bend = snake * along * math.sin(tau * (1.1 * along - t / FIRE_FRAMES + phase))
            width = max(0.55, half * (0.6 + 0.4 * min(1.0, y / 1.5)) * (1 - along * along) ** 0.6)
            for c in range(around):
                d = c - centre - sway - bend
                if wraps:
                    d = (d + around / 2) % around - around / 2
                if abs(d) < width:
                    s = d / width
                    if cells[y][c] is None or abs(s) < abs(cells[y][c][1]):
                        cells[y][c] = (index, s, along)
    return cells


def fire_values(t: int, cells, *, seed: int, dark: float, fleck: float, hot_rows: int, gap: float = 0.0):
    """Flat shades. Near-black runs up one side of each tongue, and flecks it here and there: that's what keeps the shape on any background, and
    it stays near-black after tinting. The tips stay in color, so they look lit and not outlined. Now and then a texel on the dark side is a hole."""
    rows, around = len(cells), len(cells[0])
    values = [[None] * around for _ in range(rows)]
    for y in range(rows):
        for c in range(around):
            cell = cells[y][c]
            if cell is None:
                continue
            index, s, along = cell
            dark_side = -1 if index % 2 else 1
            roll = fire_hash(seed, c, y, 1000 + t // 2)
            if along > 0.72 or (y + 1 < rows and cells[y + 1][c] is None):
                v = FIRE_BODY
            elif s * dark_side > 0.1:
                if fire_hash(seed, c, y, 3000 + t // 2) < gap:
                    continue
                v = FLAME_BLACK if roll < dark else FIRE_SHADE
            elif roll < fleck:
                v = FLAME_BLACK
            elif s * dark_side < -0.55:
                v = FIRE_SHADE
            else:
                v = FIRE_HOT if y < hot_rows else FIRE_BODY
            values[y][c] = v
    return values


def fire_scraps(t: int, values, *, seed: int, start: int, count: int) -> None:
    """Near the elbow the fire has broken up into small dark scraps, drifting up the arm."""
    rows, around = len(values), len(values[0])
    for i in range(count):
        c = int(fire_hash(seed, i, 7) * around)
        span = rows - start
        y = start + (int(fire_hash(seed, i, 8) * span) + (t + i) // 2) % span
        if values[y][c] is None:
            values[y][c] = FLAME_BLACK


def fire_cap(t: int, size: int, *, seed: int, cover: float):
    """The end of the fist, seen when the arm points at you: tongues going out from a broken middle."""
    tau = 2 * math.pi
    middle = (size - 1) / 2
    values = [[None] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            dx, dy = x - middle, y - middle
            r = math.hypot(dx, dy) / max(0.5, middle)
            a = math.atan2(dy, dx)
            reach = 0.78 + 0.34 * math.sin(3 * a + tau * (t / FIRE_FRAMES + seed * 0.1)) + 0.1 * math.sin(5 * a - tau * t / FIRE_FRAMES)
            if r > reach or fire_hash(seed, x, y, t // 2) > cover:
                continue
            speck = fire_hash(seed, x, y, 2000 + t // 2)
            values[y][x] = FLAME_BLACK if speck < 0.32 else FIRE_HOT if r < 0.45 else FIRE_BODY
    return values


def fire_round_base(cells):
    """A clump that isn't on an arm has to come to a round bottom of its own, or it sits on a straight line."""
    rows, width = len(cells), len(cells[0])
    middle = (width - 1) / 2
    for c in range(width):
        off = abs(c - middle) / middle
        for y in range(rows):
            if y < round(3.2 * off * off):
                cells[y][c] = None
    return cells


def fire_rising(values, base: int):
    """The same fire on an arm held up, where fire goes the other way: from the wrist, past the knuckles. Row 0 is still the end nearest the hand."""
    rows, around = len(values), len(values[0])
    rising = [[None] * around for _ in range(rows)]
    for y in range(rows):
        if 0 <= base - y < rows:
            rising[base - y] = list(values[y])
    return rising


def fire_put(img, x0: int, y0: int, values, *, flip: bool = True) -> None:
    rows = len(values)
    for y, row in enumerate(values):
        for x, v in enumerate(row):
            if v is not None:
                img.putpixel((x0 + x, y0 + (rows - 1 - y if flip else y)), (v, v, v, 255))


def fist_fire() -> "Image.Image":
    """A ready fist's Malice fire on a player's arm, greyscale for the Malice color at runtime, hard-edged with real holes in it.
    Rows of frames: the outer layer, whose tips break past the arm's outline; the inner layer, tight on the arm; the hot heart of it (drawn
    paler); and the first-person clump round a held item, behind it and in front. Each arm layer is an unwrap with the fist along the bottom
    and the elbow along the top, with the end of the fist beside it."""
    img = Image.new("RGBA", FIRE_SHEET_SIZE, (255, 255, 255, 0))
    ow, or_ = FIRE_OUTER
    iw, ir = FIRE_INNER
    gw, gr = FIRE_GRIP
    for t in range(FIRE_FRAMES):
        outer = fire_values(t, fire_tongues(t, 4 * ow, or_, FIRE_OUTER_TONGUES, snake=1.3), seed=11, dark=0.8, fleck=0.14, hot_rows=0)
        outer_up = fire_rising(outer, 7)
        fire_scraps(t, outer, seed=12, start=7, count=3)
        fire_scraps(t, outer_up, seed=12, start=8, count=3)
        fire_put(img, t * 5 * ow, FIRE_SHEET_ROWS["outer"], outer)
        fire_put(img, t * 5 * ow, FIRE_SHEET_ROWS["outer_up"], outer_up)
        fire_put(img, t * 5 * ow + 4 * ow, FIRE_SHEET_ROWS["outer"], fire_cap(t, ow, seed=13, cover=0.8), flip=False)

        inner = fire_values(t, fire_tongues(t, 4 * iw, ir, FIRE_INNER_TONGUES, snake=0.9), seed=21, dark=0.8, fleck=0.14, hot_rows=3)
        inner_up = fire_rising(inner, 5)
        fire_scraps(t, inner, seed=22, start=5, count=7)
        fire_scraps(t, inner_up, seed=22, start=6, count=5)
        fire_put(img, t * 5 * iw, FIRE_SHEET_ROWS["inner"], inner)
        fire_put(img, t * 5 * iw, FIRE_SHEET_ROWS["inner_up"], inner_up)
        inner_cap = fire_cap(t, iw, seed=23, cover=0.85)
        fire_put(img, t * 5 * iw + 4 * iw, FIRE_SHEET_ROWS["inner"], inner_cap, flip=False)

        heart = [[FIRE_HOT if v == FIRE_HOT and y >= 1 else None for v in row] for y, row in enumerate(inner)]
        fire_put(img, t * 5 * iw, FIRE_SHEET_ROWS["heart"], heart)
        fire_put(img, t * 5 * iw, FIRE_SHEET_ROWS["heart_up"], [[FIRE_HOT if v == FIRE_HOT else None for v in row] for row in inner_up])
        fire_put(img, t * 5 * iw + 4 * iw, FIRE_SHEET_ROWS["heart"], [[FIRE_HOT if v == FIRE_HOT else None for v in row] for row in inner_cap], flip=False)

        back = fire_values(t, fire_round_base(fire_tongues(t, gw, gr, FIRE_GRIP_BACK, snake=1.3, wraps=False)), seed=41, dark=0.88, fleck=0.14, hot_rows=4, gap=0.2)
        fire_put(img, t * gw, FIRE_SHEET_ROWS["grip_back"], back)
        front = fire_values(t, fire_round_base(fire_tongues(t, gw, gr, FIRE_GRIP_FRONT, snake=1.2, wraps=False)), seed=42, dark=0.92, fleck=0.18, hot_rows=3, gap=0.22)
        fire_put(img, t * gw, FIRE_SHEET_ROWS["grip_front"], front)
    return img


def fist_strain() -> "Image.Image":
    """A Severed player's ready fist (entity texture, lit by the world, never glowing): a forearm under strain. Dark, bruised skin
    with veins standing out, wrapping seamlessly both ways."""
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    tau = 2 * math.pi
    layers = noise_stack(seed=907, px=size, py=size, cells=(8, 4))
    for y in range(size):
        for x in range(size):
            vein = 0.0
            for fx, wobble, phase, width in ((2, 2.4, 0.4, 0.09), (3, 1.8, 2.3, 0.07), (5, 1.2, 4.4, 0.05)):
                wob = wobble * math.sin(tau * y / size + phase) + 0.5 * wobble * math.sin(3 * tau * y / size + phase * 1.7)
                s = abs(math.sin(math.pi * fx * x / size + wob * 0.5))
                vein = max(vein, max(0.0, 1.0 - s / width))
            mottle = fbm(layers, x, y)
            base = (54 + int(26 * mottle), 26 + int(14 * mottle), 28 + int(14 * mottle))
            high = (20, 8, 12)
            r, g, b = (int(base[i] + (high[i] - base[i]) * vein) for i in range(3))
            img.putpixel((x, y), (r, g, b, int(255 * (0.62 + 0.38 * vein))))
    return img


def main() -> None:
    sprites = build()
    borders = {"panel": 12,
               "tab": 5, "tab_selected": 5, "divider": (10, 0, 10, 0),
               "button": 4, "button_highlighted": 4, "button_disabled": 4, "menu_frame": 10, "menu_well": 4}
    for name, img in sprites.items():
        sprite(img, name, borders.get(name))
    stale = TEXTURES / "gui" / "sprites" / "card.png"  # the old greyscale, tinted placeholder
    for p in (stale, stale.with_suffix(".png.mcmeta")):
        if p.exists():
            p.unlink()
            print(f"removed {p.relative_to(ROOT)}")

    save(reveal_bg(), "gui/reveal_bg.png")
    save(malice_bar(), "gui/malice_bar.png")
    save(combat_panel(), "gui/combat/panel.png")
    save(combat_panel_accent(), "gui/combat/panel_accent.png")
    save(meter_cells(), "gui/combat/meter_cells.png")
    save(combat_slot("normal"), "gui/combat/slot.png")
    save(combat_slot("selected"), "gui/combat/slot_selected.png")
    save(combat_slot("ultimate"), "gui/combat/slot_ultimate.png")
    save(talisman_item(), "item/purification_talisman.png")
    # Rime's projectiles render as these items in flight
    save(icon(ICON_FROST_SHARD, ICE), "item/frost_shard.png")
    save(icon(ICON_GLACIAL_LANCE, ICE), "item/glacial_lance.png")
    save(swap_anchor_item(), "item/swap_anchor.png")
    save(puppet_glow(), "entity/puppet_glow.png")
    save(fist_flame(), "entity/fist_flame.png")
    save(fist_fire(), "entity/fist_fire.png")
    save(fist_strain(), "entity/fist_strain.png")
    save(string_braid(), "item/string_braid.png")
    for kind in ("follow", "stay", "guard", "go"):
        save(order_glyph(kind), f"gui/puppet_order/{kind}.png")

    icons = {"test_dash": icon(ICON_DASH, MALICE), "test_burst": icon(ICON_BURST, MALICE),
             "test_hover": icon(ICON_HOVER, MALICE), "test_aura": icon(ICON_AURA, MALICE),
             "test_shockwave": icon(ICON_SHOCKWAVE, MALICE),
             "frost_shard": icon(ICON_FROST_SHARD, ICE), "glacial_lance": icon(ICON_GLACIAL_LANCE, ICE),
             "frost_field": icon(ICON_FROST_FIELD, ICE), "rime_shell": icon(ICON_RIME_SHELL, ICE),
             "reverse_malice_technique": icon(ICON_RMT, HEAL),
             "positions": icon_positions(), "switchback": icon_switchback(), "awareness": icon_awareness(),
             "mimic": icon_mimic(), "anchor": icon_anchor(), "send_off": icon_send_off(), "exchange": icon_exchange(),
             "momentum_exchange": icon_momentum_exchange(), "total_swap": icon_total_swap(),
             "puppet_creation": icon_puppet_creation(), "possess": icon_possess(), "directive": icon_directive(),
             "focus": icon_focus(), "curio_burst": icon_curio_burst(), "chain_burst": icon_chain_burst(),
             "understudy": icon_understudy(), "stand_in": icon_stand_in(),
             "strings": icon_strings(), "zip_leash": icon_zip_leash(), "strings_reel": icon_strings_reel(),
             "strings_tie": icon_strings_tie(), "strings_zip": icon_strings_zip(), "strings_leash": icon_strings_leash()}
    for name, img in icons.items():
        save(img, f"gui/ability/{name}.png")
    emblems = {"test_kinetic": emblem_kinetic(), "test_flux": emblem_flux(),
               "test_prism": emblem_prism(), "test_apex": emblem_apex(), "rime": emblem_rime(),
               "swap": emblem_swap(), "puppet_manipulation": emblem_puppet_manipulation()}
    for name, img in emblems.items():
        save(img, f"gui/emblem/{name}.png")

    if "--preview" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--preview") + 1])
        items = list(sprites.values()) + [reveal_bg(), malice_bar(), talisman_item()] \
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
