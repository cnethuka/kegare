# Blockbench tooling (player animations, Strings, Glacial Lance, Glacial Spire, Frost Shard, block marks, Anchor's token)

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

Glacial Spire's spike is made the same way: `spire_shapes.py` makes its crystal groups and chunks, and
`build_glacial_spire.py` paints them with the lance's painter at a coarser density (`art/blockbench/glacial_spire.bbmodel`,
`models/fx/glacial_spire.obj`, `textures/entity/glacial_spire*.png`). Its body animations are in `tools/anims/glacial_spire.py`.
Before an animation preview after building a mesh, load the rig again (Setting up): the viewer draws whatever project is open.

    python3 -I tools/blockbench/build_glacial_spire.py --preview  # also run/spire_preview/*.png

Frost Shard's and Splinter's thrown crystals, and the chips they break into, too: `shard_shapes.py` and
`build_frost_shard.py`, at a finer density (`art/blockbench/frost_shard.bbmodel`, `models/fx/frost_shard.obj`,
`textures/entity/frost_shard*.png`). The casts are `tools/anims/frost_shard.py` and `tools/anims/splinter.py`.

    python3 -I tools/blockbench/build_frost_shard.py --preview  # also run/shard_preview/*.png

Frost Field's crystals: `field_shapes.py` (four small clusters) and `build_frost_field.py`, the lance's painter
(`art/blockbench/frost_field.bbmodel`, `models/fx/frost_field.obj`, `textures/entity/frost_field*.png`). The channel's pose is
`tools/anims/frost_field.py`; the frost on the ground is pixel art (`field_frost`, below).

    python3 -I tools/blockbench/build_frost_field.py --preview  # also run/field_preview/*.png

Ice Path has no mesh: its ice is pixel art on the blocks (`path_ice`, below). Its slide pose is `tools/anims/ice_path.py`.

Rime Shell's plates: `shell_shapes.py` (23 crested slabs, each in its body part's space about the part's pivot) and
`build_rime_shell.py`, the lance's painter (`art/blockbench/rime_shell.bbmodel`, `models/fx/rime_shell.obj`,
`textures/entity/rime_shell*.png`). The preview sets them on a stand-in body. The toggle's pose is `tools/anims/rime_shell.py`.

    python3 -I tools/blockbench/build_rime_shell.py --preview  # also run/shell_preview/*.png

Shatter Shell has no mesh of its own: the plates that fly off are Rime Shell's, its frost ring on the ground is pixel art
(`shatter_ring`, below), and its shock ring is a smooth texture drawn by the game (`build_shatter_ring.py`, writing
`textures/fx/shatter_ring.png`). The cast's pose is `tools/anims/shatter_shell.py`.

    python3 -I tools/blockbench/build_shatter_ring.py

Freeze has no mesh either: its frost on a body is pixel art in the player skin's own 64x64 layout, so it lies on the body's
texels the way the marks lie on a block's (`build_freeze_frost.py`, PIL, the marks' palette, writing
`textures/entity/freeze_frost*.png` and their order maps; wide and slim arms). The frozen pose is `tools/anims/freeze.py`.

    python3 -I tools/blockbench/build_freeze_frost.py            # write them
    python3 -I tools/blockbench/build_freeze_frost.py --preview  # also run/freeze_preview/*.png: the unwrapped art, 8x, by coverage

## Marks on blocks: pixel art, no Blockbench

Rime's frost, cracks and ice on the world's blocks, and battle damage's cracks (`battle_crack`, DESIGN §3.24: Python, like
the rest), are pixel art at the blocks' own 16 texels a block, so they need no mesh: `build_block_marks.py` draws each pattern with PIL, from a few palette colours, and writes it with its order map to
`textures/marks/` (the game's `BlockMarks` lays them on the blocks' texel grid). No `.mcmeta`: they stay nearest-filtered.

    python3 -I tools/blockbench/build_block_marks.py            # write them
    python3 -I tools/blockbench/build_block_marks.py --preview  # also run/marks_preview/<name>.png: on stone, 8x, spreading

## Files

- `build_rig.py`: the preview rig, with PAL's bone names and pivots.
- `cdp.py`: a tiny Chrome DevTools client that drives Blockbench.
- `preview.js`: injected into Blockbench; renders the rig from fixed cameras.

## Blockbench and the game disagree?

Trust the game. What was checked in game with PAL 1.1.6 is written at the top of `tools/anims/kit.py`: which channels
replace the vanilla pose, and why there's a baked `hip` instead of a custom bone and no `bend` channel.

## Anchor's token (Swap): the Python is the source

A small solid teal coin (DESIGN.md §3.19). `build_swap_anchor.py` makes the faces itself (a 16-sided coin with bevelled
rims), lays them out and exports them with the lance's islands, atlas and Blockbench export, and paints them in Swap's teal
(`art/blockbench/swap_anchor.bbmodel`, `models/fx/swap_anchor.obj`, `textures/entity/swap_anchor*.png`). Its throw and recall
are `tools/anims/swap.py`. Blockbench caches a texture it has loaded, so after a repaint its preview can show the old one:
check the colours in game.

    python3 -I tools/blockbench/build_swap_anchor.py --preview  # also run/anchor_preview/*.png
