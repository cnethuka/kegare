"""Puppet Manipulation's player animations -> player_animations/puppet_manipulation.json.

A puppeteer's body language: hands that weave and pull invisible strings, sharp conductor's cues, and a performer's
flourishes. Every cast leaves the legs to the game (so it can be used on the move) and, unless the gesture needs the
head, leaves the head looking where the player aims.

Directions (player faces -Z, its right is +X): hip +Y twists the upper body right, +X leans it forward, +Z tilts it
to the left. Arms: -X raises the arm forward (-90 = level, -170 = overhead), the right arm's +Z swings it out to its
side (the left arm's -Z), +Y swings a raised arm toward the player's right. Head: -X looks up, +Y turns right.
hip_pos shifts the upper body's weight without moving the feet: -Y sinks (knees giving; never more than 1 px, and
never +Y: it would open a gap at the waist), +Z rocks back.
"""
from kit import Anim, merge, pose

# ---------------------------------------------------------------------------------------------- charge / release

# Weaving: hands in front of the chest, shoulder width apart, the head bowed over them, pumping strings in turns.
WEAVE = pose(hip=(8, 0, 0), head=(24, 0, 0), right_arm=(-74, -8, 0), left_arm=(-74, 8, 0))
WEAVE_R = merge(WEAVE, hip=(9, 9, 0), head=(22, 7, 0), right_arm=(-96, -14, -6), left_arm=(-56, 4, 6))
WEAVE_L = merge(WEAVE, hip=(9, -9, 0), head=(22, -7, 0), right_arm=(-56, -4, -6), left_arm=(-96, 14, 6))

create_charge = (Anim("puppet_create_charge", 0.95, loop=True, look=False, fade_in=3, loop_from=0.15,
                      note="Hold: both hands weave strings in front of the chest, head bowed over them.")
                 .key(0.15, WEAVE).key(0.35, WEAVE_R).key(0.55, WEAVE).key(0.75, WEAVE_L).key(0.95, WEAVE))

create_release = (Anim("puppet_create_release", 0.72, look=False, fade_out=6,
                       note="The strings are drawn in, then flung out to the right where the puppet appears; the "
                            "head turns to look at it.")
                  .key(0.0, WEAVE)
                  .key(0.08, pose(hip=(-6, -4, 0), head=(12, -4, 0), right_arm=(-84, -32, 0), left_arm=(-84, 32, 0)))
                  .key(0.16, pose(hip=(6, 22, 0), hip_pos=(0, -0.75, 0), head=(12, 30, 0), right_arm=(-38, 48, 26), left_arm=(28, -8, -16)))
                  .key(0.24, pose(hip=(4, 27, 0), hip_pos=(0, -1, 0), head=(10, 34, 0), right_arm=(-32, 56, 32), left_arm=(32, -8, -18)))
                  .key(0.42, pose(hip=(2, 15, 0), hip_pos=(0, -0.25, 0), head=(6, 20, 0), right_arm=(-22, 40, 18), left_arm=(12, 0, -8))))

# ---------------------------------------------------------------------------------------------- possession

# The puppet at the instant it's taken over: limp, before it jolts awake.
LIMP = pose(hip=(16, 0, 0), head=(34, 0, 0), right_arm=(-12, 0, 4), left_arm=(-12, 0, -4))
# The empty body's slack pose (ShellRenderer.SlackModel, to the degree): the return starts from it, so waking is seamless.
SHELL = pose(head=(31.5, 0, 0), right_arm=(2.9, 0, 2.3), left_arm=(2.9, 0, -2.3))

possess_cast = (Anim("possess_cast", 0.65, look=False, fade_out=5,
                     note="Plays on the possessed puppet: it slumps, then jolts awake, arms flung wide.")
                .key(0.0, LIMP)
                .key(0.12, pose(hip=(-10, 0, 0), head=(-22, 0, 0), right_arm=(-24, 0, 70), left_arm=(-24, 0, -70)))
                .key(0.22, pose(hip=(-8, 0, 3), head=(-14, 0, 6), right_arm=(-32, 0, 86), left_arm=(-32, 0, -84)))
                .key(0.40, pose(hip=(0, 0, -4), head=(0, 0, 9), right_arm=(-16, 0, 34), left_arm=(-16, 0, -36))))

possess_return = (Anim("possess_return", 0.7, look=False, fade_out=6,
                       note="Back in the body: a gasp, the head snapping up and the arms flinching, then settling.")
                  .key(0.0, SHELL)
                  .key(0.10, pose(hip=(-12, 0, 0), hip_pos=(0, 0, 0.5), head=(-26, 0, 0), right_arm=(22, 0, 26), left_arm=(22, 0, -26)))
                  .key(0.22, pose(hip=(-6, 0, 0), hip_pos=(0, 0, 0.25), head=(-14, 0, 0), right_arm=(12, 0, 18), left_arm=(12, 0, -18)))
                  .key(0.40, pose(hip=(4, 0, 0), hip_pos=(0, -0.5, 0), head=(6, 0, 0), right_arm=(-6, 0, 6), left_arm=(-6, 0, -6))))

# ---------------------------------------------------------------------------------------------- directive / focus

directive_cast = (Anim("directive_cast", 0.72, fade_in=3, fade_out=6,
                       note="A conductor's cue: the hand rises over the shoulder, cuts down and across, and stops "
                            "on a sharp downward point.")
                  .key(0.15, pose(hip=(-6, -14, 0), right_arm=(-152, 20, 26), left_arm=(-10, 0, -12)))
                  .key(0.24, pose(hip=(6, 16, 0), hip_pos=(0, -0.75, 0), right_arm=(-72, -26, -8), left_arm=(16, 0, -12)))
                  .key(0.32, pose(hip=(10, 11, 0), hip_pos=(0, -1, 0), right_arm=(-54, -10, 0), left_arm=(14, 0, -10)))
                  .key(0.42, pose(hip=(7, 10, 0), hip_pos=(0, -0.5, 0), right_arm=(-60, -12, 0), left_arm=(10, 0, -8))))

# Pointing at the target: the arm is turned back by the hip's twist so it points where the player looks.
focus_point = (Anim("focus_point", 0.85, fade_in=2, fade_out=6,
                    note="Wind up, then a hard point at the target while the other hand yanks a string taut; held.")
               .key(0.10, pose(hip=(-4, -22, 0), hip_pos=(0, 0, 0.5), right_arm=(34, 0, 14), left_arm=(-40, 16, 0)))
               .key(0.18, pose(hip=(6, 20, 0), hip_pos=(0, -0.5, -0.5), right_arm=(-92, -20, 0), left_arm=(30, -10, -20)))
               .key(0.24, pose(hip=(8, 24, 0), hip_pos=(0, -0.75, -0.75), right_arm=(-97, -24, 0), left_arm=(34, -10, -22)))
               .key(0.55, pose(hip=(5, 18, 0), hip_pos=(0, -0.25, -0.25), right_arm=(-90, -18, 0), left_arm=(26, -8, -18))))

# ---------------------------------------------------------------------------------------------- bursts

curio_burst_cast = (Anim("curio_burst_cast", 0.8, fade_in=2, fade_out=6,
                         note="Reach toward the puppet, a tense beat, then the fist yanks back as it goes off.")
                    .key(0.10, pose(hip=(6, 8, 0), hip_pos=(0, 0, -0.5), right_arm=(-84, -8, 0), left_arm=(-10, 0, -10)))
                    .key(0.18, pose(hip=(7, 9, 0), hip_pos=(0, 0, -0.75), right_arm=(-89, -8, 3), left_arm=(-12, 0, -10)))
                    .key(0.24, pose(hip=(-8, -18, 0), hip_pos=(0, 0, 0.75), right_arm=(26, 10, 20), left_arm=(-36, 10, 0)))
                    .key(0.32, pose(hip=(-10, -22, 0), hip_pos=(0, 0, 1), right_arm=(36, 12, 24), left_arm=(-40, 12, 0)))
                    .key(0.50, pose(hip=(-3, -8, 0), hip_pos=(0, 0, 0.25), right_arm=(10, 5, 10), left_arm=(-14, 4, -4))))

# The high pose is a wide "Y" (arms up and OUT): straight overhead they'd cross in front of the first-person camera.
chain_burst_cast = (Anim("chain_burst_cast", 1.0, fade_in=2, fade_out=6,
                         note="Gather both hands, fling them up into a wide Y, then slam them down and out: every "
                              "string cut.")
                    .key(0.10, pose(hip=(12, 0, 0), hip_pos=(0, -0.75, 0), right_arm=(-80, -36, 0), left_arm=(-80, 36, 0)))
                    .key(0.24, pose(hip=(-12, 0, 0), right_arm=(-30, 0, 136), left_arm=(-30, 0, -136)))
                    .key(0.34, pose(hip=(-14, 0, 0), right_arm=(-32, 0, 146), left_arm=(-32, 0, -146)))
                    .key(0.44, pose(hip=(16, 0, 0), hip_pos=(0, -1, 0), right_arm=(-18, 0, 58), left_arm=(-18, 0, -58)))
                    .key(0.52, pose(hip=(18, 0, 0), hip_pos=(0, -1, 0), right_arm=(-10, 0, 50), left_arm=(-10, 0, -50)))
                    .key(0.70, pose(hip=(6, 0, 0), hip_pos=(0, -0.5, 0), right_arm=(-4, 0, 26), left_arm=(-4, 0, -26))))

# ---------------------------------------------------------------------------------------------- understudy / stand-in

understudy_cast = (Anim("understudy_cast", 0.7, fade_out=6,
                        note="Plays as the player lands in the puppet's place: a magician's reveal, arms flung open.")
                   .key(0.0, pose(hip=(10, 0, 0), hip_pos=(0, -1, 0), right_arm=(-66, -38, 0), left_arm=(-66, 38, 0)))
                   .key(0.12, pose(hip=(-9, 0, 0), right_arm=(-24, 0, 112), left_arm=(-24, 0, -112)))
                   .key(0.20, pose(hip=(-11, 0, 0), right_arm=(-26, 0, 122), left_arm=(-26, 0, -122)))
                   .key(0.40, pose(hip=(-2, 0, 0), right_arm=(-14, 0, 46), left_arm=(-14, 0, -46))))

stand_in_cast = (Anim("stand_in_cast", 1.08, fade_in=2, fade_out=8,
                      note="A stage bow: the hand sweeps across the waist, the other tucked behind the back; then it "
                           "rises presenting the double.")
                 .key(0.10, pose(hip=(0, 10, 0), right_arm=(-60, 40, 60), left_arm=(26, 20, -8)))
                 .key(0.30, pose(hip=(32, 0, 0), hip_pos=(0, -0.5, 0), right_arm=(-72, -50, -20), left_arm=(46, 30, -6)))
                 .key(0.50, pose(hip=(34, 0, 0), hip_pos=(0, -0.5, 0), right_arm=(-70, -48, -18), left_arm=(46, 30, -6)))
                 .key(0.68, pose(hip=(-4, 0, 0), right_arm=(-70, 45, 40), left_arm=(20, 0, -6))))

# ---------------------------------------------------------------------------------------------- the puppet itself

# "Kuru kuru": played on the puppet by PuppetPoses (the whole-body turn is the body bone's Y), arms out, head tipping.
idle_spin = Anim("puppet_idle_spin", 0.9, loop=True, look=False, lerp="linear",
                 note="On the puppet: one full turn every 0.9 s, arms out and bobbing, head tipping side to side.")
for i, (spin, out, tip) in enumerate(((0, 72, 6), (90, 64, 0), (180, 72, -6), (270, 64, 0), (360, 72, 6))):
    idle_spin.key(i * 0.225, pose(body=(0, spin, 0), head=(-10, 0, tip), right_arm=(0, 0, out), left_arm=(0, 0, -out)))

ANIMS = [create_charge, create_release, possess_cast, possess_return, directive_cast, focus_point,
         curio_burst_cast, chain_burst_cast, understudy_cast, stand_in_cast, idle_spin]
