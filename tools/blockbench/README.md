# Blockbench tooling (Puppet Manipulation's player animations)

Animations are written pose-to-pose in `tools/anims/<technique>.py` and exported to
`src/main/resources/assets/kegare/player_animations/<technique>.json` in Blockbench's own Bedrock format, which Player
Animation Library (PAL) reads natively. Blockbench is the viewer: every pose is rendered on a rig there before it ships.

    python3 tools/anims/build.py                    # write the JSON
    python3 tools/anims/build.py --preview [names]  # also render run/anim_preview/<name>.png and .gif

The Python files are the source of truth. Don't hand-edit the JSON: the next build overwrites it.

## Files

- `build_rig.py`: the preview rig, with PAL's bone names and pivots.
- `cdp.py`: a tiny Chrome DevTools client that drives Blockbench.
- `preview.js`: injected into Blockbench; renders the rig from fixed cameras.

## Blockbench and the game disagree?

Trust the game. What was checked in game with PAL 1.1.6 is written at the top of `tools/anims/kit.py`: which channels
replace the vanilla pose, and why there's a baked `hip` instead of a custom bone and no `bend` channel.
