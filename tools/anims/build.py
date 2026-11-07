"""Builds Kegare's player animations, and (with --preview) renders them on the Blockbench rig for review.

    python3 tools/anims/build.py                      write src/main/resources/assets/kegare/player_animations/*.json
    python3 tools/anims/build.py --preview [names...] also render contact sheets and GIFs to run/anim_preview/
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import kit  # noqa: E402
import glacial_lance  # noqa: E402
import glacial_spire  # noqa: E402
import frost_shard  # noqa: E402
import splinter  # noqa: E402
import frost_field  # noqa: E402
import melee  # noqa: E402
import puppet_manipulation  # noqa: E402

SETS = {"puppet_manipulation": puppet_manipulation.ANIMS, "melee": melee.ANIMS, "glacial_lance": glacial_lance.ANIMS,
        "glacial_spire": glacial_spire.ANIMS, "frost_shard": frost_shard.ANIMS, "splinter": splinter.ANIMS,
        "frost_field": frost_field.ANIMS}
# What a set's preview holds: (bone, OBJ, texture, objects, grip). The game draws the same thing from the same numbers.
HELD = {"glacial_lance": ("right_arm", "src/main/resources/assets/kegare/models/fx/glacial_lance.obj",
                          "src/main/resources/assets/kegare/textures/entity/glacial_lance.png",
                          ("grip", "fore", "aft", "head", "tail", "spurs"), glacial_lance.LANCE_GRIP)}
VIEWS = ("three_quarter", "side", "front")


def main(argv):
    stale = glacial_lance.grip_for(glacial_lance.WOUND, glacial_lance.GRIP_TILT)
    if tuple(glacial_lance.LANCE_GRIP["rotation"]) != stale:
        raise SystemExit(f"glacial_lance.LANCE_GRIP's rotation is stale for the wound pose: make it {stale} (and LanceHold.java)")
    for stem, anims in SETS.items():
        path = kit.write(stem, anims)
        print(f"wrote {path.relative_to(kit.ROOT)} ({len(anims)} animations)")
    if "--preview" in argv:
        from view import Viewer
        only = [a for a in argv if not a.startswith("--")]
        out = kit.ROOT / "run" / "anim_preview"
        out.mkdir(parents=True, exist_ok=True)
        v = Viewer()
        for stem, anims in SETS.items():
            held = HELD.get(stem)
            if held:
                bone, obj, texture, objects, grip = held
                v.attach("held", bone, kit.ROOT / obj, kit.ROOT / texture, objects, grip["position"], grip["rotation"], grip["scale"])
            for a in anims:
                if only and a.name not in only:
                    continue
                pv = a.to_preview()
                n = 9
                times = [round(a.length * i / (n - 1), 3) for i in range(n)]
                v.sheet(a.name, pv, times, VIEWS, out=out / f"{a.name}.png", scale=0.42, title=f"{a.name}: {a.note}")
                v.gif(a.name, pv, out / f"{a.name}.gif")
                print("previewed", a.name)
            if held:
                v.detach("held", held[0])


if __name__ == "__main__":
    main(sys.argv[1:])
