"""Makes art/blockbench/fist_shell.bbmodel and its OBJ exports (models/fx/fist_shell_regular.obj, fist_shell_slim.obj), by driving a
running Blockbench (cdp.py).

The thin shell over a Severed player's hand and forearm while a Reinforced Strike is ready (it carries the strain), modeled on the right arm hanging straight down:
the hand ends at y=0, the elbow is at y=6, as in a player model where the arm is 12 tall. Flares out over the fist, thins out toward
the elbow, with chamfered edges so it reads as something wrapped round the arm and not a bigger arm. The game mirrors it for a left
main hand and fades it toward the elbow by height. Only made it the first time, like the Strings art: from here on the .bbmodel is
the source, so open it, edit it and export OBJ over the same names.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
from cdp import BB  # noqa: E402

JS = """
(() => {
  newProject(Formats.free);
  Project.texture_width = 32; Project.texture_height = 32;
  // Rings up the arm: [height, how far out past the skin]. The bottom is a cap under the knuckles.
  const RINGS = [[-0.55, 0.0], [-0.55, 0.42], [0.0, 0.62], [1.4, 0.72], [2.8, 0.55], [3.8, 0.34], [5.0, 0.26], [6.6, 0.2]];
  const build = (name, width) => {
    const mesh = new Mesh({name, vertices: {}, origin: [0, 0, 0]});
    const hx = width / 2, hz = 2;
    const rings = RINGS.map(([y, out]) => {
      const X = hx + out, Z = hz + out, c = Math.min(0.5, 0.25 + out * 0.5);
      // Eight corners, going round the arm: the chamfer.
      const corners = [[X - c, Z], [X, Z - c], [X, -Z + c], [X - c, -Z], [-X + c, -Z], [-X, -Z + c], [-X, Z - c], [-X + c, Z]];
      return corners.map(([x, z]) => mesh.addVertices([x, y, z])[0]);
    });
    // V runs up the arm, U round it (four times round the texture's width), so the streaks wrap and scroll along it.
    const vAt = (y) => (y + 0.55) * 2;
    for (let r = 0; r + 1 < rings.length; r++) {
      for (let i = 0; i < 8; i++) {
        const j = (i + 1) % 8;
        const a = rings[r][i], b = rings[r][j], c = rings[r + 1][j], d = rings[r + 1][i];
        const uv = {};
        const u0 = i * 4, u1 = (i + 1) * 4;
        uv[a] = [u0, vAt(RINGS[r][0])]; uv[b] = [u1, vAt(RINGS[r][0])];
        uv[c] = [u1, vAt(RINGS[r + 1][0])]; uv[d] = [u0, vAt(RINGS[r + 1][0])];
        mesh.addFaces(new MeshFace(mesh, {vertices: [a, b, c, d], uv}));
      }
    }
    // The cap over the knuckles.
    const cap = rings[0], capUv = {};
    cap.slice(0, 4).forEach((v, i) => { capUv[v] = [i * 4, 0]; });
    mesh.addFaces(new MeshFace(mesh, {vertices: [cap[0], cap[1], cap[2], cap[3]], uv: capUv}));
    const capUv2 = {};
    [cap[3], cap[4], cap[7], cap[0]].forEach((v, i) => { capUv2[v] = [i * 4, 2]; });
    mesh.addFaces(new MeshFace(mesh, {vertices: [cap[3], cap[4], cap[7], cap[0]], uv: capUv2}));
    const capUv3 = {};
    [cap[4], cap[5], cap[6], cap[7]].forEach((v, i) => { capUv3[v] = [i * 4, 0]; });
    mesh.addFaces(new MeshFace(mesh, {vertices: [cap[4], cap[5], cap[6], cap[7]], uv: capUv3}));
    return mesh;
  };
  const regular = build('fist_shell_regular', 4), slim = build('fist_shell_slim', 3);
  regular.init(); slim.init();
  Canvas.updateAll();
  const only = (keep) => { [regular, slim].forEach(m => { m.export = m === keep; m.visibility = m === keep; }); return Codecs.obj.compile(); };
  const objRegular = only(regular), objSlim = only(slim);
  [regular, slim].forEach(m => { m.export = true; m.visibility = true; });
  return {bbmodel: Codecs.project.compile({raw: false}), regular: objRegular, slim: objSlim};
})()
"""


def obj_text(value):
    return value if isinstance(value, str) else value["obj"]


def main():
    result = BB().js(JS)
    bb = result["bbmodel"]
    (ROOT / "art/blockbench/fist_shell.bbmodel").write_text(bb if isinstance(bb, str) else json.dumps(bb))
    for name in ("regular", "slim"):
        text = obj_text(result[name])
        (ROOT / f"src/main/resources/assets/kegare/models/fx/fist_shell_{name}.obj").write_text(text)
        print(name, sum(line.startswith("f ") for line in text.splitlines()), "faces")


if __name__ == "__main__":
    main()
