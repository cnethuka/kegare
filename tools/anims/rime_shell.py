"""Rime Shell's player animation -> player_animations/rime_shell.json (in the Glacial Lance's style: the whole body, in Blockbench,
from Python).

The ice closes over the body: the arms cross over the chest, fists to the opposite shoulders, the back hunched as if against the
cold; then the chest comes up and the arms are flung down and out to the sides, fists clenched, as the plates form over them (from
the fourth tick, as the arms leave the chest, chest plates first: ShellClient.DELAY). The same move plays as the shell comes down,
the plates breaking off and falling at once.
Legs are left to walking, as everywhere.

Directions as in melee.py: hip +X leans forward, +Y twists right, +Z tilts left. Arms: -X raises forward (-90 = level), +Y swings
a raised arm toward the player's right, the right arm's +Z swings it out to its side (the left arm's -Z). Your own first-person
hands stay vanilla (RimeClient).
"""
from kit import Anim, pose

CROSSED = pose(hip=(8, 0, 0), hip_pos=(0, -0.5, -0.3), right_arm=(-74, -52, 0), left_arm=(-78, 50, 0))
FLEXED = pose(hip=(-7, 0, 0), hip_pos=(0, 0.2, 0.2), right_arm=(12, 0, 30), left_arm=(12, 0, -30))
SET = pose(hip=(-3, 0, 0), right_arm=(6, 0, 20), left_arm=(6, 0, -20))

# The code blends every animation in over its first 3 ticks (PlayerAnimations), so the crossed arms sit at 2 ticks.
rime_shell_toggle = (Anim("rime_shell_toggle", 0.75, lerp="catmullrom", fade_out=5,
                          note="Arms crossed over the chest, hunched; then the chest up and the arms flung down and out, fists "
                               "clenched, as the ice closes over the body.")
                     .key(0.0, pose())
                     .key(0.1, CROSSED)
                     .key(0.2, CROSSED, "linear")
                     .key(0.3, FLEXED)
                     .key(0.5, SET))

ANIMS = [rime_shell_toggle]
