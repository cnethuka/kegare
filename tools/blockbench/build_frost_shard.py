"""Frost Shard's and Splinter's thrown crystals: the mesh and textures, the same way as the lance (build_glacial_lance.py) and in the
same style, driving a running Blockbench (cdp.py).

    python3 -I tools/blockbench/build_frost_shard.py            build everything
    python3 -I tools/blockbench/build_frost_shard.py --preview  also render run/shard_preview/*.png from Blockbench

Writes
    art/blockbench/frost_shard.bbmodel                              the Blockbench project
    src/main/resources/assets/kegare/models/fx/frost_shard.obj      one object per piece (shard_shapes.py)
    src/main/resources/assets/kegare/textures/entity/frost_shard.png       the colour: navy in each face to pale frost at its ridges
    src/main/resources/assets/kegare/textures/entity/frost_shard_glow.png  what stays lit in the dark: ridges and cracks

The pieces are far smaller than the lance's, so their ridge lines and cracks are finer in blocks (the same few pixels on screen).
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import build_glacial_lance as G  # noqa: E402
import shard_shapes as S  # noqa: E402

DENSITY = 640
WIDTH = 512
TEXTURES = G.TEXTURES
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/frost_shard.obj"
BBMODEL = ROOT / "art/blockbench/frost_shard.bbmodel"
CRACKS = dict(seed=9, across=(0.09, 0.09), along=(-0.3, 0.36), step=0.045, axis=2, jitter=0.02)
LINES = dict(ridge=0.0032, band_width=0.016, crack_width=0.0028, crack_glow=0.008)

VIEWS = {
    # name: (pieces shown, camera, target, background); Blockbench pixels
    "side": (["frost_shard"], (9, 3, 2), (0, 0, 1), 0x2b2f3a),
    "front": (["frost_shard"], (3, 2, 10), (0, 0, 0), 0x2b2f3a),
    "sky": (["frost_shard"], (8, -3, 3), (0, 0, 1), 0x9ebef0),
    "splinter": (["splinter"], (7, 2.5, 1.5), (0, 0, 0.5), 0x2b2f3a),
    "both": (["frost_shard", "splinter"], (12, 5, 4), (0, 0, 1), 0x2b2f3a),
    "chips": ([f"chip_{i}" for i in range(S.CHIPS)], (3, 2, 3), (0, 0, 0), 0x2b2f3a),
}


def build(preview=False):
    shapes = S.pieces()
    islands = [G.Island(name, poly, density=DENSITY) for name, polys in shapes.items() for poly in polys]
    height = G.pack(islands, WIDTH)
    colour, glow = G.paint(islands, height, WIDTH, cracks=G.Cracks(**CRACKS), **LINES)
    colour.save(TEXTURES / "frost_shard.png", optimize=True)
    glow.save(TEXTURES / "frost_shard_glow.png", optimize=True)
    for name in ("frost_shard", "frost_shard_glow"):  # filtered smooth, like the lance's
        shutil.copyfile(TEXTURES / "glacial_lance.png.mcmeta", TEXTURES / f"{name}.png.mcmeta")
    print(f"atlas {WIDTH}x{height}, {len(islands)} faces")
    bb = G.export("frost_shard", shapes, islands, WIDTH, height, TEXTURES / "frost_shard.png", OBJ_PATH, BBMODEL)
    if preview:
        render_previews(bb, shapes)


def render_previews(bb, shapes):
    import base64
    import io
    import json
    from PIL import Image
    bb.js(G.PREVIEW_JS)
    out = ROOT / "run" / "shard_preview"
    out.mkdir(parents=True, exist_ok=True)
    for name, (show, eye, target, bg) in VIEWS.items():
        url = bb.js(f"kegareLance.shoot({json.dumps(show)}, {list(eye)}, {list(target)}, {bg})")
        Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / f"{name}.png")
    print("previews in", out.relative_to(ROOT))


if __name__ == "__main__":
    build(preview="--preview" in sys.argv)
