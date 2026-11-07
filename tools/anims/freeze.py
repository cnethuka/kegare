"""Freeze's player animation -> player_animations/freeze.json (in the Glacial Lance's style: the whole body, in Blockbench, from
Python).

Frozen solid (the frozen loop FreezeService starts at the most stacks): the body seizes the instant the ice closes over it,
caught flinching from the cold: hunched, shoulders up, head ducked, arms drawn in across the chest with the hands near the
shoulders, knees a little bent. A jolt past the pose, then it sets, and while it's held there's only a faint tremble in the
arms and head, a body locked in ice, not a body moving (FreezeService stuns and roots it; FreezeClient glazes it). The
2-tick blend in from wherever the body was is the seize; breaking free blends back out.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), the right
arm's +Z swings it out to its side (the left arm's -Z); raised, the right arm's -Y draws it in across the chest. Legs: -X swings forward.
"""
from kit import Anim, pose

_ARMS = dict(right_arm=(-70, -26, -10), left_arm=(-70, 26, 10))
JOLT = pose(hip=(15, 0, 0), hip_pos=(0, -0.9, 0), head=(20, 0, 0), right_leg=(-14, 0, 0), left_leg=(-8, 0, 0),
            right_arm=(-80, -34, -14), left_arm=(-80, 34, 14))
SET = pose(hip=(11, 0, 0), hip_pos=(0, -0.6, 0), head=(15, 0, 0), right_leg=(-10, 0, 0), left_leg=(-6, 0, 0), **_ARMS)
# The tremble: a degree or two either way in the arms and head, nothing else.
SHAKE_A = pose(hip=(11, 0, 0), hip_pos=(0, -0.6, 0), head=(15, 0, 1.2), right_leg=(-10, 0, 0), left_leg=(-6, 0, 0),
               right_arm=(-70, -26, -11.5), left_arm=(-70, 26, 11.5))
SHAKE_B = pose(hip=(11, 0, 0), hip_pos=(0, -0.6, 0), head=(15, 0, -1.2), right_leg=(-10, 0, 0), left_leg=(-6, 0, 0),
               right_arm=(-70, -26, -8.5), left_arm=(-70, 26, 8.5))

frozen = (Anim("frozen", 0.6, loop=True, fade_in=2, loop_from=0.2, lerp="catmullrom",
               note="Frozen solid: seized flinching from the cold, hunched, arms drawn in across the chest; then held, only a "
                    "faint tremble.")
          .key(0.1, JOLT)
          .key(0.2, SET)
          .key(0.3, SHAKE_A, "linear")
          .key(0.4, SET, "linear")
          .key(0.5, SHAKE_B, "linear")
          .key(0.6, SET, "linear"))

ANIMS = [frozen]
