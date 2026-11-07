"""Renders animations on the Blockbench rig (tools/blockbench) for review: contact sheets and GIFs.

Needs Blockbench running with remote debugging on 127.0.0.1:9222 and the rig loaded (see tools/blockbench/README.md).
"""
from __future__ import annotations

import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
from cdp import BB  # noqa: E402

PREVIEW_JS = ROOT / "tools/blockbench/preview.js"


def _img(url):
    return Image.open(io.BytesIO(base64.b64decode(url.split(",", 1)[1]))).convert("RGB")


class Viewer:
    def __init__(self):
        self.bb = BB()
        if not self.bb.js("!!window.kegarePreview"):
            self.bb.js(PREVIEW_JS.read_text())

    def load(self, name: str, pal_anim: dict):
        """Loads one PAL animation (Bedrock format) into Blockbench."""
        doc = {"format_version": "1.8.0", "animations": {name: pal_anim}}
        return self.bb.js(f"kegarePreview.load({json.dumps(doc)}, {json.dumps(name)})")

    def attach(self, name, bone, obj_path, texture_path, objects, position, rotation, scale):
        """Hangs the named objects of an OBJ (blocks) off a bone, textured, at position/rotation/scale (see preview.js)."""
        positions, uvs = [], []
        verts, tex, current = [], [], None
        for line in Path(obj_path).read_text().splitlines():
            parts = line.split()
            if not parts:
                continue
            if parts[0] == "o":
                current = parts[1]
            elif parts[0] == "v":
                verts.append([float(x) for x in parts[1:4]])
            elif parts[0] == "vt":
                tex.append([float(x) for x in parts[1:3]])
            elif parts[0] == "f" and current in objects:
                refs = [p.split("/") for p in parts[1:]]
                for i in range(1, len(refs) - 1):
                    for r in (refs[0], refs[i], refs[i + 1]):
                        positions += verts[int(r[0]) - 1]
                        u, w = tex[int(r[1]) - 1]
                        uvs += [u, 1 - w]
        url = "data:image/png;base64," + base64.b64encode(Path(texture_path).read_bytes()).decode()
        spec = {"name": name, "bone": bone, "positions": positions, "uvs": uvs, "texture": url,
                "position": list(position), "rotation": list(rotation), "scale": scale}
        return self.bb.js(f"kegarePreview.attach({json.dumps(spec)})")

    def detach(self, name, bone):
        return self.bb.js(f"kegarePreview.attach({json.dumps({'name': name, 'bone': bone})})")

    def frames(self, name, times, views):
        rows = self.bb.js(f"kegarePreview.frames({json.dumps(name)}, {json.dumps(list(times))}, {json.dumps(list(views))})")
        return [[_img(u) for u in row] for row in rows]

    def sheet(self, name, pal_anim, times, views=("three_quarter", "side"), out=None, scale=0.5, title=None):
        self.load(name, pal_anim)
        grid = self.frames(name, times, views)
        w, h = int(grid[0][0].width * scale), int(grid[0][0].height * scale)
        pad, head = 4, 30
        sheet = Image.new("RGB", (len(times) * (w + pad) + pad, len(views) * (h + pad) + pad + head), (16, 16, 20))
        d = ImageDraw.Draw(sheet)
        d.text((pad + 2, 2), title or name, fill=(255, 255, 255))
        for ci, t in enumerate(times):
            d.text((pad + ci * (w + pad) + 4, 16), f"{t:.2f}s", fill=(190, 190, 200))
            for ri in range(len(views)):
                sheet.paste(grid[ci][ri].resize((w, h), Image.LANCZOS), (pad + ci * (w + pad), head + pad + ri * (h + pad)))
        if out:
            sheet.save(out)
        return sheet

    def gif(self, name, pal_anim, out, view="three_quarter", fps=20, scale=0.6, hold=0.4):
        """An animated GIF at game speed (20 fps), holding the last frame a moment."""
        self.load(name, pal_anim)
        n = max(1, round(pal_anim["animation_length"] * fps))
        times = [i / fps for i in range(n + 1)]
        frames = [row[0] for row in self.frames(name, times, [view])]
        frames = [f.resize((int(f.width * scale), int(f.height * scale)), Image.LANCZOS) for f in frames]
        frames += [frames[-1]] * int(hold * fps)
        frames[0].save(out, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0)
        return out
