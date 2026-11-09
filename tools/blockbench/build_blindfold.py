"""The Blindfold (DESIGN §3.32): a band of soft black cloth wrapped round the head at eye level. Our own model: made here, viewed and exported
through Blockbench the way Rime's ice is. The Python is the source; an edit made in the .bbmodel is lost on the next run.

    python3 -I tools/blockbench/build_blindfold.py   (needs the running Blockbench, see README)

Writes
    art/blockbench/blindfold.bbmodel                             the Blockbench project
    src/main/resources/assets/kegare/models/fx/blindfold.obj     one object, "blindfold"
    src/main/resources/assets/kegare/textures/entity/blindfold.png   high resolution, opaque, filtered smooth

In the head's space (Blockbench's axes about the head's pivot, in pixels: Y up from the neck, the player's right +X, the front -Z), so the game
draws it with the head and it follows every pose. It sits outside the hat layer (4.5) and inside a helmet (5.0) and a worn skull (4.75).

The band is one height field over (a, y): a is how far along the band from the middle of the front, either way round the head, y the height.
Its top and bottom edges come from EDGE_TOP and EDGE_BOTTOM; how far it stands off the head is lobes over the eyes, a pinch over the nose,
roundness across it, and the folds. The texture is painted from the same folds, so the grey sits on the real ridges.
"""
import math
import random
import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import build_glacial_lance as G  # noqa: E402
import lance_shapes as L  # noqa: E402

PX = 1 / 16
WIDTH, HEIGHT = 1024, 128
TEXTURE = G.TEXTURES / "blindfold.png"
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/blindfold.obj"
BBMODEL = ROOT / "art/blockbench/blindfold.bbmodel"

#: Distance of the band's middle from the head's axis, and how round its corners are. Hat layer 4.5, worn skull 4.75, helmet 5.0.
BASE, CORNER = 4.56, 0.45
INNER = 4.02  # where the rims go back to: just off the skin
STRAIGHT = BASE - CORNER
QUARTER = CORNER * math.pi / 2
HALF_ROUND = 4 * STRAIGHT + 2 * QUARTER  # from the middle of the front to the middle of the back
Y_MIN, Y_MAX = 1.8, 6.2  # the texture's range of heights

# (a, y): pinched and dipping at the bridge, a lobe over each eye, pulling tight and narrow toward the temples, a plain strap behind.
EDGE_BOTTOM = [(0.0, 2.2), (0.35, 2.34), (0.95, 2.85), (2.0, 2.5), (3.05, 2.66), (4.3, 3.12), (6.0, 3.22), (12.0, 3.26), (HALF_ROUND, 3.26)]
EDGE_TOP = [(0.0, 5.2), (0.7, 5.3), (2.0, 5.7), (3.2, 5.62), (4.3, 5.34), (6.0, 5.24), (12.0, 5.2), (HALF_ROUND, 5.2)]

CHARCOAL = (21, 22, 26)
SHADOW = (11, 11, 14)
RIDGE_GREY = (112, 114, 122)


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def through(points, a):
    for (a0, y0), (a1, y1) in zip(points, points[1:]):
        if a <= a1:
            return y0 + (y1 - y0) * smooth((a - a0) / (a1 - a0))
    return points[-1][1]


class Noise:
    """Smooth 1D noise, -1 to 1: what makes an edge or a ridge uneven."""

    def __init__(self, seed, count=256):
        rng = random.Random(seed)
        self.values = [rng.uniform(-1, 1) for _ in range(count)]

    def __call__(self, x):
        i = math.floor(x)
        f = smooth(x - i)
        n = len(self.values)
        return self.values[i % n] * (1 - f) + self.values[(i + 1) % n] * f


EDGE_NOISE = {1: Noise(11), -1: Noise(12)}
RIDGE_NOISE = {1: Noise(21), -1: Noise(22)}


def creases(side):
    """The folds on one side: each runs out and up from near the middle, a diagonal climbing toward the temple, the cloth pulled up and back
    round the head. (y at the middle, y at the temple, where it starts and stops along the band, how strong, how wide.)"""
    rng = random.Random(40 + side)
    out = []
    for k, (y0, y1, start) in enumerate([(2.55, 3.75, 0.9), (2.95, 4.35, 0.55), (3.5, 4.8, 0.4), (4.15, 5.12, 0.45)]):
        out.append((y0 + rng.uniform(-0.07, 0.07), y1 + rng.uniform(-0.05, 0.05), start + rng.uniform(-0.1, 0.1), rng.uniform(4.9, 6.4),
                    rng.uniform(0.82, 1.0), rng.uniform(0.2, 0.27), k))
    return out


CREASES = {1: creases(1), -1: creases(-1)}


def fold(a, y):
    """The folds' ridges at a point: above 0 on a ridge, below in the valley under it."""
    side = 1 if a >= 0 else -1
    far = abs(a)
    total = 0.0
    for y0, y1, start, stop, strength, width, k in CREASES[side]:
        if far < start or far > stop:
            continue
        along = min(1.0, far / 4.6)  # straight enough to read as a diagonal
        centre = y0 + (y1 - y0) * along + 0.05 * RIDGE_NOISE[side](far * 1.6 + 9 * k)
        d = y - centre
        envelope = smooth((far - start) / 0.9) * (1 - smooth((far - (stop - 1.8)) / 1.8))
        uneven = 0.78 + 0.22 * RIDGE_NOISE[side](far * 2.3 + 31 * k)
        w = width * (1.1 - 0.3 * along) * (0.9 + 0.2 * RIDGE_NOISE[side](far * 3.1 + 53 * k))
        ridge = math.exp(-((d / w) ** 2)) - 0.55 * math.exp(-(((d + 1.7 * w) / (1.3 * w)) ** 2))
        total += ridge * envelope * uneven * strength
    return total


def top(a):
    side = 1 if a >= 0 else -1
    return through(EDGE_TOP, abs(a)) + 0.03 * EDGE_NOISE[side](abs(a) * 1.7)


def bottom(a):
    side = 1 if a >= 0 else -1
    return through(EDGE_BOTTOM, abs(a)) + 0.03 * EDGE_NOISE[side](abs(a) * 1.7 + 77)


def stand_off(a, y):
    """How far the cloth stands proud of BASE."""
    far = abs(a)
    t = (y - bottom(a)) / max(0.01, top(a) - bottom(a))
    across = 1 - (2 * t - 1) ** 4  # rounded across, its edges closest to the head
    lobe = 0.06 * math.exp(-(((far - 2.0) / 1.5) ** 2))
    pinch = -0.03 * math.exp(-((far / 0.3) ** 2))
    tight = 1 - 0.8 * smooth((far - 4.0) / 2.5)  # flat on the head past the temple
    return max(0.0, (0.03 + lobe) * across * tight + pinch * across + 0.055 * fold(a, y))


def round_the_head(a, off):
    """Where on the band's path: x, z and the outward normal, off the path by `off`."""
    side = 1 if a >= 0 else -1
    s = abs(a)
    if s <= STRAIGHT:
        x, z, nx, nz = s, -BASE, 0.0, -1.0
    elif s <= STRAIGHT + QUARTER:
        t = (s - STRAIGHT) / CORNER - math.pi / 2
        nx, nz = math.cos(t), math.sin(t)
        x, z = STRAIGHT + CORNER * nx, -STRAIGHT + CORNER * nz
    elif s <= 3 * STRAIGHT + QUARTER:
        x, z, nx, nz = BASE, -STRAIGHT + (s - STRAIGHT - QUARTER), 1.0, 0.0
    elif s <= 3 * STRAIGHT + 2 * QUARTER:
        t = (s - 3 * STRAIGHT - QUARTER) / CORNER
        nx, nz = math.cos(t), math.sin(t)
        x, z = STRAIGHT + CORNER * nx, STRAIGHT + CORNER * nz
    else:
        x, z, nx, nz = STRAIGHT - (s - 3 * STRAIGHT - 2 * QUARTER), BASE, 0.0, 1.0
    return side * (x + nx * off), z + nz * off, side * nx, nz


def uv(a, y):
    return ((a + HALF_ROUND) / (2 * HALF_ROUND) * WIDTH, (Y_MAX - y) / (Y_MAX - Y_MIN) * HEIGHT)


class Quad:
    """A face in the shape G.export wants: its corners in blocks, and where each is in the texture."""
    piece = "blindfold"

    def __init__(self, corners):
        self.polygon = [(c[0] * PX, c[1] * PX, c[2] * PX) for c, _ in corners]
        self._uvs = [t for _, t in corners]

    def uvs(self):
        return self._uvs


def stations():
    """The cuts along the band: fine over the front, where the folds are; coarse round the back."""
    out = []
    a = 0.0
    while a < HALF_ROUND - 1e-6:
        out.append(a)
        a += 0.22 if a < 5.0 else 0.8 if a < 9.0 else 1.6
    out.append(HALF_ROUND)
    return [-s for s in reversed(out[1:])] + out


def band_faces():
    faces = []
    rows = 10
    cuts = stations()

    def at(a, j):
        y = bottom(a) + (top(a) - bottom(a)) * j / rows
        x, z, _, _ = round_the_head(a, stand_off(a, y))
        return (x, y, z), uv(a, y)

    def rim(a, j):
        """The band's edge turned back to the head: from the outer edge in, painted dark."""
        y = bottom(a) + (top(a) - bottom(a)) * j / rows
        x, z, _, _ = round_the_head(a, INNER - BASE)
        return (x, y, z), uv(a, Y_MAX - 0.02)

    for a0, a1 in zip(cuts, cuts[1:]):
        for j in range(rows):
            faces.append(Quad([at(a0, j), at(a0, j + 1), at(a1, j + 1), at(a1, j)]))
        faces.append(Quad([(at(a0, rows)[0], rim(a0, rows)[1]), rim(a0, rows), rim(a1, rows), (at(a1, rows)[0], rim(a1, rows)[1])]))
        faces.append(Quad([rim(a0, 0), (at(a0, 0)[0], rim(a0, 0)[1]), (at(a1, 0)[0], rim(a1, 0)[1]), rim(a1, 0)]))
    return faces


# The overlap behind: one end of the strap laid flat over the other, stopping in a plain slanted edge. No knot, no tails.
FLAP_FROM, FLAP_TO = -1.1, 1.0  # x, across the back of the head
FLAP_OFF = 0.1
FLAP_BOTTOM, FLAP_TOP = 3.2, 5.24


def flap_uv(x, y):
    return uv(HALF_ROUND - 2.4 + (x - FLAP_FROM) / (FLAP_TO - FLAP_FROM) * 2.2, y)


def flap_faces():
    z = BASE + FLAP_OFF
    slant = 0.42  # how far the free end leans

    def corner(x, y):
        return (x, y, z), flap_uv(x, y)

    def inside(x, y):
        return (x, y, BASE - 0.02), flap_uv(FLAP_FROM, Y_MAX - 0.02)

    x0, x1 = FLAP_FROM + slant, FLAP_FROM
    x2 = FLAP_TO
    # Seen from behind, the player's right (+X) is on the left of the view; this wind faces +Z.
    faces = [Quad([corner(x0, FLAP_BOTTOM), corner(x2, FLAP_BOTTOM), corner(x2, FLAP_TOP), corner(x1, FLAP_TOP)])]
    faces.append(Quad([inside(x1, FLAP_TOP), inside(x0, FLAP_BOTTOM), corner(x0, FLAP_BOTTOM), corner(x1, FLAP_TOP)]))
    faces.append(Quad([inside(x2, FLAP_TOP), inside(x1, FLAP_TOP), corner(x1, FLAP_TOP), corner(x2, FLAP_TOP)]))
    faces.append(Quad([inside(x0, FLAP_BOTTOM), inside(x2, FLAP_BOTTOM), corner(x2, FLAP_BOTTOM), corner(x0, FLAP_BOTTOM)]))
    return faces


def paint():
    weave = random.Random(7)
    img = Image.new("RGB", (WIDTH, HEIGHT), SHADOW)
    px = img.load()
    for ty in range(HEIGHT):
        y = Y_MAX - (ty + 0.5) / HEIGHT * (Y_MAX - Y_MIN)
        for tx in range(WIDTH):
            a = (tx + 0.5) / WIDTH * 2 * HALF_ROUND - HALF_ROUND
            f = fold(a, y)
            shade = weave.uniform(-1.6, 1.6) + 1.2 * math.sin(tx * 1.9) * math.sin(ty * 2.3)
            if f >= 0:
                t = min(1.0, f * 1.15) ** 1.3
                rgb = [c + (r - c) * t for c, r in zip(CHARCOAL, RIDGE_GREY)]
            else:
                t = min(1.0, -f * 1.6)
                rgb = [c + (s - c) * t for c, s in zip(CHARCOAL, SHADOW)]
            # The overlap's free end: a soft fold just inside it.
            far = abs(a)
            if a > 0 and far > HALF_ROUND - 2.4:
                d = (far - (HALF_ROUND - 2.4)) / 2.2
                t = math.exp(-(((d - 0.1) / 0.06) ** 2)) * 0.45
                rgb = [c + (r - c) * t for c, r in zip(rgb, RIDGE_GREY)]
            px[tx, ty] = tuple(max(0, min(255, int(round(c + shade)))) for c in rgb)
    return img


def build():
    faces = band_faces() + flap_faces()
    for f in faces:
        p = f.polygon
        n, mid = L.polygon_normal(p), L.mul(L.sum_points(p), 1.0 / len(p))
        if abs(n[1]) < 0.5:  # (the rims above and below look up and down)
            assert L.dot(n, (mid[0], 0.0, mid[2])) > 0, "the band's faces must look outward"
    print(len(faces), "faces")
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    paint().save(TEXTURE, optimize=True)
    shutil.copyfile(G.TEXTURES / "glacial_lance.png.mcmeta", G.TEXTURES / "blindfold.png.mcmeta")
    G.export("blindfold", {"blindfold": [f.polygon for f in faces]}, faces, WIDTH, HEIGHT, TEXTURE, OBJ_PATH, BBMODEL)


if __name__ == "__main__":
    build()
