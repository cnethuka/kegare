"""Builds Strings' cord segment and knot IN Blockbench (running, see README.md) and writes:

    art/blockbench/string_segment.bbmodel, string_knot.bbmodel       (the sources; open and edit these by hand)
    src/main/resources/assets/kegare/models/string/segment.json, knot.json   (Blockbench's own Java block export)

Only for making them from nothing: once you've edited a .bbmodel by hand, re-export it from Blockbench instead (see
DESIGN.md §3.20) or this overwrites your work. Run: python3 -I tools/blockbench/build_string_models.py
"""
import base64
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
from cdp import BB  # noqa: E402

TEXTURE = ROOT / "src/main/resources/assets/kegare/textures/item/string_braid.png"
ART = ROOT / "art/blockbench"
MODELS = ROOT / "src/main/resources/assets/kegare/models/string"

SETUP = r"""
window.kegareModel = {
  async start(name, dataUrl) {
    newProject(Formats.java_block);
    Project.name = name;
    Project.java_block_version = '1.9.0'; // "1.9 - 1.21.5": the game is on 1.21.1
    const tex = new Texture({name: 'string_braid.png', folder: 'item', namespace: 'kegare', particle: true});
    tex.fromDataURL(dataUrl);
    tex.add(false);
    await new Promise(r => setTimeout(r, 300));
    tex.namespace = 'kegare';
    tex.folder = 'item';
    tex.name = 'string_braid.png';
    this.tex = tex;
  },
  // uvs: {north:[u0,v0,u1,v1], ...}; a face left out isn't drawn or exported.
  cube(name, from, to, origin, rotation, uvs) {
    const faces = {};
    for (const f of ['north', 'east', 'south', 'west', 'up', 'down']) {
      faces[f] = uvs[f] ? {uv: uvs[f], texture: this.tex.uuid} : {uv: [0, 0, 0, 0], texture: null};
    }
    const c = new Cube({name, from, to, origin, rotation, autouv: 0, faces}).init();
    return c.uuid;
  },
  finish() {
    Canvas.updateAll();
    return {
      bbmodel: Codecs.project.compile({raw: false}),
      java: Codecs.java_block.compile({raw: false}),
      cubes: Cube.all.length,
      textureLink: this.tex.javaTextureLink(),
    };
  },
  shot(target, yaw, pitch, dist) {
    const W = 640, H = 480;
    if (!this.renderer) {
      this.renderer = new THREE.WebGLRenderer({antialias: true, preserveDrawingBuffer: true});
      this.renderer.setSize(W, H);
      this.renderer.setClearColor(0x14121c, 1);
      this.camera = new THREE.PerspectiveCamera(28, W / H, 0.1, 1000);
    }
    const y = yaw * Math.PI / 180, p = pitch * Math.PI / 180;
    const t = new THREE.Vector3(target[0] - 8, target[1], target[2] - 8);
    this.camera.position.set(t.x + Math.sin(y) * Math.cos(p) * dist, t.y + Math.sin(p) * dist, t.z + Math.cos(y) * Math.cos(p) * dist);
    this.camera.lookAt(t);
    const hidden = [];
    for (const child of scene.children) {
      if (child === Project.model_3d || child.isLight) continue;
      if (child.visible) { hidden.push(child); child.visible = false; }
    }
    this.renderer.render(scene, this.camera);
    hidden.forEach(c => c.visible = true);
    return this.renderer.domElement.toDataURL('image/png');
  },
};
true
"""

# The sides of a bar running along Z (end faces are inside the cord or the knot: not drawn). Each side gets its own stretch of the
# braid, so the crossings wind round the cord instead of lining up in stripes.
def sides(u0, u1, v):
    return {"east": [u0, v, u1, v + 4], "west": [u0, v + 1.5, u1, v + 5.5], "up": [u0, v + 3, u1, v + 7], "down": [u0, v + 0.75, u1, v + 4.75]}


def segment(bb):
    """Half a block of cord, along +Z from z=0 to z=8, centred on x=y=8: four short lengths, each turned 22.5 degrees further
    than the last. A square section looks the same every 90 degrees, so the fifth length (the next segment's first) continues
    the twist exactly and segments tile with no visible joint."""
    half = 0.7
    for i, angle in enumerate([0, 22.5, 45, -22.5]):
        z0, z1 = 2 * i, 2 * i + 2
        bb.js("kegareModel.cube(%s, %s, %s, %s, %s, %s)" % (json.dumps(f"strand_{i}"), [8 - half, 8 - half, z0 - 0.05], [8 + half, 8 + half, z1 + 0.05],
                                                            [8, 8, z0 + 1], [0, 0, angle], json.dumps(sides(4 * i, 4 * i + 4, 0))))


def knot(bb):
    """Where a string grips: a tight wrap at the origin (8,8,8) where the cord arrives from -Z, and a hoop past it, toward whatever
    it grips: an octagon in the YZ plane, made of a square of four bars and the same square turned 45 degrees."""
    wrap_uv = {"east": [0, 8, 6, 11], "west": [0, 8, 6, 11], "up": [0, 8, 6, 11], "down": [0, 8, 6, 11], "north": [0, 8, 3, 11], "south": [0, 8, 3, 11]}
    bb.js("kegareModel.cube('wrap_a', [6.7,6.7,6.8], [9.3,9.3,9.2], [8,8,8], [0,0,0], %s)" % json.dumps(wrap_uv))
    bb.js("kegareModel.cube('wrap_b', [6.9,6.9,6.2], [9.1,9.1,9.8], [8,8,8], [0,0,45], %s)" % json.dumps(wrap_uv))
    centre = [8, 8, 11.6]
    radius, side, t = 2.9, 2.4, 0.45
    bar_uv = {"east": [8, 8, 12, 10], "west": [8, 8, 12, 10], "up": [8, 8, 12, 10], "down": [8, 8, 12, 10], "north": [8, 8, 10, 10], "south": [8, 8, 10, 10]}
    for angle in [0, 45]:
        cy, cz = centre[1], centre[2]
        bars = [
            ("top", [8 - t, cy + radius - t, cz - side / 2], [8 + t, cy + radius + t, cz + side / 2]),
            ("bottom", [8 - t, cy - radius - t, cz - side / 2], [8 + t, cy - radius + t, cz + side / 2]),
            ("near", [8 - t, cy - side / 2, cz - radius - t], [8 + t, cy + side / 2, cz - radius + t]),
            ("far", [8 - t, cy - side / 2, cz + radius - t], [8 + t, cy + side / 2, cz + radius + t]),
        ]
        for name, frm, to in bars:
            bb.js("kegareModel.cube(%s, %s, %s, %s, %s, %s)" % (json.dumps(f"hoop_{name}_{angle}"), frm, to, centre, [angle, 0, 0], json.dumps(bar_uv)))


def build(bb, name, make, out_json):
    url = "data:image/png;base64," + base64.b64encode(TEXTURE.read_bytes()).decode()
    bb.js("kegareModel.start(%s, %s)" % (json.dumps(name), json.dumps(url)))
    make(bb)
    done = bb.js("kegareModel.finish()")
    (ART / f"{name}.bbmodel").write_text(done["bbmodel"])
    out_json.write_text(done["java"] if done["java"].endswith("\n") else done["java"] + "\n")
    print(name, done["cubes"], "cubes; texture", done["textureLink"])


def main():
    ART.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    bb = BB()
    bb.js(SETUP)
    build(bb, "string_segment", segment, MODELS / "segment.json")
    build(bb, "string_knot", knot, MODELS / "knot.json")


if __name__ == "__main__":
    main()
