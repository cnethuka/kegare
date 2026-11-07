"""Glacial Spire's ice spike: the mesh and textures, the same way as the lance (build_glacial_lance.py) and in the same style, driving
a running Blockbench (cdp.py).

    python3 -I tools/blockbench/build_glacial_spire.py            build everything
    python3 -I tools/blockbench/build_glacial_spire.py --preview  also render run/spire_preview/*.png from Blockbench

Writes
    art/blockbench/glacial_spire.bbmodel                              the Blockbench project
    src/main/resources/assets/kegare/models/fx/glacial_spire.obj      one object per piece (spire_shapes.py)
    src/main/resources/assets/kegare/textures/entity/glacial_spire.png       the colour: navy in each face to pale frost at its ridges
    src/main/resources/assets/kegare/textures/entity/glacial_spire_glow.png  what stays lit in the dark: ridges and cracks

The Python is the source (spire_shapes.py makes the crystals; the lance's painter lays them out and paints them). The spike is far
bigger than the lance and seen from further off, so its atlas is coarser (160 texels a block, filtered smooth) and its ridge lines
and cracks are wider in blocks.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import build_glacial_lance as G  # noqa: E402
import spire_shapes as S  # noqa: E402

DENSITY = 160
WIDTH = 1024
TEXTURES = G.TEXTURES
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/glacial_spire.obj"
BBMODEL = ROOT / "art/blockbench/glacial_spire.bbmodel"
CRACKS = dict(seed=41, across=(0.75, 0.75), along=(-0.7, 3.5), step=0.2, axis=1, jitter=0.08)
LINES = dict(ridge=0.012, band_width=0.075, crack_width=0.011, crack_glow=0.032)

VIEWS = {
    # name: (pieces shown, camera, target, background); Blockbench pixels, Y up, the ground at 0
    "full": (None, (120, 60, 120), (0, 24, 0), 0x2b2f3a),
    "low": ([n for n, _, _ in S.PIECES if n != "heavy"], (120, 60, 120), (0, 24, 0), 0x2b2f3a),
    "ground": (None, (110, 8, 70), (0, 28, 0), 0x2b2f3a),
    "top": (None, (10, 170, 10), (0, 0, 0), 0x2b2f3a),
    "sky": (None, (110, 10, 110), (0, 30, 0), 0x9ebef0),
    "chunks": ([f"chunk_{i}" for i in range(S.CHUNKS)], (20, 14, 20), (0, 0, 0), 0x2b2f3a),
}


def build(preview=False):
    shapes = S.pieces()
    islands = [G.Island(name, poly, density=DENSITY) for name, polys in shapes.items() for poly in polys]
    height = G.pack(islands, WIDTH)
    colour, glow = G.paint(islands, height, WIDTH, cracks=G.Cracks(**CRACKS), **LINES)
    colour.save(TEXTURES / "glacial_spire.png", optimize=True)
    glow.save(TEXTURES / "glacial_spire_glow.png", optimize=True)
    for name in ("glacial_spire", "glacial_spire_glow"):  # filtered smooth, like the lance's
        shutil.copyfile(TEXTURES / "glacial_lance.png.mcmeta", TEXTURES / f"{name}.png.mcmeta")
    print(f"atlas {WIDTH}x{height}, {len(islands)} faces")
    bb = G.export("glacial_spire", shapes, islands, WIDTH, height, TEXTURES / "glacial_spire.png", OBJ_PATH, BBMODEL)
    if preview:
        render_previews(bb)


def render_previews(bb):
    import base64
    import io
    import json
    from PIL import Image
    bb.js(G.PREVIEW_JS)
    out = ROOT / "run" / "spire_preview"
    out.mkdir(parents=True, exist_ok=True)
    every = [n for n, _, _ in S.PIECES]
    for name, (show, eye, target, bg) in VIEWS.items():
        url = bb.js(f"kegareLance.shoot({json.dumps(show or every)}, {list(eye)}, {list(target)}, {bg})")
        Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / f"{name}.png")
    print("previews in", out.relative_to(ROOT))


if __name__ == "__main__":
    build(preview="--preview" in sys.argv)
