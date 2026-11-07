"""Glacial Lance's mesh and texture, by driving a running Blockbench (cdp.py).

    python3 -I tools/blockbench/build_glacial_lance.py            build everything
    python3 -I tools/blockbench/build_glacial_lance.py --preview  also render run/lance_preview/*.png from Blockbench

Writes
    art/blockbench/glacial_lance.bbmodel                      the Blockbench project (open it to look the lance over)
    src/main/resources/assets/kegare/models/fx/glacial_lance.obj      one object per chunk and per shard (lance_shapes.py)
    src/main/resources/assets/kegare/textures/entity/glacial_lance.png       the colour, painted face by face
    src/main/resources/assets/kegare/textures/entity/glacial_lance_glow.png  what stays lit in the dark: ridges and cracks
    src/main/resources/assets/kegare/textures/entity/glacial_lance_frost.png the frost mark it leaves, 8 frames from whole to melted
    src/main/resources/assets/kegare/textures/fx/glacial_lance_trail.png     its trail's line: even along it, a hard core across it

The Python is the source: lance_shapes.py makes the crystals, and this lays every face out flat in a texture atlas at a fixed
density and paints it there, knowing that face's real edges. So each plane is a clean gradient, navy in the middle to pale frost
at its ridges, with no grid to see; and the cracks come from one field through the whole lance, so they run on across faces.
Every texel is opaque. Run it again after changing a shape: the atlas and both textures follow.
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

    def __init__(self, seed=77):
        import random
        rng = random.Random(seed)
        self.seeds = []
        z = -1.15
        while z < 1.95:
            for _ in range(2):
                self.seeds.append(((rng.uniform(-0.32, 0.32), rng.uniform(-0.28, 0.28), z + rng.uniform(-0.06, 0.06)), rng.randrange(3)))
            z += 0.13

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
    def __init__(self, piece, polygon):
        self.piece = piece
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
        self.w = math.ceil(max(x for x, _ in self.flat) * DENSITY) + 2 * PAD
        self.h = math.ceil(max(y for _, y in self.flat) * DENSITY) + 2 * PAD
        self.x0 = self.y0 = 0

    def texel(self, flat):
        return (self.x0 + PAD + flat[0] * DENSITY, self.y0 + PAD + flat[1] * DENSITY)

    def uvs(self):
        return [self.texel(f) for f in self.flat]

    def point(self, tx, ty):
        """The 3D point under a texel's centre, and where that is on the flat face (blocks)."""
        fx = (tx + 0.5 - self.x0 - PAD) / DENSITY
        fy = (ty + 0.5 - self.y0 - PAD) / DENSITY
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


def pack(islands):
    islands = sorted(islands, key=lambda i: -i.h)
    x = y = row = 0
    for isl in islands:
        if x + isl.w > ATLAS_WIDTH:
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

def paint(islands, height):
    colour = Image.new("RGBA", (ATLAS_WIDTH, height), FROST + (255,))
    glow = Image.new("RGBA", (ATLAS_WIDTH, height), (0, 0, 0, 0))
    cpx, gpx = colour.load(), glow.load()
    cracks = Cracks()
    for isl in islands:
        inner = isl.inradius()
        band = min(BAND, max(0.01, inner * 0.5))
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

MARK_FRAME = 512
FROST_MID = (150, 196, 245)


def frost_mark(seed=5):
    """Frost spiking out from where the lance came down: long jagged spikes with side branches round a broken core, two tones (a
    mid-ice body with a frost-white heart) and hard edges. Eight frames for the sheet, 4 x 2: frame 0 whole, then melting from the
    tips in, so the game can spread it in a few ticks and melt it away later just by showing frames, never a soft fade."""
    import random
    from PIL import ImageDraw
    rng = random.Random(seed)
    size, c, r = MARK_FRAME, MARK_FRAME / 2, MARK_FRAME * 0.48
    body = Image.new("L", (size, size), 0)
    heart = Image.new("L", (size, size), 0)
    db, dh = ImageDraw.Draw(body), ImageDraw.Draw(heart)
    melt = {}

    def spike(angle, start, length, width, inner):
        ux, uy = math.cos(angle), math.sin(angle)
        px, py = -uy, ux
        bx, by = c + ux * start, c + uy * start
        for draw, k in ((db, 1.0), (dh, inner)):
            w = width * k
            draw.polygon([(bx + px * w, by + py * w), (bx + ux * length * (0.55 + 0.4 * k), by + uy * length * (0.55 + 0.4 * k)),
                          (bx - px * w, by - py * w)], fill=255)

    # Uneven on purpose: a few long spikes where the force went, many short ones, no two gaps alike, so it reads as ice that burst
    # outward, not as a snowflake.
    spikes = 13
    angles = sorted(2 * math.pi * (i + rng.uniform(-0.42, 0.42)) / spikes for i in range(spikes))
    for i, a in enumerate(angles):
        length = r * (0.32 + 0.68 * rng.random() ** 1.6)
        width = r * rng.uniform(0.03, 0.065) * (0.6 + 0.6 * length / r)
        bend = rng.uniform(-0.07, 0.07)
        spike(a + bend, r * 0.05, length, width, 0.42)
        for _ in range(rng.choice((0, 1, 1, 2, 3))):
            along = rng.uniform(0.25, 0.7) * length
            side = rng.choice((-1, 1))
            ba = a + side * math.radians(rng.uniform(28, 68))
            sx, sy = math.cos(a) * along, math.sin(a) * along
            ux, uy = math.cos(ba), math.sin(ba)
            bl = length * rng.uniform(0.14, 0.34)
            bw = width * rng.uniform(0.4, 0.6)
            for draw, k in ((db, 1.0), (dh, 0.4)):
                draw.polygon([(c + sx - uy * bw * k, c + sy + ux * bw * k), (c + sx + ux * bl * (0.6 + 0.4 * k), c + sy + uy * bl * (0.6 + 0.4 * k)),
                              (c + sx + uy * bw * k, c + sy - ux * bw * k)], fill=255)
    # Hairline splinters between the spikes, and chips of frost thrown clear of the core.
    for _ in range(18):
        a = rng.uniform(0, 2 * math.pi)
        spike(a, r * 0.1, r * rng.uniform(0.15, 0.4), r * rng.uniform(0.012, 0.02), 0.0)
    for _ in range(14):
        a, d = rng.uniform(0, 2 * math.pi), r * rng.uniform(0.3, 0.8)
        px, py, s = c + math.cos(a) * d, c + math.sin(a) * d, r * rng.uniform(0.012, 0.03)
        turn = rng.uniform(0, math.pi)
        corners = [turn + k * 2.1 + rng.uniform(-0.3, 0.3) for k in range(3)]
        db.polygon([(px + math.cos(q) * s, py + math.sin(q) * s) for q in corners], fill=255)
    core = [(c + math.cos(2 * math.pi * k / 11) * r * rng.uniform(0.1, 0.2), c + math.sin(2 * math.pi * k / 11) * r * rng.uniform(0.1, 0.2))
            for k in range(11)]
    db.polygon(core, fill=255)
    dh.polygon([(c + (x - c) * 0.55, c + (y - c) * 0.55) for x, y in core], fill=255)

    bp, hp = body.load(), heart.load()
    wobble = [rng.uniform(-0.08, 0.08) for _ in range(64)]
    frames = Image.new("RGBA", (MARK_FRAME * 4, MARK_FRAME * 2), (0, 0, 0, 0))
    fp = frames.load()
    for y in range(size):
        for x in range(size):
            if not bp[x, y]:
                continue
            d = math.hypot(x - c, y - c) / r
            a = (math.atan2(y - c, x - c) / (2 * math.pi)) % 1.0
            m = 1.0 - d + wobble[int(a * 64) % 64]  # how long it lasts: the tips go first, the core last
            colour = (FROST if hp[x, y] else FROST_MID) + (255,)
            for f in range(8):
                if m >= f / 8 * 0.92:
                    fp[(f % 4) * size + x, (f // 4) * size + y] = colour
    return frames


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
  Project.name = 'glacial_lance';
  Project.texture_width = data.width; Project.texture_height = data.height;
  const tex = new Texture({name: 'glacial_lance.png'}).fromPath(data.texture).add(false);
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
    from cdp import BB
    shapes = L.pieces()
    islands = [Island(name, poly) for name, polys in shapes.items() for poly in polys]
    height = pack(islands)
    colour, glow = paint(islands, height)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    colour.save(TEXTURES / "glacial_lance.png", optimize=True)
    glow.save(TEXTURES / "glacial_lance_glow.png", optimize=True)
    frost_mark().save(TEXTURES / "glacial_lance_frost.png", optimize=True)
    (TEXTURES.parent / "fx").mkdir(exist_ok=True)
    trail_line().save(TEXTURES.parent / "fx" / "glacial_lance_trail.png", optimize=True)
    print(f"atlas {ATLAS_WIDTH}x{height}, {len(islands)} faces")

    pieces = []
    for name in shapes:
        tris = []
        for isl in (i for i in islands if i.piece == name):
            uvs = isl.uvs()
            corners = list(zip(isl.polygon, uvs))
            for i in range(1, len(corners) - 1):
                tri = (corners[0], corners[i], corners[i + 1])
                tris.append([{"p": [round(c[0][0] * 16, 5), round(c[0][1] * 16, 5), round(c[0][2] * 16, 5)],
                              "uv": [round(c[1][0], 4), round(c[1][1], 4)]} for c in tri])
        pieces.append({"name": name, "triangles": tris})
    data = {"width": ATLAS_WIDTH, "height": height, "texture": str(TEXTURES / "glacial_lance.png"), "pieces": pieces}
    bb = BB()
    result = bb.js(BUILD_JS % json.dumps(data))
    bbmodel = result["bbmodel"] if isinstance(result["bbmodel"], str) else json.dumps(result["bbmodel"])
    BBMODEL.parent.mkdir(parents=True, exist_ok=True)
    BBMODEL.write_text(bbmodel)
    obj = result["obj"] if isinstance(result["obj"], str) else result["obj"]["obj"]
    OBJ_PATH.write_text(obj)
    print("wrote", BBMODEL.relative_to(ROOT), "and", OBJ_PATH.relative_to(ROOT), len(obj), "bytes")
    check_winding(shapes)
    if preview:
        render_previews(bb)


def check_winding(shapes):
    """The OBJ's triangles must face out (the game culls back faces): compare each with the face it came from."""
    verts, faces, current = [], {}, None
    for line in OBJ_PATH.read_text().splitlines():
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
    for polys in shapes.values():
        for poly in polys:
            for tri in L.triangles(poly):
                outward[key(tri)] = L.polygon_normal(list(tri))
    flipped = unmatched = 0
    for tris in faces.values():
        for t in tris:
            want = outward.get(key(t))
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
