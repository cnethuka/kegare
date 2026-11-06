"""Makes art/blockbench/parry_arc.bbmodel and its OBJ export (models/fx/parry_arc.obj), by driving a running Blockbench (cdp.py).

A crescent facing +X, thick in the middle and tapering to nothing, for the perfect parry's spark. Only made it the first
time, like the Strings art: from here on the .bbmodel is the source, so open it, edit it and export OBJ over the same name.
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
  Project.texture_width = 16; Project.texture_height = 16;
  const mesh = new Mesh({name: 'parry_arc', vertices: {}, origin: [0, 0, 0]});
  // Drawn as it's seen from the front, in the Y-Z plane, opening downward; the middle leans out along +X (the way the effect faces).
  // UVs run across the thickness only, so the soft glow texture gives a bright spine and soft edges along the whole length.
  const N = 20, R = 16, THICK = 4.2, SPREAD = 85 * Math.PI / 180, BULGE = 3.2;
  const outer = [], inner = [];
  for (let i = 0; i <= N; i++) {
    const t = i / N, a = -SPREAD + 2 * SPREAD * t, th = THICK * Math.pow(Math.sin(Math.PI * t), 0.8) + 0.05;
    const x = BULGE * Math.sin(Math.PI * t);
    outer.push(mesh.addVertices([x, Math.cos(a) * R - 6, Math.sin(a) * R])[0]);
    inner.push(mesh.addVertices([x, Math.cos(a) * (R - th) - 6, Math.sin(a) * (R - th)])[0]);
  }
  for (let i = 0; i < N; i++) {
    const uv = {};
    uv[outer[i]] = [8, 0]; uv[outer[i + 1]] = [8, 0]; uv[inner[i + 1]] = [8, 16]; uv[inner[i]] = [8, 16];
    mesh.addFaces(new MeshFace(mesh, {vertices: [outer[i], outer[i + 1], inner[i + 1], inner[i]], uv}));
  }
  mesh.init();
  Canvas.updateAll();
  return {bbmodel: Codecs.project.compile({raw: false}), obj: Codecs.obj.compile()};
})()
"""


def main():
    result = BB().js(JS)
    (ROOT / "art/blockbench/parry_arc.bbmodel").write_text(result["bbmodel"] if isinstance(result["bbmodel"], str) else json.dumps(result["bbmodel"]))
    obj = result["obj"] if isinstance(result["obj"], str) else result["obj"]["obj"]
    (ROOT / "src/main/resources/assets/kegare/models/fx/parry_arc.obj").write_text(obj)
    print("wrote", len(obj), "bytes of OBJ")


if __name__ == "__main__":
    main()
