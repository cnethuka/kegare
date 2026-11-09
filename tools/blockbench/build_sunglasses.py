"""The Sunglasses (DESIGN §3.32): small round lenses in a hairline silver frame. Our own model: made here, viewed and exported through Blockbench
the way Rime's ice is. The Python is the source; an edit made in the .bbmodel is lost on the next run.

    python3 -I tools/blockbench/build_sunglasses.py   (needs the running Blockbench, see README)

Writes
    art/blockbench/sunglasses.bbmodel                             the Blockbench project
    src/main/resources/assets/kegare/models/fx/sunglasses.obj     one object, "sunglasses"
    src/main/resources/assets/kegare/textures/entity/sunglasses.png   silver for the frame, indigo with alpha for the lenses

In the head's space (Blockbench's axes about the head's pivot, in pixels: Y up from the neck, the player's right +X, the front -Z), so the game
draws it with the head and it follows every pose. Outside the hat layer (4.5), inside a worn skull (4.75) and a helmet (5.0).
"""
import math
import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import build_glacial_lance as G  # noqa: E402
import lance_shapes as L  # noqa: E402

PX = 1 / 16
SIZE = 256
TEXTURE = G.TEXTURES / "sunglasses.png"
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/sunglasses.obj"
BBMODEL = ROOT / "art/blockbench/sunglasses.bbmodel"

SIDES = 40  # round at any distance: no corner shows
CENTRE_X, CENTRE_Y = 2.0, 3.3  # on the eyes (their row is y 3 to 4), a touch low on the nose
LENS_R = 1.45  # about 3 pixels across, a little wider than an eye
RING = 0.15  # the frame's width: a hairline, wide enough not to break up at 7 blocks
LENS_Z = -4.53  # just outside the hat layer
FRONT_Z = -4.6  # the frame's face
SIDE_X = 4.58  # the arms, just outside the hat layer
WIRE = 0.07  # half a wire's width (bridge and arms)
ARM_Y = 3.75  # where the arms leave the rims
ARM_END_Z = 2.8  # how far back they run
BRIDGE_Y = 3.82
STEP = 0.24  # how far the bridge steps up

# Where things are in the texture, in texels.
LENS_BOX = (0, 0, 128, 128)  # the lens, painted as a disc
SILVER_BOX = (136, 8, 248, 120)  # the frame's silver


class Face:
    """A face in the shape G.export wants: its corners in blocks, and where each is in the texture."""
    piece = "sunglasses"

    def __init__(self, corners, uvs):
        self.polygon = [(c[0] * PX, c[1] * PX, c[2] * PX) for c in corners]
        self._uvs = uvs

    def uvs(self):
        return self._uvs


def silver_uv(shade=0.5, along=0.5):
    x0, y0, x1, y1 = SILVER_BOX
    return (x0 + (x1 - x0) * along, y0 + (y1 - y0) * shade)


def silver(corners, shade=0.5):
    return Face(corners, [silver_uv(shade, i / max(1, len(corners) - 1)) for i in range(len(corners))])


def circle(cx, cy, r, z, i):
    t = 2 * math.pi * i / SIDES
    return (cx + r * math.cos(t), cy + r * math.sin(t), z)


def lens(cx):
    """A disc, its UVs the same way up on both lenses, so the highlight falls the same way on each."""
    x0, y0, x1, y1 = LENS_BOX
    corners, uvs = [], []
    for i in range(SIDES):
        p = circle(cx, CENTRE_Y, LENS_R + 0.02, LENS_Z, i)
        corners.append(p)
        uvs.append((x0 + (x1 - x0) * (0.5 + 0.5 * (p[0] - cx) / (LENS_R + 0.02)), y0 + (y1 - y0) * (0.5 - 0.5 * (p[1] - CENTRE_Y) / (LENS_R + 0.02))))
    # Seen from the front (looking along +Z with +X on the view's left), this runs clockwise on screen for rising i; reverse to face -Z.
    return Face(list(reversed(corners)), list(reversed(uvs)))


def ring(cx):
    """The rim: its face and its outer edge, back to the lens."""
    faces = []
    r0, r1 = LENS_R, LENS_R + RING
    for i in range(SIDES):
        a0, a1 = circle(cx, CENTRE_Y, r0, FRONT_Z, i), circle(cx, CENTRE_Y, r0, FRONT_Z, i + 1)
        b0, b1 = circle(cx, CENTRE_Y, r1, FRONT_Z, i), circle(cx, CENTRE_Y, r1, FRONT_Z, i + 1)
        c0, c1 = circle(cx, CENTRE_Y, r1, LENS_Z - 0.0, i), circle(cx, CENTRE_Y, r1, LENS_Z - 0.0, i + 1)
        d0, d1 = circle(cx, CENTRE_Y, r0, LENS_Z, i), circle(cx, CENTRE_Y, r0, LENS_Z, i + 1)
        light = 0.25 + 0.5 * (0.5 - 0.5 * math.sin(2 * math.pi * (i + 0.5) / SIDES))
        faces.append(silver([a1, b1, b0, a0], light))
        faces.append(silver([b1, c1, c0, b0], 0.85))
        faces.append(silver([d1, a1, a0, d0], 0.85))
    return faces


def wire(points, width=WIRE, shade=0.4):
    """A thin square bar along a path of (x, y, z) corners."""
    faces = []
    for p, q in zip(points, points[1:]):
        d = L.norm(L.sub(q, p))
        up = (0.0, 1.0, 0.0) if abs(d[1]) < 0.9 else (1.0, 0.0, 0.0)
        side = L.norm(L.cross(d, up))
        up = L.norm(L.cross(side, d))
        ring4 = [L.add(L.mul(side, width), L.mul(up, width)), L.add(L.mul(side, -width), L.mul(up, width)),
                 L.add(L.mul(side, -width), L.mul(up, -width)), L.add(L.mul(side, width), L.mul(up, -width))]
        for i in range(4):
            a, b = ring4[i], ring4[(i + 1) % 4]
            quad = [L.add(p, a), L.add(p, b), L.add(q, b), L.add(q, a)]
            mid = L.mul(L.sum_points(quad), 0.25)
            axis = L.mul(L.sum_points([p, q]), 0.5)
            out = L.sub(mid, axis)
            if L.dot(L.polygon_normal(quad), out) < 0:
                quad.reverse()
            faces.append(silver(quad, shade + 0.25 * i / 3))
    return faces


def faces():
    out = []
    for sign in (1, -1):
        cx = sign * CENTRE_X
        out.append(lens(cx))
        out += ring(cx)
        # The arm: from the rim's outer edge along the front of the head, round the corner, straight back along the side.
        rim_x = math.sqrt(max(0.0, (LENS_R + RING * 0.5) ** 2 - (ARM_Y - CENTRE_Y) ** 2))
        out += wire([(sign * (CENTRE_X + rim_x), ARM_Y, FRONT_Z + WIRE), (sign * SIDE_X, ARM_Y, FRONT_Z + WIRE),
                     (sign * SIDE_X, ARM_Y, ARM_END_Z)])
    # The bridge: out of one rim nearly flat, a small sharp step up, and on into the other rim higher. Not an arc.
    inner = CENTRE_X - math.sqrt(max(0.0, (LENS_R + RING * 0.5) ** 2 - (BRIDGE_Y - CENTRE_Y) ** 2))
    z = FRONT_Z + WIRE
    out += wire([(inner, BRIDGE_Y, z), (0.1, BRIDGE_Y - 0.04, z), (-0.06, BRIDGE_Y + STEP, z), (-inner, BRIDGE_Y + STEP, z)])
    return out


def paint():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    x0, y0, x1, y1 = LENS_BOX
    w = x1 - x0
    for ty in range(y0, y1):
        for tx in range(x0, x1):
            u, v = (tx + 0.5 - x0) / w * 2 - 1, (ty + 0.5 - y0) / w * 2 - 1
            r = math.hypot(u, v)
            # Deep indigo: darker toward the top, a little lighter at the bottom, a touch denser at the rim.
            t = (v + 1) / 2
            rgb = [14 + 30 * t, 16 + 38 * t, 66 + 62 * t]
            alpha = 0.86 - 0.2 * t + 0.08 * r ** 4
            # A small highlight, high on one side.
            hx, hy = (u + 0.36) / 0.3, (v + 0.46) / 0.13
            d = math.hypot(hx * math.cos(0.5) + hy * math.sin(0.5), -hx * math.sin(0.5) + hy * math.cos(0.5))
            if d < 1:
                k = min(1.0, (1 - d) * 3.5)
                rgb = [c + (p - c) * k for c, p in zip(rgb, (188, 198, 240))]
                alpha += (0.92 - alpha) * k
            px[tx, ty] = tuple(int(round(max(0, min(255, c)))) for c in rgb) + (int(round(min(1.0, alpha) * 255)),)
    x0, y0, x1, y1 = SILVER_BOX
    for ty in range(y0 - 8, y1 + 8):
        for tx in range(x0 - 8, x1 + 8):
            t = (ty - y0) / (y1 - y0)
            k = 214 - 92 * max(0.0, min(1.0, t))
            px[tx, ty] = (int(k), int(k + 3), int(k + 10), 255)
    return img


def build():
    made = faces()
    print(len(made), "faces")
    paint().save(TEXTURE, optimize=True)
    shutil.copyfile(G.TEXTURES / "glacial_lance.png.mcmeta", G.TEXTURES / "sunglasses.png.mcmeta")
    G.export("sunglasses", {"sunglasses": [f.polygon for f in made]}, made, SIZE, SIZE, TEXTURE, OBJ_PATH, BBMODEL)


if __name__ == "__main__":
    build()
