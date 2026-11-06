# Blockbench tooling (Puppet Manipulation's player animations)

Animations are written pose-to-pose in `tools/anims/<technique>.py` and exported to
`src/main/resources/assets/kegare/player_animations/<technique>.json` in Blockbench's own Bedrock format, which Player
Animation Library (PAL) reads natively. Blockbench is the viewer: every pose is rendered on a rig there before it ships.

    python3 tools/anims/build.py                    # write the JSON
    python3 tools/anims/build.py --preview [names]  # also render run/anim_preview/<name>.png and .gif

The Python files are the source of truth. Don't hand-edit the JSON: the next build overwrites it.

## Strings' art is different: the .bbmodel files are the source

Strings' cord, knot and five animations live in `art/blockbench/` as ordinary Blockbench projects. Open them, edit them, and
export over the same file names (paths in DESIGN.md §3.20). `build_string_models.py` and `build_string_animations.py` only
made them the first time, by driving a running Blockbench through `cdp.py`; run them again and they overwrite your work.
`string_animations.bbmodel` has its own plain mannequin rig (PAL's bone names and pivots, no skin), so nothing of the
Emotecraft template is in the repo.

## Files

- `build_rig.py`: the preview rig, with PAL's bone names and pivots.
- `cdp.py`: a tiny Chrome DevTools client that drives Blockbench.
- `preview.js`: injected into Blockbench; renders the rig from fixed cameras.

## Blockbench and the game disagree?

Trust the game. What was checked in game with PAL 1.1.6 is written at the top of `tools/anims/kit.py`: which channels
replace the vanilla pose, and why there's a baked `hip` instead of a custom bone and no `bend` channel.
