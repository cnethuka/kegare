"""Glacial Spire's player animations -> player_animations/glacial_spire.json (in the Glacial Lance's style: the whole body, in
Blockbench, from Python).

The charge raises the fist high over the head and holds it there, gathering cold, the body drawn up and leaning back, the free hand
out and down at the spot on the ground where it'll come up. The release is the fist brought down like a hammer onto the ground from
afar: a hitch higher, then the whole upper body drops into it, the free arm thrown back, and it stays down a beat (the crack is
spreading), then rises. Legs are left to walking, as everywhere.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward, +X swings back, the right
arm's +Z swings it out to its side. Both animations leave your own first-person hands to vanilla (RimeClient).
"""
from kit import Anim, merge, pose

# Fist up: the right arm up over the head and a touch out (kept short of -175: baked with the hip, it would wrap round), the left out in front and down at the target, leaning back.
RAISED = pose(hip=(-6, 10, 0), hip_pos=(0, 0, 0.4), right_arm=(-158, 10, 14), left_arm=(-62, -14, -6))
_RAISED_HIGH = merge(RAISED, hip=(-9, 12, 0), right_arm=(-164, 10, 12), left_arm=(-66, -14, -6))
# Brought down: bowed over it, the fist driven down in front, the free arm flung back.
SLAM = pose(hip=(30, -8, 0), hip_pos=(0, -1.5, -0.6), right_arm=(-36, -6, 4), left_arm=(38, 0, -24))

glacial_spire_charge = (Anim("glacial_spire_charge", 1.2, loop=True, fade_in=2, loop_from=0.2,
                             note="The fist goes up in a beat and holds there, gathering cold: it strains a little higher as the "
                                  "body draws back, the free hand never leaving the spot it'll rise from.")
                        .key(0.1, merge(RAISED, hip=(-3, 6, 0), right_arm=(-110, 8, 18)))
                        .key(0.2, RAISED)
                        .key(0.7, _RAISED_HIGH)
                        .key(1.2, RAISED))

glacial_spire_release = (Anim("glacial_spire_release", 0.7, lerp="catmullrom", fade_out=8,
                              note="The hammer: a hitch higher, then the whole body drops into the blow and drives the fist down; "
                                   "it stays down while the ground cracks, then eases back up.")
                         .key(0.0, RAISED)
                         .key(0.04, merge(_RAISED_HIGH, hip=(-12, 12, 0), right_arm=(-170, 10, 10)), "linear")
                         .key(0.1, SLAM, "linear")
                         .key(0.16, merge(SLAM, hip=(26, -7, 0), hip_pos=(0, -1.3, -0.5), right_arm=(-40, -6, 4)))
                         .key(0.3, merge(SLAM, hip=(22, -6, 0), hip_pos=(0, -1.2, -0.4), right_arm=(-38, -6, 4))))

ANIMS = [glacial_spire_charge, glacial_spire_release]
