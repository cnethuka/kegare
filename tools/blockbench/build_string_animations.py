"""Fills a Blockbench project (running, see README.md) with Strings' animations and writes what it saves and exports:

    art/blockbench/string_animations.bbmodel    the source: a plain mannequin rig plus the animations; edit this by hand
    src/main/resources/assets/kegare/player_animations/puppet_strings.json     Blockbench's own "Save All Animations" output

The rig has PAL's bone names and pivots, on plain cubes with no skin (nothing from the Emotecraft template that
build_rig.py downloads for the other previews). Only for making them from nothing: once you've edited the .bbmodel by hand,
export from Blockbench instead (DESIGN.md §3.20) or this overwrites your work.
Run: python3 -I tools/blockbench/build_string_animations.py [--preview]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/blockbench"))
sys.path.insert(0, str(ROOT / "tools/anims"))
from cdp import BB  # noqa: E402
import kit  # noqa: E402
import puppet_strings  # noqa: E402

RIG = r"""
(() => {
  newProject(Formats.bedrock);
  Project.name = 'string_animations';
  Project.geometry_name = 'string_animations';
  const mk = (name, origin, parent, cubes, color) => {
    const g = new Group({name, origin}).init();
    if (parent) g.addTo(parent);
    for (const [cname, from, to] of cubes) new Cube({name: cname, from, to, color, box_uv: false}).addTo(g).init();
    return g;
  };
  const body = mk('body', [0, 12, 0], null, [], 0);
  mk('torso', [0, 24, 0], body, [['torso', [-4, 12, -2], [4, 24, 2]]], 5);
  mk('head', [0, 24, 0], body, [['head', [-4, 24, -4], [4, 32, 4]], ['nose', [-1, 27, -5], [1, 29, -4]]], 4);
  mk('right_arm', [5, 22, 0], body, [['right_arm', [4, 12, -2], [8, 24, 2]]], 2);
  mk('left_arm', [-5, 22, 0], body, [['left_arm', [-8, 12, -2], [-4, 24, 2]]], 6);
  mk('right_leg', [2, 12, 0], body, [['right_leg', [0, 0, -2], [4, 12, 2]]], 7);
  mk('left_leg', [-2, 12, 0], body, [['left_leg', [-4, 0, -2], [0, 12, 2]]], 7);
  Canvas.updateAll();
  return Group.all.length + ' bones, ' + Cube.all.length + ' cubes';
})()
"""


def main(argv):
    bb = BB()
    print(bb.js(RIG))
    doc = {"format_version": "1.8.0", "animations": {}}
    for a in puppet_strings.ANIMS:
        bad = a.check()
        if bad:
            raise ValueError(f"{a.name}: {bad}")
        doc["animations"][a.name] = a.to_pal()
    print(bb.js("""(() => {
        Modes.options.animate.select();
        Animator.loadFile({path: 'puppet_strings.animation.json', content: %s});
        return Animation.all.map(a => a.name + ' ' + a.length + 's');
    })()""" % json.dumps(json.dumps(doc))))
    # What Blockbench's Animation > Save All Animations writes.
    exported = bb.js("JSON.stringify(Animator.buildFile(), null, 1)")
    out = kit.OUT_DIR / "puppet_strings.json"
    out.write_text(exported + "\n")
    bbmodel = bb.js("Codecs.project.compile({raw: false})")
    art = ROOT / "art/blockbench"
    art.mkdir(parents=True, exist_ok=True)
    (art / "string_animations.bbmodel").write_text(bbmodel)
    print("wrote", out.relative_to(ROOT), "and art/blockbench/string_animations.bbmodel")
    if "--preview" in argv:
        from view import Viewer
        v = Viewer()
        prev = ROOT / "run/anim_preview"
        prev.mkdir(parents=True, exist_ok=True)
        views = ("three_quarter", "side", "front")
        for a in puppet_strings.ANIMS:
            n = 9
            times = [round(a.length * i / (n - 1), 3) for i in range(n)]
            grid = v.frames(a.name, times, views)
            from PIL import Image, ImageDraw
            scale = 0.42
            w, h = int(grid[0][0].width * scale), int(grid[0][0].height * scale)
            pad, head = 4, 30
            sheet = Image.new("RGB", (n * (w + pad) + pad, len(views) * (h + pad) + pad + head), (16, 16, 20))
            d = ImageDraw.Draw(sheet)
            d.text((pad + 2, 2), f"{a.name}: {a.note}", fill=(255, 255, 255))
            for ci, t in enumerate(times):
                d.text((pad + ci * (w + pad) + 4, 16), f"{t:.2f}s", fill=(190, 190, 200))
                for ri in range(len(views)):
                    sheet.paste(grid[ci][ri].resize((w, h), Image.LANCZOS), (pad + ci * (w + pad), head + pad + ri * (h + pad)))
            sheet.save(prev / f"{a.name}.png")
            print("previewed", a.name)


if __name__ == "__main__":
    main(sys.argv[1:])
