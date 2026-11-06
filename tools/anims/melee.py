"""The shared melee layer's player animations -> player_animations/melee.json.

Not a technique's body language but a fighter's: weight low and committed. Dash is Kinetic Manipulation, so the whole body goes
with it, legs included. Parry and Strike are Reinforcement: arms driven by the hips, with a reaction you can read at a distance.

Directions (player faces -Z, its right is +X): hip or body +X leans forward, +Y twists right, +Z tilts to the left. Arms: -X raises
forward (-90 = level), the right arm's +Z swings it out to its side (the left arm's -Z), +Y swings a raised arm toward the player's
right. Legs: -X forward, +X back. Head: -X looks up. Anything that follows the server's timing says so: a reaction's key pose sits
on its first frames, because the server has already applied the hit by the time it plays.
"""
from kit import Anim, merge, pose

# ---------------------------------------------------------------------------------------------- dash (server: 4 ticks of push)

# Each direction launches on its first frames, holds the shape while the push lasts, and recovers as the speed runs out.
_FORWARD_LOW = pose(body=(30, 0, 0), body_pos=(0, -2.5, 0), right_arm=(58, 0, 12), left_arm=(46, 0, -12), right_leg=(-62, 0, 0), left_leg=(44, 0, 0))
dash_forward = (Anim("dash_forward", 0.5, fade_in=1, fade_out=6,
                     note="Launches low: the body thrown ahead of the feet, arms trailing, a long stride.")
                .key(0.05, _FORWARD_LOW)
                .key(0.2, merge(_FORWARD_LOW, body=(26, 0, 0), right_leg=(-52, 0, 0), left_leg=(52, 0, 0))))

_BACK = pose(body=(-20, 0, 0), body_pos=(0, -1.5, 0), right_arm=(-34, 0, 18), left_arm=(-48, 0, -18), right_leg=(-38, 0, 0), left_leg=(30, 0, 0))
dash_back = (Anim("dash_back", 0.5, fade_in=1, fade_out=6,
                  note="Pushes off the front foot, leaning away from what's in front, hands still up.")
             .key(0.05, _BACK)
             .key(0.2, merge(_BACK, body=(-16, 0, 0), right_leg=(-30, 0, 0), left_leg=(36, 0, 0))))

_LEFT = pose(body=(8, 0, 20), body_pos=(0, -1.5, 0), right_arm=(-16, 0, 44), left_arm=(18, 0, -16), right_leg=(0, 0, 30), left_leg=(0, 0, -8))
dash_left = (Anim("dash_left", 0.5, fade_in=1, fade_out=6,
                  note="Thrown to its left, the right leg pushing off and the right arm flung out for balance.")
             .key(0.05, _LEFT)
             .key(0.2, merge(_LEFT, body=(6, 0, 16), right_leg=(0, 0, 22), left_leg=(0, 0, -14))))

_RIGHT = pose(body=(8, 0, -20), body_pos=(0, -1.5, 0), right_arm=(18, 0, 16), left_arm=(-16, 0, -44), right_leg=(0, 0, 8), left_leg=(0, 0, -30))
dash_right = (Anim("dash_right", 0.5, fade_in=1, fade_out=6,
                   note="The same to its right.")
              .key(0.05, _RIGHT)
              .key(0.2, merge(_RIGHT, body=(6, 0, -16), right_leg=(0, 0, 14), left_leg=(0, 0, -22))))

# ---------------------------------------------------------------------------------------------- parry

# Forearms crossed in front of the chest, low enough to see over in first person; rigid arms, so the cross comes from the shoulders.
GUARD = pose(hip=(8, 0, 0), hip_pos=(0, -0.5, 0), right_arm=(-72, -34, 0), left_arm=(-84, 34, 0))
_GUARD_B = merge(GUARD, hip=(9, 0, 0), hip_pos=(0, -0.75, 0), right_arm=(-70, -34, 0), left_arm=(-82, 34, 0))
parry_guard = (Anim("parry_guard", 1.15, loop=True, fade_in=1, loop_from=0.15,
                    note="Hold: snaps up into a cross guard on its first frames (it has to read in the perfect window), then breathes.")
               .key(0.05, merge(GUARD, right_arm=(-88, -30, 0), left_arm=(-100, 30, 0)))
               .key(0.15, GUARD).key(0.65, _GUARD_B).key(1.15, GUARD))

parry_perfect = (Anim("parry_perfect", 0.45,
                      note="The moment it lands: the guard bursts open, both arms thrown wide, the chest back. Wide and not high, because "
                           "a raised arm fills the first-person view. Ends back on the guard, because the hold usually goes on.")
                 .key(0.0, GUARD)
                 .key(0.05, pose(hip=(-12, -8, 0), hip_pos=(0, -0.25, 0.25), right_arm=(-64, 46, 44), left_arm=(-50, -46, -44)))
                 .key(0.2, pose(hip=(-9, -6, 0), hip_pos=(0, -0.25, 0.25), right_arm=(-58, 42, 40), left_arm=(-46, -42, -40)))
                 .key(0.45, GUARD))

parry_recovery = (Anim("parry_recovery", 0.8, look=False, fade_in=1, fade_out=7,
                       note="A guard that caught nothing: hands fall away, shoulders drop and the head goes down, off balance and open.")
                  .key(0.05, pose(hip=(14, 0, 5), hip_pos=(0, -1, 0.25), head=(16, 0, 0), right_arm=(-22, 0, 16), left_arm=(-10, 0, -22)))
                  .key(0.3, pose(hip=(18, 4, 6), hip_pos=(0, -1, 0.25), head=(24, 2, 0), right_arm=(10, 0, 18), left_arm=(14, 0, -20)))
                  .key(0.45, pose(hip=(12, 2, 3), hip_pos=(0, -0.5, 0), head=(14, 0, 0), right_arm=(6, 0, 10), left_arm=(8, 0, -12))))

stagger = (Anim("stagger", 0.5, look=False, fade_out=4,
                note="On the parried attacker: the striking arm knocked out wide, chest open, head snapped back, a step to catch "
                     "themselves. Out and not up, so it stays at the edge of their own first-person view.")
           .key(0.0, pose(hip=(-10, 10, 0), hip_pos=(0, 0, 0.25), head=(-8, 6, 0), right_arm=(-96, 42, 40), left_arm=(14, 0, -18)))
           .key(0.08, pose(hip=(-20, 18, -4), hip_pos=(0, 0, 0.5), head=(-18, 12, 0), right_arm=(-112, 58, 62), left_arm=(26, 0, -30), right_leg=(22, 0, 4), left_leg=(-12, 0, 0)))
           .key(0.22, pose(hip=(-14, 14, -3), hip_pos=(0, -0.25, 0.5), head=(-10, 8, 0), right_arm=(-100, 50, 54), left_arm=(18, 0, -24), right_leg=(16, 0, 4), left_leg=(-8, 0, 0)))
           .key(0.3, pose(hip=(-4, 6, -1), head=(-2, 2, 0), right_arm=(-50, 20, 24), left_arm=(8, 0, -12), right_leg=(6, 0, 2), left_leg=(-4, 0, 0))))

# ---------------------------------------------------------------------------------------------- reinforced strike

reinforced_strike = (Anim("reinforced_strike", 0.55, fade_in=1, fade_out=6,
                          note="Fired by the hit itself, so it opens on full extension: hips driven through, the arm straight down the "
                               "line, the other pulled back hard, then a beat held on the follow-through.")
                     .key(0.05, pose(hip=(16, -30, 0), hip_pos=(0, -1, 0), right_arm=(-96, -16, 0), left_arm=(44, 0, -14)))
                     .key(0.25, pose(hip=(14, -26, 0), hip_pos=(0, -0.75, 0), right_arm=(-92, -12, 0), left_arm=(38, 0, -12))))

ANIMS = [dash_forward, dash_back, dash_left, dash_right, parry_guard, parry_perfect, parry_recovery, stagger, reinforced_strike]
