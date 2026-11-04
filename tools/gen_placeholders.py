#!/usr/bin/env python3
"""Generate Kegare's 16x16 placeholder textures.

Usage (from anywhere):  python3 tools/gen_placeholders.py
Requires Pillow (pip install pillow). Output goes into src/main/resources/assets/kegare/textures/.
Re-running overwrites the placeholders. Replace them with real art whenever it's ready.
"""
from __future__ import annotations

import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "kegare" / "textures"


def rgba(hex_color: str, alpha: int = 255) -> tuple[int, int, int, int]:
    hex_color = hex_color.lstrip("#")
    return int(hex_color[0:2], 16), int(hex_color[2:4], 16), int(hex_color[4:6], 16), alpha


def from_grid(grid: list[str], palette: dict[str, tuple[int, int, int, int]], noise: dict[str, int] | None = None,
              seed: int = 0) -> Image.Image:
    """Build a 16x16 image from 16 strings of 16 chars. '.' is transparent."""
    assert len(grid) == 16 and all(len(row) == 16 for row in grid), "grid must be 16x16"
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            r, g, b, a = palette[ch]
            if noise and ch in noise:
                d = rng.randint(-noise[ch], noise[ch])
                r, g, b = (max(0, min(255, c + d)) for c in (r, g, b))
            img.putpixel((x, y), (r, g, b, a))
    return img


def talisman() -> Image.Image:
    # An ofuda paper strip: ink strokes and a red seal.
    grid = [
        "................",
        "....KKKKKKKK....",
        "....KPPPPPPK....",
        "....KPIIIIPK....",
        "....KPPPPPPK....",
        "....KPPIIPPK....",
        "....KPRRRRPK....",
        "....KRRWWRRK....",
        "....KRRWWRRK....",
        "....KPRRRRPK....",
        "....KPPIIPPK....",
        "....KPIPPIPK....",
        "....KPPIIPPK....",
        "....KSPPPPSK....",
        "....KKKKKKKK....",
        "................",
    ]
    palette = {
        "K": rgba("#3B2F2F"),
        "P": rgba("#EDE3C8"),
        "S": rgba("#C9B88E"),
        "I": rgba("#1C1A1F"),
        "R": rgba("#B3261E"),
        "W": rgba("#E8705F"),
    }
    return from_grid(grid, palette, noise={"P": 8}, seed=7)


def malice_bar() -> Image.Image:
    """Rows 0-7: empty bar. Rows 8-15: full bar. Cols 0-1 / 14-15 are caps; 2-13 stretch."""
    border = rgba("#140B18")
    empty_rows = ["#3A2647", "#2A1A33", "#2A1A33", "#24162C", "#24162C", "#1F1226"]
    full_rows = ["#F1D2FF", "#D49BFF", "#B266F2", "#9D4EDD", "#8436C9", "#6A24A8"]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for block, rows in ((0, empty_rows), (8, full_rows)):
        for y in range(8):
            for x in range(16):
                corner = (x in (0, 15)) and (y in (0, 7))
                if corner:
                    continue  # rounded corners
                if y in (0, 7) or x in (0, 15):
                    img.putpixel((x, block + y), border)
                else:
                    img.putpixel((x, block + y), rgba(rows[y - 1]))
    return img


def ability_slot(selected: bool) -> Image.Image:
    border = rgba("#F2D27A") if selected else rgba("#2B2B30")
    light = rgba("#FFF1C2") if selected else rgba("#4A4A55")
    inner = (40, 30, 50, 200) if selected else (0, 0, 0, 140)
    img = Image.new("RGBA", (16, 16), inner)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, border)
    for i in range(1, 15):  # top-left bevel
        img.putpixel((i, 1), light)
        img.putpixel((1, i), light)
    return img


# ---------------------------------------------------------------- GUI sprites (textures/gui/sprites/)
# Nine-slice sprites get a .mcmeta so Minecraft stretches only their middle. Greyscale ones are tinted in code
# (cards and radial slots take the rarity / state color).


def frame(w: int, h: int, border: str, inner_top: str, inner_bottom: str, highlight: str | None = None,
          border_px: int = 1, inner_alpha: int = 255) -> Image.Image:
    """Rectangle with a 1px rounded border, a vertical gradient fill and an optional top-left bevel."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    top, bottom = rgba(inner_top, inner_alpha), rgba(inner_bottom, inner_alpha)
    for y in range(h):
        t = y / max(1, h - 1)
        row = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(4))
        for x in range(w):
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if corner:
                continue
            if x < border_px or y < border_px or x >= w - border_px or y >= h - border_px:
                img.putpixel((x, y), rgba(border))
            else:
                img.putpixel((x, y), row)
    if highlight:
        for i in range(border_px, w - border_px):
            img.putpixel((i, border_px), rgba(highlight))
        for i in range(border_px, h - border_px):
            img.putpixel((border_px, i), rgba(highlight))
    return img


def card() -> Image.Image:
    # Greyscale: tinted by rarity color in code. Bright border, darker body, inner line.
    img = frame(24, 32, "#FFFFFF", "#6A6A6A", "#2E2E2E", border_px=2)
    for x in range(3, 21):
        img.putpixel((x, 3), rgba("#B0B0B0"))
    return img


def panel() -> Image.Image:
    return frame(16, 16, "#2A1838", "#1B1024", "#0E0814", highlight="#3E2752", border_px=1, inner_alpha=240)


def tab(selected: bool) -> Image.Image:
    if selected:
        return frame(16, 16, "#B07CE0", "#4A2C66", "#2C1A3C", highlight="#D9B3FF")
    return frame(16, 16, "#3A2650", "#22152E", "#170E20", highlight="#2E1E40")


def radial_slot(selected: bool) -> Image.Image:
    if selected:
        return frame(24, 24, "#F2D27A", "#4A3A18", "#2A200C", highlight="#FFF1C2", border_px=2, inner_alpha=230)
    return frame(24, 24, "#5A4A6A", "#24182E", "#140C1A", highlight="#3A2A48", border_px=2, inner_alpha=220)


def sparkle() -> Image.Image:
    # A small four-point star, white so it can be tinted.
    grid = [
        "...W...",
        "...W...",
        "..WWW..",
        "WWWWWWW",
        "..WWW..",
        "...W...",
        "...W...",
    ]
    img = Image.new("RGBA", (7, 7), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == "W":
                img.putpixel((x, y), (255, 255, 255, 255 if (x == 3 or y == 3) else 200))
    return img


def severed_emblem() -> Image.Image:
    # A broken ring: Malice that never flowed.
    import math
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            dx, dy = x - 15.5, y - 15.5
            d = math.hypot(dx, dy)
            angle = math.degrees(math.atan2(dy, dx)) % 360
            in_gap = 30 <= angle <= 60 or 210 <= angle <= 240
            if 10.5 <= d <= 14.5 and not in_gap:
                shade = 200 - int((d - 10.5) * 20)
                img.putpixel((x, y), (shade, shade, shade + 10, 255))
            elif d < 3.5:
                img.putpixel((x, y), (110, 30, 30, 255))
    return img


def nine_slice_meta(width: int, height: int, border: int) -> str:
    return ('{\n  "gui": {\n    "scaling": {\n      "type": "nine_slice",\n'
            f'      "width": {width},\n      "height": {height},\n      "border": {border}\n'
            '    }\n  }\n}\n')


def save_sprite(img: Image.Image, name: str, nine_slice_border: int | None = None) -> None:
    save(img, f"gui/sprites/{name}.png")
    meta = TEXTURES / "gui" / "sprites" / f"{name}.png.mcmeta"
    if nine_slice_border is not None:
        meta.write_text(nine_slice_meta(img.width, img.height, nine_slice_border))
        print(f"wrote {meta.relative_to(ROOT)}")
    elif meta.exists():
        meta.unlink()


def save(img: Image.Image, relative: str) -> None:
    path = TEXTURES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"wrote {path.relative_to(ROOT)}")


def main() -> None:
    save(talisman(), "item/purification_talisman.png")
    save(malice_bar(), "gui/malice_bar.png")
    save(ability_slot(False), "gui/ability_slot.png")
    save(ability_slot(True), "gui/ability_slot_selected.png")
    # GUI sprites
    save_sprite(panel(), "panel", nine_slice_border=4)
    save_sprite(card(), "card", nine_slice_border=6)
    save_sprite(tab(False), "tab", nine_slice_border=3)
    save_sprite(tab(True), "tab_selected", nine_slice_border=3)
    save_sprite(radial_slot(False), "radial_slot", nine_slice_border=4)
    save_sprite(radial_slot(True), "radial_slot_selected", nine_slice_border=4)
    save_sprite(sparkle(), "sparkle")
    save_sprite(severed_emblem(), "severed_emblem")


if __name__ == "__main__":
    main()
