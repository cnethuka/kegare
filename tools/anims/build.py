"""Builds Kegare's player animations, and (with --preview) renders them on the Blockbench rig for review.

    python3 tools/anims/build.py                      write src/main/resources/assets/kegare/player_animations/*.json
    python3 tools/anims/build.py --preview [names...] also render contact sheets and GIFs to run/anim_preview/
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import kit  # noqa: E402
import puppet_manipulation  # noqa: E402

SETS = {"puppet_manipulation": puppet_manipulation.ANIMS}
VIEWS = ("three_quarter", "side", "front")


def main(argv):
    for stem, anims in SETS.items():
        path = kit.write(stem, anims)
        print(f"wrote {path.relative_to(kit.ROOT)} ({len(anims)} animations)")
    if "--preview" in argv:
        from view import Viewer
        only = [a for a in argv if not a.startswith("--")]
        out = kit.ROOT / "run" / "anim_preview"
        out.mkdir(parents=True, exist_ok=True)
        v = Viewer()
        for anims in SETS.values():
            for a in anims:
                if only and a.name not in only:
                    continue
                pv = a.to_preview()
                n = 9
                times = [round(a.length * i / (n - 1), 3) for i in range(n)]
                v.sheet(a.name, pv, times, VIEWS, out=out / f"{a.name}.png", scale=0.42, title=f"{a.name}: {a.note}")
                v.gif(a.name, pv, out / f"{a.name}.gif")
                print("previewed", a.name)


if __name__ == "__main__":
    main(sys.argv[1:])
