"""Freeze's frost on a body: pixel art in the player skin's own layout (64x64, its texels), so frost on a player is drawn the way
frost on a block is, a texel at a time, and the body looks frozen itself.

    python3 -I tools/blockbench/build_freeze_frost.py            # write them
    python3 -I tools/blockbench/build_freeze_frost.py --preview  # also run/freeze_preview/*.png: the unwrapped art, 8x, by coverage

Writes src/main/resources/assets/kegare/textures/entity/freeze_frost.png and freeze_frost_order.png (wide arms), and
freeze_frost_slim*.png (slim arms, three texels wide). No .mcmeta: they stay nearest-filtered, like the skin under them.

Only the skin's outer layer is painted (hat, jacket, sleeves, trouser legs): it sits over the rest, so a jacket can't hide the
frost. The order says when each texel frosts over as the frost creeps (1 first, 255 last; 0 is off the outer layer): up from the soles and the hands, the
legs to the hips and the arms to the shoulders, then up the body, the head last; ragged, never a ruled line. FreezeClient shows
the texels up to how far the frost has got (the stacks out of the most), with a rim of rime along the creeping edge, and glazes
the whole body in ice while it's frozen solid.

Tones are block marks' palette (build_block_marks.py): a glaze of blue ice over the skin (thicker in patches), frost in clumps
through it, rime feathering up each face from below, and rime and glints on the box edges, where frost gathers.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/kegare/textures/entity"
PREVIEW = ROOT / "run" / "freeze_preview"
sys.path.insert(0, str(ROOT / "tools/blockbench"))
from build_block_marks import DEEP, FROST, GLINT, ICE, NONE, RIME  # noqa: E402

# (name, box UV origin, size (w, h, d) in texels, where it starts creeping (0..1), how far it creeps over its height)
# The outer layer's boxes in the skin (the Java edition's 64x64 layout).
def parts(slim):
    arm = 3 if slim else 4
    return [
        ("right_leg", (0, 32), (4, 12, 4), 0.00, 0.45),
        ("left_leg", (0, 48), (4, 12, 4), 0.00, 0.45),
        ("right_arm", (40, 32), (arm, 12, 4), 0.05, 0.45),
        ("left_arm", (48, 48), (arm, 12, 4), 0.05, 0.45),
        ("body", (16, 32), (8, 12, 4), 0.45, 0.40),
        ("head", (32, 0), (8, 8, 8), 0.85, 0.15),
    ]


def faces(origin, size):
    """Every texel of a box's six faces: (x, y, face, column across it, row down it (0 the top), its width, its height)."""
    u, v = origin
    w, h, d = size
    out = []
    rects = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, (x0, y0, fw, fh) in rects.items():
        for j in range(fh):
            for i in range(fw):
                out.append((x0 + i, y0 + j, face, i, j, fw, fh))
    return out


def side_faces(origin, size):
    """The four side faces' rects (x, y, width, height): where frost feathers up from the bottom."""
    u, v = origin
    w, h, d = size
    return [(u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]


def smooth_noise(rng, size=64, cell=3):
    grid = [[rng.random() for _ in range(size // cell + 2)] for _ in range(size // cell + 2)]

    def at(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx), int(gy)
        fx, fy = gx - x0, gy - y0
        a = grid[y0][x0] * (1 - fx) + grid[y0][x0 + 1] * fx
        b = grid[y0 + 1][x0] * (1 - fx) + grid[y0 + 1][x0 + 1] * fx
        return a * (1 - fy) + b * fy
    return at


def build(slim, seed=71):
    rng = random.Random(seed)
    noise = smooth_noise(rng)
    clumps = smooth_noise(rng, cell=2)
    art = Image.new("RGBA", (64, 64), NONE)
    when = Image.new("L", (64, 64), 0)
    for name, origin, size, start, span in parts(slim):
        w, h, d = size
        for x, y, face, i, j, fw, fh in faces(origin, size):
            # How far up the part this texel is, 0 at its bottom (sole, hand, waist, chin) to 1 at its top.
            if face == "top":
                up = 1.0
            elif face == "bottom":
                up = 0.0
            else:
                up = (fh - 1 - j + 0.5) / fh
            t = start + span * up + (noise(x, y) - 0.5) * 0.14
            when.putpixel((x, y), max(1, min(255, 1 + round(t * 254))))
            # The tone: a glaze of blue ice over the body, thicker in patches, frost in clumps through it, rime on the box's
            # edges and corners where frost gathers, and the odd glint. Clumps, never a checker.
            edge = i == 0 or i == fw - 1 or j == 0 or j == fh - 1
            c = clumps(x, y)
            r = rng.random()
            if edge and r < 0.3:
                tone = GLINT if r < 0.04 else RIME
            elif c > 0.7:
                tone = RIME if r < 0.7 else GLINT if r < 0.78 else FROST
            elif c > 0.58:
                tone = FROST
            elif c < 0.25:
                tone = DEEP
            else:
                tone = ICE
            art.putpixel((x, y), tone)
        for x0, y0, fw, fh in side_faces(origin, size):
            for _ in range(max(1, fw // 2)):
                i, j = rng.randrange(fw), fh - 1
                for _ in range(rng.randint(2, 4)):
                    art.putpixel((x0 + i, y0 + j), RIME)
                    j -= 1
                    i = min(fw - 1, max(0, i + rng.choice((-1, 0, 1))))
                    if j < 0:
                        break
    return art, when


def preview(name, art, when):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    skin = Image.new("RGBA", (64, 64), (120, 96, 80, 255))
    panels = []
    for cover in (0.2, 0.4, 0.6, 0.8, 1.0):
        layer = Image.new("RGBA", (64, 64), NONE)
        for y in range(64):
            for x in range(64):
                if art.getpixel((x, y))[3] and (when.getpixel((x, y)) - 1) / 254 <= cover:
                    layer.putpixel((x, y), art.getpixel((x, y)))
        panels.append(Image.alpha_composite(skin, layer).resize((512, 512), Image.NEAREST))
    sheet = Image.new("RGB", (len(panels) * 520, 512), (24, 24, 28))
    for k, p in enumerate(panels):
        sheet.paste(p.convert("RGB"), (k * 520, 0))
    sheet.save(PREVIEW / f"{name}.png")
    print("preview", (PREVIEW / f"{name}.png").relative_to(ROOT))


def main(args):
    for name, slim in (("freeze_frost", False), ("freeze_frost_slim", True)):
        art, when = build(slim)
        art.save(OUT / f"{name}.png", optimize=True)
        when.save(OUT / f"{name}_order.png", optimize=True)
        print("wrote", (OUT / f"{name}.png").relative_to(ROOT))
        if "--preview" in args:
            preview(name, art, when)


if __name__ == "__main__":
    main(sys.argv[1:])
