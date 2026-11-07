"""Rime Shell's plates: the mesh and textures, the same way as the lance (build_glacial_lance.py) and in the same style, driving a
running Blockbench (cdp.py).

    python3 -I tools/blockbench/build_rime_shell.py            build everything
    python3 -I tools/blockbench/build_rime_shell.py --preview  also render run/shell_preview/*.png from Blockbench, the plates
                                                               set on a stand-in body at each part's pivot

Writes
    art/blockbench/rime_shell.bbmodel                              the Blockbench project
    src/main/resources/assets/kegare/models/fx/rime_shell.obj      one object per plate, each in its body part's space (shell_shapes.py)
    src/main/resources/assets/kegare/textures/entity/rime_shell.png       the colour: navy in each face to pale frost at its ridges
    src/main/resources/assets/kegare/textures/entity/rime_shell_glow.png  what stays lit in the dark: ridges and cracks

Plates a few pixels across seen from a few blocks: about the frost field crystals' density, the lines a little wider so they read.
"""
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import build_glacial_lance as G  # noqa: E402
import shell_shapes as S  # noqa: E402

DENSITY = 340
WIDTH = 512
TEXTURES = G.TEXTURES
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/rime_shell.obj"
BBMODEL = ROOT / "art/blockbench/rime_shell.bbmodel"
CRACKS = dict(seed=37, across=(0.3, 0.2), along=(-0.8, 0.2), step=0.07, axis=1, jitter=0.03)
LINES = dict(ridge=0.0035, band_width=0.012, crack_width=0.0028, crack_glow=0.008)

#: Each part's pivot on the Blockbench rig (pixels), for the preview only: the game draws each plate in its part's own space.
PIVOTS = {"torso": (0, 24, 0), "right_arm": (5, 22, 0), "left_arm": (-5, 22, 0), "right_leg": (1.9, 12, 0), "left_leg": (-1.9, 12, 0)}
VIEWS = {
    # name: (camera, target, background); Blockbench pixels, the player facing -Z
    "front": ((14, 22, -52), (0, 15, 0), 0x2b2f3a),
    "back": ((-16, 24, 50), (0, 15, 0), 0x2b2f3a),
    "side": ((54, 18, -4), (0, 15, 0), 0x2b2f3a),
    "sky": ((22, 4, -44), (0, 18, 0), 0x9ebef0),
    "close": ((10, 26, -22), (0, 20, 0), 0x2b2f3a),
}

BODY_JS = """
(() => {
  const old = Project.model_3d.getObjectByName('kegareBody');
  if (old) Project.model_3d.remove(old);
  const body = new THREE.Group(); body.name = 'kegareBody';
  const skin = new THREE.MeshBasicMaterial({color: 0x9a7a62}), cloth = new THREE.MeshBasicMaterial({color: 0x56606e});
  const box = (x0, y0, z0, x1, y1, z1, m) => {
    const g = new THREE.Mesh(new THREE.BoxGeometry(x1 - x0, y1 - y0, z1 - z0), m);
    g.position.set((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2); body.add(g);
  };
  box(-4, 24, -4, 4, 32, 4, skin); box(-4, 12, -2, 4, 24, 2, cloth);
  box(4, 12, -2, 8, 24, 2, cloth); box(-8, 12, -2, -4, 24, 2, cloth);
  box(-0.1, 0, -2, 3.9, 12, 2, cloth); box(-3.9, 0, -2, 0.1, 12, 2, cloth);
  Project.model_3d.add(body);
  const pivots = %s;
  for (const m of Mesh.all) {
    const part = Object.keys(pivots).find(p => m.name.startsWith(p + '_'));
    if (part) m.mesh.position.set(...pivots[part]);
  }
  return true;
})()
"""


def build(preview=False):
    shapes = S.pieces()
    islands = [G.Island(name, poly, density=DENSITY) for name, polys in shapes.items() for poly in polys]
    height = G.pack(islands, WIDTH)
    colour, glow = G.paint(islands, height, WIDTH, cracks=G.Cracks(**CRACKS), **LINES)
    colour.save(TEXTURES / "rime_shell.png", optimize=True)
    glow.save(TEXTURES / "rime_shell_glow.png", optimize=True)
    for name in ("rime_shell", "rime_shell_glow"):  # filtered smooth, like the lance's
        shutil.copyfile(TEXTURES / "glacial_lance.png.mcmeta", TEXTURES / f"{name}.png.mcmeta")
    print(f"atlas {WIDTH}x{height}, {len(islands)} faces, {len(shapes)} plates")
    bb = G.export("rime_shell", shapes, islands, WIDTH, height, TEXTURES / "rime_shell.png", OBJ_PATH, BBMODEL)
    if preview:
        render_previews(bb, shapes)


def render_previews(bb, shapes):
    import base64
    import io
    import json
    from PIL import Image
    bb.js(G.PREVIEW_JS)
    bb.js(BODY_JS % json.dumps(PIVOTS))
    out = ROOT / "run" / "shell_preview"
    out.mkdir(parents=True, exist_ok=True)
    for name, (eye, target, bg) in VIEWS.items():
        url = bb.js(f"kegareLance.shoot({json.dumps(list(shapes))}, {list(eye)}, {list(target)}, {bg})")
        Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / f"{name}.png")
    print("previews in", out.relative_to(ROOT))


if __name__ == "__main__":
    build(preview="--preview" in sys.argv)
