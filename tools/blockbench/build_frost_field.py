"""Frost Field's crystals jutting out of the frosted ground: the mesh and textures, the same way as the lance (build_glacial_lance.py)
and in the same style, driving a running Blockbench (cdp.py).

    python3 -I tools/blockbench/build_frost_field.py            build everything
    python3 -I tools/blockbench/build_frost_field.py --preview  also render run/field_preview/*.png from Blockbench

Writes
    art/blockbench/frost_field.bbmodel                              the Blockbench project
    src/main/resources/assets/kegare/models/fx/frost_field.obj      one object per cluster (field_shapes.py)
    src/main/resources/assets/kegare/textures/entity/frost_field.png       the colour: navy in each face to pale frost at its ridges
    src/main/resources/assets/kegare/textures/entity/frost_field_glow.png  what stays lit in the dark: ridges and cracks

Small crystals seen a few blocks off: finer than the spire's, a little coarser than the shard's.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import build_glacial_lance as G  # noqa: E402
import field_shapes as S  # noqa: E402

DENSITY = 420
WIDTH = 512
TEXTURES = G.TEXTURES
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/frost_field.obj"
BBMODEL = ROOT / "art/blockbench/frost_field.bbmodel"
CRACKS = dict(seed=19, across=(0.12, 0.12), along=(-0.25, 0.75), step=0.06, axis=1, jitter=0.025)
LINES = dict(ridge=0.0045, band_width=0.022, crack_width=0.004, crack_glow=0.011)

VIEWS = {
    # name: (pieces shown, camera, target, background); Blockbench pixels, Y up, the ground at 0
    "all": (None, (22, 12, 22), (0, 4, 0), 0x2b2f3a),
    "low": (None, (26, 3, 10), (0, 5, 0), 0x2b2f3a),
    "top": (None, (2, 26, 2), (0, 0, 0), 0x2b2f3a),
    "sky": (None, (22, 1, 16), (0, 6, 0), 0x9ebef0),
    "one": (["cluster_2"], (14, 8, 14), (0, 4, 0), 0x2b2f3a),
}


def build(preview=False):
    shapes = S.pieces()
    islands = [G.Island(name, poly, density=DENSITY) for name, polys in shapes.items() for poly in polys]
    height = G.pack(islands, WIDTH)
    colour, glow = G.paint(islands, height, WIDTH, cracks=G.Cracks(**CRACKS), **LINES)
    colour.save(TEXTURES / "frost_field.png", optimize=True)
    glow.save(TEXTURES / "frost_field_glow.png", optimize=True)
    for name in ("frost_field", "frost_field_glow"):  # filtered smooth, like the lance's
        shutil.copyfile(TEXTURES / "glacial_lance.png.mcmeta", TEXTURES / f"{name}.png.mcmeta")
    print(f"atlas {WIDTH}x{height}, {len(islands)} faces")
    bb = G.export("frost_field", shapes, islands, WIDTH, height, TEXTURES / "frost_field.png", OBJ_PATH, BBMODEL)
    if preview:
        render_previews(bb, shapes)


def render_previews(bb, shapes):
    import base64
    import io
    import json
    from PIL import Image
    bb.js(G.PREVIEW_JS)
    out = ROOT / "run" / "field_preview"
    out.mkdir(parents=True, exist_ok=True)
    for name, (show, eye, target, bg) in VIEWS.items():
        url = bb.js(f"kegareLance.shoot({json.dumps(show or list(shapes))}, {list(eye)}, {list(target)}, {bg})")
        Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / f"{name}.png")
    print("previews in", out.relative_to(ROOT))


if __name__ == "__main__":
    build(preview="--preview" in sys.argv)
