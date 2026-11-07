# Blockbench tooling (player animations, Strings, Glacial Lance)

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

## The fist shell

`art/blockbench/fist_shell.bbmodel` carries a Severed player's strained forearm (DESIGN.md §3.22); a ready fist's Malice is sprites now, not
this shell. `build_fist_shell.py` writes the project and both
OBJs (`models/fx/fist_shell_regular.obj`, `fist_shell_slim.obj`): regular and slim differ only in width. Hand at y=0, faded out by y=6.6 px.

## Glacial Lance: the Python is the source

The Rime rework's style test (DESIGN.md §3.14). `lance_shapes.py` makes the chunks and shards as clusters of convex crystals
(flat planes, sharp ridges), and `build_glacial_lance.py` lays every face out in a texture atlas and paints it there, sends
the meshes to the running Blockbench and writes `art/blockbench/glacial_lance.bbmodel`, the OBJ and the textures (paths at
the top of the script). Blockbench is the viewer here, not the editor: it can't paint a gradient that knows a face's real
edges, so an edit made in the .bbmodel is lost on the next run.

    python3 -I tools/blockbench/build_glacial_lance.py            # everything
    python3 -I tools/blockbench/build_glacial_lance.py --preview  # also run/lance_preview/*.png from Blockbench

Its body animations are in `tools/anims/glacial_lance.py`; `build.py --preview` draws the lance in the hand by
`LANCE_GRIP`, the numbers `LanceHold` uses in game (`GlacialLanceArtTest` checks they match). Blockbench's preview lights
faces its own way: check the colours in game.

## Files

- `build_rig.py`: the preview rig, with PAL's bone names and pivots.
- `cdp.py`: a tiny Chrome DevTools client that drives Blockbench.
- `preview.js`: injected into Blockbench; renders the rig from fixed cameras.

## Blockbench and the game disagree?

Trust the game. What was checked in game with PAL 1.1.6 is written at the top of `tools/anims/kit.py`: which channels
replace the vanilla pose, and why there's a baked `hip` instead of a custom bone and no `bend` channel.
