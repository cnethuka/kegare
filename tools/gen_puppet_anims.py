"""Writes assets/kegare/player_animations/puppet_manipulation.json (Player Animation Library / Bedrock format).

Run from the project root: python3 tools/gen_puppet_anims.py
Rotations are degrees per bone. The caster animations play through PAL. puppet_idle_spin plays on the puppet itself
through Kegare's PuppetPoses sampler (PAL animates players only), which reads the same file: there, "body" turns the
whole puppet and arm/head rotations use the model's own axes (z positive = the right arm swings out).
"""
import json
from pathlib import Path

OUT = Path("src/main/resources/assets/kegare/player_animations/puppet_manipulation.json")


def keys(*frames):
    out = {}
    for t, v, e in frames:
        out["0.0" if t == 0 else repr(float(t))] = {"vector": list(v), "easing": e}
    return out


def anim(length, bones, loop=False):
    return {"animation_length": length, "loop": loop, "bones": {b: {"rotation": keys(*f)} for b, f in bones.items()}}


A = {}

# Idle spin ("kuru kuru"), on the puppet: one full turn every 0.9 s, arms out, head tipped back a little.
A["puppet_idle_spin"] = anim(0.9, {
    "body": [(0, (0, 0, 0), "linear"), (0.9, (0, 360, 0), "linear")],
    "right_arm": [(0, (0, 0, 72), "linear"), (0.45, (0, 0, 64), "easeInOutSine"), (0.9, (0, 0, 72), "easeInOutSine")],
    "left_arm": [(0, (0, 0, -72), "linear"), (0.45, (0, 0, -64), "easeInOutSine"), (0.9, (0, 0, -72), "easeInOutSine")],
    "head": [(0, (-12, 0, 0), "linear"), (0.9, (-12, 0, 0), "linear")],
}, loop=True)

# Puppet Creation, charging (loops): both hands held out, fingers working the strings in turn.
A["puppet_create_charge"] = anim(0.8, {
    "right_arm": [(0, (-62, -14, 6), "easeInOutSine"), (0.2, (-74, -10, 4), "easeInOutSine"), (0.4, (-62, -14, 6), "easeInOutSine"),
                  (0.6, (-56, -16, 8), "easeInOutSine"), (0.8, (-62, -14, 6), "easeInOutSine")],
    "left_arm": [(0, (-62, 14, -6), "easeInOutSine"), (0.2, (-56, 16, -8), "easeInOutSine"), (0.4, (-62, 14, -6), "easeInOutSine"),
                 (0.6, (-74, 10, -4), "easeInOutSine"), (0.8, (-62, 14, -6), "easeInOutSine")],
    "head": [(0, (14, 0, 0), "linear"), (0.8, (14, 0, 0), "linear")],
}, loop=True)

# Puppet Creation, release: the strings are pulled taut, then the right hand flicks down and out to where the puppet stands.
A["puppet_create_release"] = anim(0.5, {
    "right_arm": [(0, (-70, -12, 6), "easeOutQuad"), (0.07, (-96, -4, 2), "easeOutQuad"), (0.18, (-30, 10, 40), "easeOutQuad"),
                  (0.32, (-24, 8, 36), "easeInOutSine"), (0.5, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (-70, 12, -6), "easeOutQuad"), (0.12, (-40, 10, -14), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.18, (0, -14, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (14, 0, 0), "easeOutQuad"), (0.18, (8, -20, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
})

# Possess: two fingers to the temple, then the hand flung toward the puppet as the mind leaves.
A["possess_cast"] = anim(0.6, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.12, (-150, -40, -20), "easeOutQuad"), (0.28, (-146, -42, -18), "linear"),
                  (0.38, (-92, 10, 8), "easeOutQuad"), (0.6, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (0, 0, 0), "easeOutQuad"), (0.12, (10, -12, 0), "easeOutQuad"), (0.3, (14, -10, 0), "linear"),
             (0.38, (-8, 0, 0), "easeOutQuad"), (0.6, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.38, (4, 6, 0), "easeOutQuad"), (0.6, (0, 0, 0), "easeInOutSine")],
})

# Returning to the body: a jolt (head snaps up, arms twitch out), then the body settles.
A["possess_return"] = anim(0.5, {
    "head": [(0, (24, 0, 0), "linear"), (0.08, (-16, 0, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "right_arm": [(0, (0, 0, 0), "linear"), (0.08, (-20, 0, 24), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (0, 0, 0), "linear"), (0.08, (-20, 0, -24), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (10, 0, 0), "linear"), (0.1, (-4, 0, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
})

# Directive: a raised hand sweeps out over the puppets, then snaps closed (the order given).
A["directive_cast"] = anim(0.55, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.12, (-160, 10, -10), "easeOutQuad"), (0.3, (-110, 30, 30), "easeInOutSine"),
                  (0.36, (-112, 28, 28), "linear"), (0.55, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.3, (0, 12, 0), "easeInOutSine"), (0.55, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (0, 0, 0), "easeOutQuad"), (0.12, (-10, 0, 0), "easeOutQuad"), (0.55, (0, 0, 0), "easeInOutSine")],
})

# Focus: a long arm thrust straight at the target, held, the other hand drawn back like pulling a string taut.
A["focus_point"] = anim(0.7, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.08, (-92, -6, 0), "easeOutQuad"), (0.45, (-90, -6, 0), "linear"),
                  (0.7, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.1, (-70, 40, 0), "easeOutQuad"), (0.45, (-68, 42, 0), "linear"),
                 (0.7, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.1, (0, -16, 0), "easeOutQuad"), (0.45, (0, -16, 0), "linear"), (0.7, (0, 0, 0), "easeInOutSine")],
})

# Curio Burst: the hand reaches out, then clenches into a fist and yanks back (the string pulled, the curio pops).
A["curio_burst_cast"] = anim(0.5, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.1, (-96, -8, 0), "easeOutQuad"), (0.18, (-100, -8, 0), "linear"),
                  (0.26, (-56, 6, 10), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.26, (0, 10, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (0, 0, 0), "easeOutQuad"), (0.26, (-6, 6, 0), "easeOutQuad"), (0.5, (0, 0, 0), "easeInOutSine")],
})

# Chain Burst: both hands thrown wide open overhead, fingers spread, then brought down (every string let go at once).
A["chain_burst_cast"] = anim(0.7, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.14, (-170, -20, 40), "easeOutQuad"), (0.3, (-165, -20, 44), "linear"),
                  (0.45, (-40, 0, 30), "easeInQuad"), (0.7, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.14, (-170, 20, -40), "easeOutQuad"), (0.3, (-165, 20, -44), "linear"),
                 (0.45, (-40, 0, -30), "easeInQuad"), (0.7, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (0, 0, 0), "easeOutQuad"), (0.14, (-18, 0, 0), "easeOutQuad"), (0.45, (8, 0, 0), "easeInQuad"), (0.7, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.14, (-6, 0, 0), "easeOutQuad"), (0.45, (6, 0, 0), "easeInQuad"), (0.7, (0, 0, 0), "easeInOutSine")],
})

# Understudy: a quick beckon with two fingers (come here), the body already turning to where the puppet stands.
A["understudy_cast"] = anim(0.45, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.08, (-84, -10, 0), "easeOutQuad"), (0.18, (-120, -6, 0), "easeOutQuad"),
                  (0.26, (-70, -10, 0), "easeInOutSine"), (0.45, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.18, (0, 14, 0), "easeOutQuad"), (0.45, (0, 0, 0), "easeInOutSine")],
})

# Stand-In: arms spread wide, presenting (here, take my place), then a small bow.
A["stand_in_cast"] = anim(0.8, {
    "right_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.2, (-40, 0, 70), "easeOutQuad"), (0.45, (-40, 0, 70), "linear"),
                  (0.8, (0, 0, 0), "easeInOutSine")],
    "left_arm": [(0, (0, 0, 0), "easeOutQuad"), (0.2, (-40, 0, -70), "easeOutQuad"), (0.45, (-40, 0, -70), "linear"),
                 (0.8, (0, 0, 0), "easeInOutSine")],
    "torso": [(0, (0, 0, 0), "easeOutQuad"), (0.45, (0, 0, 0), "linear"), (0.6, (16, 0, 0), "easeInOutSine"), (0.8, (0, 0, 0), "easeInOutSine")],
    "head": [(0, (0, 0, 0), "easeOutQuad"), (0.45, (0, 0, 0), "linear"), (0.6, (20, 0, 0), "easeInOutSine"), (0.8, (0, 0, 0), "easeInOutSine")],
})

OUT.write_text(json.dumps({"format_version": "1.8.0", "animations": A}, indent=2) + "\n")
print("wrote", OUT, "with", len(A), "animations")
