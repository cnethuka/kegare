"""Puppet Manipulation's sounds (DESIGN §3.20). Only Curio Burst's are made here: the rest are vanilla's (wool, soul escape,
chime, bell, mirror, bobber, leash), which already fit thread.

Curio Burst is a puppet bursting like an overwound toy: a dry pop, then its music box's comb struck all at once, a bright
detuned jangle dying fast. SOUNDS maps the file each one ships as (assets/kegare/sounds/puppet_manipulation/<name>.ogg) to how
it's made. BURST_CANDIDATES are the versions made for choosing (run/sounds_preview/puppet_manipulation/, with --preview); the
owner picked 3_music_box, which ships as curio_burst.
"""
from __future__ import annotations

import math
import random

import impact
from synth import RATE, bandpass, bursts, finish, highpass, lowpass, mix, mul, noise, room, tone


def _pop(seconds: float, seed: int, centre: float = 1100.0) -> list[float]:
    """The burst itself: a sharp crack of band-limited noise over a short low knock, no rumble after it (it's a toy, not a bomb)."""
    n = int(seconds * RATE)
    crack = mul(highpass(bandpass(noise(seconds, seed), centre, 0.9), 380), bursts(n, [0.0, 0.004], 0.009, levels=[1.0, 0.6]))
    knock = mul(lowpass(tone(seconds, 118, 0.05, sweep=0.35), 400), bursts(n, [0.0], 0.06))
    return mix((1.0, crack), (0.9, knock))


def _spring(seconds: float, freq: float, start: float, decay: float, wobble: float = 7.0, depth: float = 0.06) -> list[float]:
    """A coil spring let go: a bright metallic twang whose pitch wobbles as it shakes out (two detuned partials beating)."""
    n = int(seconds * RATE)
    out = [0.0] * n
    s = int(start * RATE)
    phase_a = phase_b = 0.0
    for i in range(s, n):
        t = (i - s) / RATE
        bend = 1.0 + depth * math.sin(2 * math.pi * wobble * t) * math.exp(-t / (decay * 1.5)) - 0.12 * (1 - math.exp(-t / 0.06))
        phase_a += 2 * math.pi * freq * bend / RATE
        phase_b += 2 * math.pi * freq * 2.76 * bend / RATE
        env = math.exp(-t / decay)
        out[i] = (math.sin(phase_a) + 0.35 * math.sin(phase_b)) * env
    return highpass(out, 300)


def _scatter(seconds: float, seed: int, start: float, count: int, spread: float, level: float = 1.0) -> list[float]:
    """Small hard bits landing round about: little wooden ticks, quieter and further apart as they go."""
    n = int(seconds * RATE)
    rng = random.Random(seed)
    times = sorted(start + spread * rng.random() ** 1.6 for _ in range(count))
    levels = [level * (0.9 - 0.6 * (t - start) / spread) * (0.6 + 0.4 * rng.random()) for t in times]
    return mul(bandpass(noise(seconds, seed + 1), 2300, 1.4), bursts(n, times, 0.0018, levels=levels))


def _ratchet(seconds: float, seed: int, end: float, clicks: int) -> list[float]:
    """A clockwork ratchet wound past its limit: clicks coming faster and faster, up to {end}."""
    n = int(seconds * RATE)
    times = [end - 0.11 * (1 - (k / clicks)) ** 1.7 for k in range(clicks)]
    levels = [0.35 + 0.5 * k / clicks for k in range(clicks)]
    return mul(highpass(bandpass(noise(seconds, seed), 3200, 2.0), 1600), bursts(n, times, 0.0012, levels=levels))


def spring_pop() -> list[float]:
    """The pop and knock, a spring twanging loose right after it, and the bits scattering."""
    seconds = 0.7
    pop = _pop(seconds, 101)
    spring = _spring(seconds, 610, 0.008, 0.16)
    bits = _scatter(seconds, 103, 0.05, 7, 0.32)
    return finish(room(mix((1.0, pop), (0.28, spring), (0.45, bits)), 0.7, 0.12))


def ratchet_pop() -> list[float]:
    """Wound too far: a quick ratchet running away, then the pop and a short twang."""
    seconds = 0.8
    lead = 0.12
    clicks = _ratchet(seconds, 107, lead - 0.004, 9)
    pop = [0.0] * int(lead * RATE) + _pop(seconds - lead, 109)
    spring = _spring(seconds, 700, lead + 0.006, 0.09)
    bits = _scatter(seconds, 111, lead + 0.05, 5, 0.25)
    return finish(room(mix((0.6, clicks), (1.0, pop), (0.22, spring), (0.4, bits)), 0.7, 0.12))


def music_box() -> list[float]:
    """A toy's music box springing apart: the pop, then its comb's teeth struck all at once, a bright detuned jangle dying fast."""
    seconds = 0.75
    pop = _pop(seconds, 113, 1300)
    teeth = mix(*[(0.5, tone(seconds, f, 0.12 + 0.03 * k, start=0.006 + 0.004 * k)) for k, f in enumerate((1568, 1975, 2349, 2637, 3136))])
    bits = _scatter(seconds, 117, 0.06, 6, 0.3)
    return finish(room(mix((1.0, pop), (0.3, highpass(teeth, 900)), (0.4, bits)), 0.75, 0.14))


BURST_CANDIDATES = {"1_spring_pop": spring_pop, "2_ratchet_pop": ratchet_pop, "3_music_box": music_box}


# ---------------------------------------------------------------- the thump under it (DESIGN §3.23's hit sounds)
# A deep, heavy thump played under the music box, so the creepy toy actually hurts. Three versions in run/sounds_preview/impact/
# (THUMP_CANDIDATES); the first ships as curio_thump.

def thump_box() -> list[float]:
    """A heavy box dropped on a hollow floor: a 58 Hz drop, a woody knock at 150 Hz, a muffled puff."""
    s = 0.7
    n = int(s * RATE)
    low = tone(s, 58, 0.16, sweep=0.4)
    knock = tone(s, 150, 0.045, sweep=0.2)
    puff = mul(lowpass(noise(s, 131), 320), bursts(n, [0.0], 0.05))
    return impact.deep_finish(room(mix((1.0, low), (0.45, knock), (0.6, puff)), 0.7, 0.12))


def thump_hollow() -> list[float]:
    """Hollower: the knock rings a little in the toy's empty body, the low end shorter."""
    s = 0.8
    low = tone(s, 64, 0.12, sweep=0.35)
    body = mix((0.6, tone(s, 212, 0.11)), (0.35, tone(s, 318, 0.08)))
    puff = mul(lowpass(noise(s, 141), 280), bursts(int(s * RATE), [0.0], 0.04))
    return impact.deep_finish(room(mix((1.0, low), (0.5, body), (0.5, puff)), 0.8, 0.16))


def thump_drum() -> list[float]:
    """A deep drum struck once: longer and rounder, a soft skin slap at the front."""
    s = 0.9
    low = tone(s, 50, 0.24, sweep=0.3)
    slap = mul(bandpass(noise(s, 151), 900, 0.9), bursts(int(s * RATE), [0.0], 0.008))
    return impact.deep_finish(room(mix((1.0, low), (0.3, slap), (0.25, tone(s, 100, 0.1))), 0.8, 0.14))


THUMP_CANDIDATES = {"curio_thump_1_box": thump_box, "curio_thump_2_hollow": thump_hollow, "curio_thump_3_drum": thump_drum}


def curio_burst() -> list[float]:
    return music_box()


SOUNDS = {"curio_burst": curio_burst, "curio_thump": thump_box}
