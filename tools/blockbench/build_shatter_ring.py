"""Shatter Shell's shock ring: its texture, a smooth one (part of the technique, not a mark on the blocks).

    python3 -I tools/blockbench/build_shatter_ring.py

Writes src/main/resources/assets/kegare/textures/fx/shatter_ring.png (+ .mcmeta, filtered smooth). ShatterClient draws the ring
with it twice: flat on the ground (the top row across: a soft trail of frost behind a bright front) and as a low wall at the
front (the column at the front, down: bright at the ground, gone at the top).
"""
import math
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/kegare/textures/fx/shatter_ring.png"
#: Where the bright front sits across the texture (0 the inside edge, 1 the outside): ShatterClient.FRONT.
FRONT = 0.82


def across(u):
    """How solid the ring is across its width: a trail rising behind the front, the front, then quickly nothing."""
    if u <= FRONT:
        trail = (u / FRONT) ** 1.6
        return 0.4 * trail + 0.6 * math.exp(-((u - FRONT) / 0.06) ** 2)
    return math.exp(-((u - FRONT) / 0.07) ** 2)


def colour(u):
    """Pale ice blue in the trail to white at the front."""
    k = math.exp(-((u - FRONT) / 0.1) ** 2)
    trail, front = (150, 214, 255), (244, 251, 255)
    return tuple(round(t + (f - t) * k) for t, f in zip(trail, front))


def main(width=64, height=32):
    im = Image.new("RGBA", (width, height))
    for y in range(height):
        up = (1 - y / (height - 1)) ** 1.6
        for x in range(width):
            u = (x + 0.5) / width
            r, g, b = colour(u)
            im.putpixel((x, y), (r, g, b, round(255 * min(1.0, across(u)) * up)))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    im.save(OUT, optimize=True)
    shutil.copyfile(ROOT / "src/main/resources/assets/kegare/textures/entity/glacial_lance.png.mcmeta", OUT.with_name(OUT.name + ".mcmeta"))
    print("wrote", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
