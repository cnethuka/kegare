"""Glacial Lance's player animations -> player_animations/glacial_lance.json (the first of Rime's moves to be reworked; the rest
are still hand-keyed in rime.json).

A javelin thrower's body: the charge winds the whole upper body back, the throwing arm stretched out behind with the lance lying
along it, tip forward past the head, the other arm out in front on the target. The release is the whole body whipping through:
hips first, then the chest, then the arm overhead and down across the body, the free arm torn back to pay for it.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward, +X swings back, the right
arm's +Z swings it out to its side. Both animations leave your own first-person hands to vanilla (RimeClient).
"""
import math

import kit
from kit import Anim, merge, pose

# Wound back: chest turned away, leaning back over the rear foot, the throwing arm straight out behind, the hand up by the ear and
# out to the side, so the lance it holds passes beside the head, not through it.
WOUND = pose(hip=(-7, 24, 0), hip_pos=(0, -0.5, 0.5), right_arm=(112, -40, 0), left_arm=(-86, -22, 0))
_WOUND_TIGHT = merge(WOUND, hip=(-8, 27, 0), right_arm=(116, -42, 0), left_arm=(-85, -24, 0))

glacial_lance_charge = (Anim("glacial_lance_charge", 1.3, loop=True, fade_in=2, loop_from=0.25,
                             note="Winds back in a beat, then holds the throw drawn: the arm creeps further back and the chest "
                                  "further round as it breathes, never letting go of the aim.")
                        .key(0.1, merge(WOUND, hip=(-4, 16, 0), right_arm=(84, -30, 0)))
                        .key(0.25, WOUND)
                        .key(0.8, _WOUND_TIGHT)
                        .key(1.3, WOUND))

glacial_lance_release = (Anim("glacial_lance_release", 0.8, lerp="catmullrom", fade_out=8,
                              note="The whip: hips and chest snap round, the arm comes over the top and is let go at full "
                                   "stretch, then carries on down across the body while the free arm tears back.")
                         .key(0.0, WOUND)
                         .key(0.05, pose(hip=(4, 10, 0), hip_pos=(0, -0.5, 0), right_arm=(-150, 0, 18), left_arm=(-40, -10, -10)), "linear")
                         .key(0.1, pose(hip=(14, -22, 0), hip_pos=(0, -1, -0.5), right_arm=(-118, -12, 6), left_arm=(30, 0, -26)))
                         .key(0.22, pose(hip=(20, -34, 0), hip_pos=(0, -1.25, -0.75), right_arm=(-46, -38, 0), left_arm=(44, 0, -30)))
                         .key(0.4, pose(hip=(16, -28, 0), hip_pos=(0, -1, -0.5), right_arm=(-30, -30, 0), left_arm=(36, 0, -24))))

ANIMS = [glacial_lance_charge, glacial_lance_release]


def grip_for(wound, tilt):
    """The lance's rotation in the right arm's own space (degrees, X then Y then Z) that, in the wound pose, points it straight
    ahead and `tilt` degrees up with its blade upright, whichever way the arm is turned to get the hand there."""
    m = kit.matrix(wound.baked()["right_arm"][0])
    t = math.radians(tilt)
    z = (0.0, math.sin(t), -math.cos(t))
    x = (0.0, math.cos(t), math.sin(t))  # the blade's width: up, square to the shaft
    y = (z[1] * x[2] - z[2] * x[1], z[2] * x[0] - z[0] * x[2], z[0] * x[1] - z[1] * x[0])
    want = [[x[i], y[i], z[i]] for i in range(3)]
    r = kit._mul([[m[j][i] for j in range(3)] for i in range(3)], want)
    ry = math.asin(max(-1.0, min(1.0, r[0][2])))
    rx, rz = math.atan2(-r[1][2], r[2][2]), math.atan2(-r[0][1], r[0][0])
    return tuple(round(math.degrees(a), 1) for a in (rx, ry, rz))


# The lance in the right hand, as the preview draws it (Blockbench pixels from the right arm's pivot, in its own space: the arm hangs
# along -Y, its outside is +X). The game draws it from the same numbers (LanceHold.java; GlacialLanceArtTest checks they match), so if
# the wound pose changes, so does this: build.py stops if it's stale.
LANCE_GRIP = {"position": (1.0, -10.0, 0.0), "rotation": (-64.3, 16.3, -88.2), "scale": 0.85}
GRIP_TILT = 10.0
