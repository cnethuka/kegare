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


def ce_bar() -> Image.Image:
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


def save(img: Image.Image, relative: str) -> None:
    path = TEXTURES / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"wrote {path.relative_to(ROOT)}")


def main() -> None:
    save(talisman(), "item/purification_talisman.png")
    save(ce_bar(), "gui/ce_bar.png")
    save(ability_slot(False), "gui/ability_slot.png")
    save(ability_slot(True), "gui/ability_slot_selected.png")


if __name__ == "__main__":
    main()
