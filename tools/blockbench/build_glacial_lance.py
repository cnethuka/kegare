"""Glacial Lance's mesh and texture, by driving a running Blockbench (cdp.py).

    python3 -I tools/blockbench/build_glacial_lance.py            build everything
    python3 -I tools/blockbench/build_glacial_lance.py --preview  also render run/lance_preview/*.png from Blockbench

Writes
    art/blockbench/glacial_lance.bbmodel                      the Blockbench project (open it to look the lance over)
    src/main/resources/assets/kegare/models/fx/glacial_lance.obj      one object per chunk and per shard (lance_shapes.py)
    src/main/resources/assets/kegare/textures/entity/glacial_lance.png       the colour, painted face by face
    src/main/resources/assets/kegare/textures/entity/glacial_lance_glow.png  what stays lit in the dark: ridges and cracks
    src/main/resources/assets/kegare/textures/fx/glacial_lance_trail.png     its trail's line: even along it, a hard core across it

The Python is the source: lance_shapes.py makes the crystals, and this lays every face out flat in a texture atlas at a fixed
density and paints it there, knowing that face's real edges. So each plane is a clean gradient, navy in the middle to pale frost
at its ridges, with no grid to see; and the cracks come from one field through the whole lance, so they run on across faces.
Every texel is opaque. Run it again after changing a shape: the atlas and both textures follow. The frost it leaves on the blocks
is pixel art at the blocks' own resolution, so it's made with the other block marks, in build_block_marks.py.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
import lance_shapes as L  # noqa: E402

DENSITY = 352  # texels per block: about a screen pixel each two blocks away, and the atlas still fits 1024 x 1024
PAD = 4
ATLAS_WIDTH = 1024
TEXTURES = ROOT / "src/main/resources/assets/kegare/textures/entity"
OBJ_PATH = ROOT / "src/main/resources/assets/kegare/models/fx/glacial_lance.obj"
BBMODEL = ROOT / "art/blockbench/glacial_lance.bbmodel"

# Colours, from a ridge in to the middle of a face.
FROST = (236, 246, 255)
PALE = (172, 208, 248)
ICE = (88, 138, 220)
RIME = (34, 64, 152)
NAVY = (22, 40, 104)
DEEP = (13, 24, 70)
CRACK = (118, 184, 255)

RIDGE = 0.0055  # blocks of frost-white along every edge: the razor line, lit in the dark too
BAND = 0.03  # how far in the frost fades to navy
CRACK_WIDTH = 0.0055
CRACK_GLOW = 0.016
CRACK_MIX = 0.55  # faint: they show the navy's depth, they don't light it up


def lerp(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def ramp(stops, t):
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return lerp(c0, c1, smooth((t - t0) / (t1 - t0)))
    return stops[-1][1]


EDGE_RAMP = ((0.0, FROST), (0.12, PALE), (0.45, ICE), (0.78, RIME), (1.0, NAVY))


# ---------------------------------------------------------------------------------------------- cracks

class Cracks:
    """Borders between regions of a Voronoi field, only where the two nearest seeds belong to different groups: sparse, straight,
    angular lines through the whole volume, so a crack crossing a ridge carries on along the next face."""

    def __init__(self, seed=77, across=(0.32, 0.28), along=(-1.15, 1.95), step=0.13, axis=2, jitter=0.06):
        """Seeds two to a slice, `step` apart along `axis` from along[0] to along[1], within +-across on the other two axes (the
        defaults are the lance's: along Z)."""
        import random
        rng = random.Random(seed)
        self.seeds = []
        z = along[0]
        others = [a for a in range(3) if a != axis]
        while z < along[1]:
            for _ in range(2):
                p = [0.0, 0.0, 0.0]
                p[others[0]] = rng.uniform(-across[0], across[0])
                p[others[1]] = rng.uniform(-across[1], across[1])
                p[axis] = z + rng.uniform(-jitter, jitter)
                self.seeds.append((tuple(p), rng.randrange(3)))
            z += step

    def distance(self, p):
        """Distance to the nearest crack, and whether there's one near enough to matter."""
        best = []
        for s, g in self.seeds:
            d = (p[0] - s[0]) ** 2 + (p[1] - s[1]) ** 2 + (p[2] - s[2]) ** 2
            best.append((d, s, g))
        best.sort(key=lambda b: b[0])
        d1, s1, g1 = best[0]
        out = 1.0
        for d2, s2, g2 in best[1:4]:
            if g2 == g1:
                continue
            gap = math.sqrt((s2[0] - s1[0]) ** 2 + (s2[1] - s1[1]) ** 2 + (s2[2] - s1[2]) ** 2)
            out = min(out, (d2 - d1) / (2 * gap))
        return out


# ---------------------------------------------------------------------------------------------- atlas

class Island:
    def __init__(self, piece, polygon, density=DENSITY):
        self.piece = piece
        self.density = density
        self.polygon = polygon
        self.normal = L.polygon_normal(polygon)
        # Lay the face flat along its longest edge.
        edges = [(polygon[i], polygon[(i + 1) % len(polygon)]) for i in range(len(polygon))]
        a, b = max(edges, key=lambda e: L.length(L.sub(e[1], e[0])))
        self.u = L.norm(L.sub(b, a))
        self.v = L.cross(self.normal, self.u)
        self.origin = polygon[0]
        flat = [(L.dot(L.sub(p, self.origin), self.u), L.dot(L.sub(p, self.origin), self.v)) for p in polygon]
        self.min_x = min(x for x, _ in flat)
        self.min_y = min(y for _, y in flat)
        self.flat = [(x - self.min_x, y - self.min_y) for x, y in flat]
        self.w = math.ceil(max(x for x, _ in self.flat) * density) + 2 * PAD
        self.h = math.ceil(max(y for _, y in self.flat) * density) + 2 * PAD
        self.x0 = self.y0 = 0

    def texel(self, flat):
        return (self.x0 + PAD + flat[0] * self.density, self.y0 + PAD + flat[1] * self.density)

    def uvs(self):
        return [self.texel(f) for f in self.flat]

    def point(self, tx, ty):
        """The 3D point under a texel's centre, and where that is on the flat face (blocks)."""
        fx = (tx + 0.5 - self.x0 - PAD) / self.density
        fy = (ty + 0.5 - self.y0 - PAD) / self.density
        lx, ly = fx + self.min_x, fy + self.min_y
        return L.add(self.origin, L.add(L.mul(self.u, lx), L.mul(self.v, ly))), (fx, fy)

    def edge_distance(self, f):
        """Distance in to the nearest edge (negative outside). Wound counter-clockwise, so inside is on each edge's left."""
        best = 1e9
        n = len(self.flat)
        for i in range(n):
            ax, ay = self.flat[i]
            bx, by = self.flat[(i + 1) % n]
            ex, ey = bx - ax, by - ay
            el = math.hypot(ex, ey)
            d = ((f[0] - ax) * -ey + (f[1] - ay) * ex) / el
            best = min(best, d)
        return best

    def inradius(self):
        best = 0.0
        for ty in range(self.y0, self.y0 + self.h, 2):
            for tx in range(self.x0, self.x0 + self.w, 2):
                best = max(best, self.edge_distance(self.point(tx, ty)[1]))
        return best


def pack(islands, width=ATLAS_WIDTH):
    islands = sorted(islands, key=lambda i: -i.h)
    x = y = row = 0
    for isl in islands:
        if x + isl.w > width:
            x, y, row = 0, y + row, 0
        isl.x0, isl.y0 = x, y
        x += isl.w
        row = max(row, isl.h)
    height = y + row
    size = 64
    while size < height:
        size *= 2
    return size


# ---------------------------------------------------------------------------------------------- paint

def paint(islands, height, width=ATLAS_WIDTH, cracks=None, ridge=RIDGE, band_width=BAND, crack_width=CRACK_WIDTH,
          crack_glow=CRACK_GLOW):
    """The colour (opaque) and the glow (cut out) for every face. The defaults are the lance's; a bigger piece of ice wants wider
    lines (in blocks) so they read at the distance it's seen from."""
    RIDGE, CRACK_WIDTH, CRACK_GLOW = ridge, crack_width, crack_glow  # noqa: N806 (the lance's names, read below)
    colour = Image.new("RGBA", (width, height), FROST + (255,))
    glow = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    cpx, gpx = colour.load(), glow.load()
    cracks = cracks or Cracks()
    for isl in islands:
        inner = isl.inradius()
        band = min(band_width, max(0.01, inner * 0.5))
        shard = isl.piece.startswith("shard")
        for ty in range(isl.y0, isl.y0 + isl.h):
            for tx in range(isl.x0, isl.x0 + isl.w):
                p, f = isl.point(tx, ty)
                d = isl.edge_distance(f)
                if d < RIDGE:
                    # The razor line, and the padding round the face (so a sample just over its edge is still the ridge).
                    cpx[tx, ty] = FROST + (255,)
                    gpx[tx, ty] = FROST + (255,)
                    continue
                if d < band:
                    c = ramp(EDGE_RAMP, (d - RIDGE) / (band - RIDGE))
                else:
                    c = lerp(NAVY, DEEP, smooth((d - band) / max(1e-4, inner - band)) * 0.85)
                lit = False
                if not shard and d > 2 * RIDGE:
                    cd = cracks.distance(p)
                    if cd < CRACK_WIDTH:
                        c = lerp(c, CRACK, CRACK_MIX)
                        lit = True
                    elif cd < CRACK_GLOW:
                        c = lerp(c, ICE, 0.22 * (1 - (cd - CRACK_WIDTH) / (CRACK_GLOW - CRACK_WIDTH)) ** 2)
                rgb = tuple(int(round(x)) for x in c)
                cpx[tx, ty] = rgb + (255,)
                if lit:
                    gpx[tx, ty] = rgb + (255,)
    return colour, glow


# ---------------------------------------------------------------------------------------------- the frost mark

def trail_line(width=64, height=16):
    """The trail's cross-section, the same all along it (its taper and fade come from the effect): a solid white core a quarter of
    the way across, falling off quickly to nothing at the sides."""
    im = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = im.load()
    for y in range(height):
        d = abs(y + 0.5 - height / 2) / (height / 2)
        a = 1.0 if d < 0.25 else max(0.0, 1 - (d - 0.25) / 0.75) ** 2.2
        for x in range(width):
            px[x, y] = (255, 255, 255, int(round(255 * a)))
    return im


# ---------------------------------------------------------------------------------------------- Blockbench

BUILD_JS = """
(async () => {
  const data = %s;
  newProject(Formats.free);
  Project.name = data.name;
  Project.texture_width = data.width; Project.texture_height = data.height;
  const tex = new Texture({name: data.name + '.png'}).fromPath(data.texture).add(false);
  tex.uv_width = data.width; tex.uv_height = data.height;
  for (const piece of data.pieces) {
    const mesh = new Mesh({name: piece.name, vertices: {}, origin: [0, 0, 0]});
    for (const tri of piece.triangles) {
      const keys = mesh.addVertices(...tri.map(c => c.p));
      const uv = {};
      keys.forEach((k, i) => uv[k] = tri[i].uv);
      mesh.addFaces(new MeshFace(mesh, {vertices: keys, uv, texture: tex.uuid}));
    }
    mesh.init();
  }
  Canvas.updateAll();
  return {bbmodel: Codecs.project.compile({raw: false}), obj: Codecs.obj.compile()};
})()
"""

PREVIEW_JS = """
(() => {
  const W = 900, H = 520;
  const renderer = new THREE.WebGLRenderer({antialias: true, preserveDrawingBuffer: true});
  renderer.setSize(W, H);
  renderer.outputColorSpace = THREE.SRGBColorSpace || renderer.outputColorSpace;
  const camera = new THREE.PerspectiveCamera(30, W / H, 0.5, 4000);
  function shoot(show, eye, target, bg) {
    renderer.setClearColor(bg, 1);
    for (const m of Mesh.all) m.mesh.visible = show.includes(m.name);
    const hidden = [];
    for (const child of scene.children) {
      if (child === Project.model_3d || child.isLight) continue;
      if (child.visible) { hidden.push(child); child.visible = false; }
    }
    camera.position.set(...eye); camera.lookAt(new THREE.Vector3(...target));
    renderer.render(scene, camera);
    hidden.forEach(c => c.visible = true);
    for (const m of Mesh.all) m.mesh.visible = true;
    return renderer.domElement.toDataURL('image/png');
  }
  window.kegareLance = {shoot};
  return true;
})()
"""


def build(preview=False):
    shapes = L.pieces()
    islands = [Island(name, poly) for name, polys in shapes.items() for poly in polys]
    height = pack(islands)
    colour, glow = paint(islands, height)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    colour.save(TEXTURES / "glacial_lance.png", optimize=True)
    glow.save(TEXTURES / "glacial_lance_glow.png", optimize=True)
    (TEXTURES.parent / "fx").mkdir(exist_ok=True)
    trail_line().save(TEXTURES.parent / "fx" / "glacial_lance_trail.png", optimize=True)
    print(f"atlas {ATLAS_WIDTH}x{height}, {len(islands)} faces")

    bb = export("glacial_lance", shapes, islands, ATLAS_WIDTH, height, TEXTURES / "glacial_lance.png", OBJ_PATH, BBMODEL)
    if preview:
        render_previews(bb)


def export(name, shapes, islands, width, height, texture, obj_path, bbmodel_path):
    """Sends the meshes to the running Blockbench (one mesh per piece, UVs into the atlas) and writes back what it makes of them:
    the .bbmodel and the OBJ. Returns the Blockbench connection."""
    from cdp import BB
    pieces = []
    for piece in shapes:
        tris = []
        for isl in (i for i in islands if i.piece == piece):
            uvs = isl.uvs()
            corners = list(zip(isl.polygon, uvs))
            for i in range(1, len(corners) - 1):
                tri = (corners[0], corners[i], corners[i + 1])
                tris.append([{"p": [round(c[0][0] * 16, 5), round(c[0][1] * 16, 5), round(c[0][2] * 16, 5)],
                              "uv": [round(c[1][0], 4), round(c[1][1], 4)]} for c in tri])
        pieces.append({"name": piece, "triangles": tris})
    data = {"name": name, "width": width, "height": height, "texture": str(texture), "pieces": pieces}
    bb = BB()
    result = bb.js(BUILD_JS % json.dumps(data))
    bbmodel = result["bbmodel"] if isinstance(result["bbmodel"], str) else json.dumps(result["bbmodel"])
    bbmodel_path.parent.mkdir(parents=True, exist_ok=True)
    bbmodel_path.write_text(bbmodel)
    obj = result["obj"] if isinstance(result["obj"], str) else result["obj"]["obj"]
    obj_path.write_text(obj)
    print("wrote", bbmodel_path.relative_to(ROOT), "and", obj_path.relative_to(ROOT), len(obj), "bytes")
    check_winding(shapes, obj_path)
    return bb


def check_winding(shapes, obj_path=OBJ_PATH):
    """The OBJ's triangles must face out (the game culls back faces): compare each with the face it came from."""
    verts, faces, current = [], {}, None
    for line in obj_path.read_text().splitlines():
        parts = line.split()
        if not parts:
            continue
        if parts[0] == "o":
            current = parts[1]
        elif parts[0] == "v":
            verts.append(tuple(float(x) for x in parts[1:4]))
        elif parts[0] == "f":
            faces.setdefault(current, []).append([verts[int(p.split("/")[0]) - 1] for p in parts[1:]])
    def key(tri):
        return tuple(sorted(tuple(round(c, 3) for c in p) for p in tri))

    outward = {}
    polygons = []
    for polys in shapes.values():
        for poly in polys:
            polygons.append((poly, L.polygon_normal(list(poly))))
            for tri in L.triangles(poly):
                outward[key(tri)] = L.polygon_normal(list(tri))

    def on(p, poly):
        return any(max(abs(p[i] - q[i]) for i in range(3)) < 2e-3 for q in poly)

    flipped = unmatched = 0
    for tris in faces.values():
        for t in tris:
            want = outward.get(key(t))
            if want is None:
                # Blockbench may cut a flat many-sided face along other diagonals: find the face all three corners are on.
                want = next((n for poly, n in polygons if all(on(p, poly) for p in t)), None)
            if want is None:
                unmatched += 1
            elif L.dot(L.cross(L.sub(t[1], t[0]), L.sub(t[2], t[0])), want) < 0:
                flipped += 1
    print(f"winding: {flipped} inward, {unmatched} unmatched | objects:", {k: len(v) for k, v in faces.items()})
    if flipped or unmatched:
        raise SystemExit("the OBJ's faces don't match the shapes")


VIEWS = {
    # name: (pieces shown, camera, target, background); Blockbench pixels, the lance along +Z
    "side": (None, (70, 6, 6), (0, 0, 6), 0x2b2f3a),
    "three_quarter": (None, (46, 30, -30), (0, 0, 6), 0x2b2f3a),
    "top": (None, (4, 72, 8), (0, 0, 6), 0x2b2f3a),
    "head": (None, (20, 12, 38), (0, 0, 22), 0x2b2f3a),
    "sky": (None, (46, 30, -30), (0, 0, 6), 0x9ebef0),
    "stub": (["grip"], (46, 30, -30), (0, 0, 6), 0x2b2f3a),
    "javelin": (["grip", "fore", "aft"], (46, 30, -30), (0, 0, 6), 0x2b2f3a),
}


def render_previews(bb):
    import base64
    import io
    bb.js(PREVIEW_JS)
    out = ROOT / "run" / "lance_preview"
    out.mkdir(parents=True, exist_ok=True)
    lance = [n for n, _ in L.STAGES]
    for name, (show, eye, target, bg) in VIEWS.items():
        url = bb.js(f"kegareLance.shoot({json.dumps(show or lance)}, {list(eye)}, {list(target)}, {bg})")
        Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / f"{name}.png")
    shards = [f"shard_{i}" for i in range(L.SHARDS)]
    url = bb.js(f"kegareLance.shoot({json.dumps(shards)}, [10, 6, 10], [0, 0, 0], 0x2b2f3a)")
    Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).save(out / "shards.png")
    print("previews in", out.relative_to(ROOT))


if __name__ == "__main__":
    build(preview="--preview" in sys.argv)
