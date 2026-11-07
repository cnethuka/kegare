"""Anchor's token (DESIGN §3.19): a small solid teal coin, the thing Swap leaves behind to swap back to. The mesh and its textures,
made the way Rime's ice is (build_glacial_lance.py's islands, atlas and Blockbench export) but painted in Swap's teal.

    python3 -I tools/blockbench/build_swap_anchor.py            build everything (needs the running Blockbench, see README)
    python3 -I tools/blockbench/build_swap_anchor.py --preview  also render run/anchor_preview/*.png from Blockbench

Writes
    art/blockbench/swap_anchor.bbmodel                              the Blockbench project
    src/main/resources/assets/kegare/models/fx/swap_anchor.obj      one object, "token"
    src/main/resources/assets/kegare/textures/entity/swap_anchor.png       the colour: deep teal faces, a bright inlaid ring, pale rims
    src/main/resources/assets/kegare/textures/entity/swap_anchor_glow.png  what stays lit in the dark: the ring, the mark, the rims

The coin lies in the XY plane, centred on the origin, its faces looking along +Z and -Z (AnchorRenderer turns it from there). The
Python is the source: an edit made in the .bbmodel is lost on the next run.
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

DENSITY = 1024  # texels per block: the coin is a quarter of a block across, so its face is about 260 texels
WIDTH = 512
TEXTURES = G.TEXTURES
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/swap_anchor.obj"
BBMODEL = ROOT / "art/blockbench/swap_anchor.bbmodel"

SIDES = 16
RADIUS = 0.13  # blocks, to the rim
HALF = 0.026  # half the thickness
BEVEL = 0.014  # how far the bevel cuts in, both ways

DEEP = (8, 58, 62)
BODY = (14, 104, 102)
TEAL = (28, 168, 156)
BRIGHT = (46, 242, 218)  # Swap's teal (SwapFx.GLOW)
PALE = (204, 251, 241)  # SwapFx.PALE

RIM = 0.0035  # the pale line round each face, lit in the dark
RING_AT, RING_WIDTH = 0.072, 0.007  # the inlaid ring on each face (radius, width)
MARK = 0.022  # the diamond in the middle, centre to point

VIEWS = {
    # name: (pieces shown, camera, target, background); Blockbench pixels (16 a block)
    "face": (["token"], (0.6, 0.8, 9), (0, 0, 0), 0x2b2f3a),
    "edge": (["token"], (8, 1.5, 3), (0, 0, 0), 0x2b2f3a),
    "sky": (["token"], (4, -2, 7), (0, 0, 0), 0x9ebef0),
}


def ring(radius, z):
    return [(radius * math.cos(2 * math.pi * i / SIDES), radius * math.sin(2 * math.pi * i / SIDES), z) for i in range(SIDES)]


def outward(poly):
    """Wound counter-clockwise seen from outside (the coin is convex about the origin, so outside is away from it)."""
    centre = L.mul(L.sum_points(poly), 1.0 / len(poly))
    return poly if L.dot(L.polygon_normal(poly), centre) > 0 else list(reversed(poly))


def faces():
    """(kind, polygon) for every face: the two flat faces, the bevels round them, and the band round the edge."""
    inner = RADIUS - BEVEL
    top, top_rim = ring(inner, HALF), ring(RADIUS, HALF - BEVEL)
    bottom, bottom_rim = ring(inner, -HALF), ring(RADIUS, -(HALF - BEVEL))
    out = [("face", outward(top)), ("face", outward(bottom))]
    for i in range(SIDES):
        j = (i + 1) % SIDES
        out.append(("bevel", outward([top[i], top_rim[i], top_rim[j], top[j]])))
        out.append(("bevel", outward([bottom[i], bottom_rim[i], bottom_rim[j], bottom[j]])))
        out.append(("band", outward([top_rim[i], bottom_rim[i], bottom_rim[j], top_rim[j]])))
    return out


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def paint(islands, kinds, height):
    colour = Image.new("RGBA", (WIDTH, height), PALE + (255,))
    glow = Image.new("RGBA", (WIDTH, height), (0, 0, 0, 0))
    cpx, gpx = colour.load(), glow.load()
    for isl, kind in zip(islands, kinds):
        for ty in range(isl.y0, isl.y0 + isl.h):
            for tx in range(isl.x0, isl.x0 + isl.w):
                p, f = isl.point(tx, ty)
                d = isl.edge_distance(f)
                lit = False
                if kind == "face":
                    r = math.hypot(p[0], p[1])
                    c = lerp(BODY, DEEP, r / (RADIUS - BEVEL))
                    if d < RIM:
                        # The pale line round the face, and the padding past it (a sample just over its edge is still the line).
                        c, lit = PALE, True
                    elif abs(r - RING_AT) < RING_WIDTH / 2:
                        c, lit = BRIGHT, True
                    elif abs(p[0]) + abs(p[1]) < MARK:
                        c, lit = PALE if abs(p[0]) + abs(p[1]) < MARK * 0.45 else BRIGHT, True
                elif kind == "bevel":
                    # Bright where it leaves the face, teal out at the rim; no seams between the facets, so it reads round.
                    c = lerp(BRIGHT, TEAL, (HALF - abs(p[2])) / BEVEL)  # by height: even along each flat facet
                else:  # the band round the edge: teal, a little darker along its middle
                    c = lerp(BODY, TEAL, abs(p[2]) / (HALF - BEVEL))
                rgb = tuple(int(round(x)) for x in c)
                cpx[tx, ty] = rgb + (255,)
                if lit:
                    gpx[tx, ty] = rgb + (255,)
    return colour, glow


def build(preview=False):
    made = faces()
    islands, kinds = [], []
    for kind, poly in made:
        islands.append(G.Island("token", poly, density=DENSITY))
        kinds.append(kind)
    height = G.pack(islands, WIDTH)
    colour, glow = paint(islands, kinds, height)
    colour.save(TEXTURES / "swap_anchor.png", optimize=True)
    glow.save(TEXTURES / "swap_anchor_glow.png", optimize=True)
    for name in ("swap_anchor", "swap_anchor_glow"):  # filtered smooth, like the lance's
        shutil.copyfile(TEXTURES / "glacial_lance.png.mcmeta", TEXTURES / f"{name}.png.mcmeta")
    print(f"atlas {WIDTH}x{height}, {len(islands)} faces")
    shapes = {"token": [poly for _, poly in made]}
    bb = G.export("swap_anchor", shapes, islands, WIDTH, height, TEXTURES / "swap_anchor.png", OBJ_PATH, BBMODEL)
    if preview:
        render_previews(bb)


def render_previews(bb):
    import base64
    import io
    import json
    bb.js(G.PREVIEW_JS)
    out = ROOT / "run" / "anchor_preview"
    out.mkdir(parents=True, exist_ok=True)
    for name, (show, eye, target, bg) in VIEWS.items():
        url = bb.js(f"kegareLance.shoot({json.dumps(show)}, {list(eye)}, {list(target)}, {bg})")
        Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / f"{name}.png")
    print("previews in", out.relative_to(ROOT))


if __name__ == "__main__":
    build(preview="--preview" in sys.argv)
