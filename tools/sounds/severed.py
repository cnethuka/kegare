"""Severed's sounds (DESIGN §3.27): a body, nothing else. Breath, air torn past, grit and gravel kicked up, a heel planted and a
body slammed into stone. No hum, no shimmer, nothing that could be Malice.

SOUNDS maps the file each one ships as (assets/kegare/sounds/severed/<name>.ogg) to how it's made. CANDIDATES are the versions of
Blur's start, Closing Line's launch and the wall crash made for choosing (run/sounds_preview/severed/, with --preview), three of each;
the first of each ships.
"""
from __future__ import annotations

import math
import random

from impact import _crack, _crunch, _rumble, _swish, _thud, deep_finish
from synth import RATE, bandpass, bursts, delay, fade_tail, finish, highpass, lowpass, mix, mul, noise, normalize, room, tone, trim


def _breath(seconds: float, seed: int, start: float, length: float, centre: float = 1100.0, level: float = 1.0) -> list[float]:
    """A breath pushed out: soft-edged noise round {centre}, swelling fast and falling away over {length}."""
    n = int(seconds * RATE)
    band = lowpass(bandpass(noise(seconds, seed), centre, 0.7), centre * 2.5)
    env = [0.0] * n
    s = int(start * RATE)
    for i in range(s, n):
        t = (i - s) / RATE
        env[i] = level * (min(1.0, t / (length * 0.18)) * math.exp(-max(0.0, t - length * 0.18) / (length * 0.35)))
    return mul(band, env)


def _grit(seconds: float, seed: int, start: float, length: float, density: int, centre: float = 2600.0) -> list[float]:
    """Gravel and grit: a scatter of small hard grains over {length}, thinning out."""
    n = int(seconds * RATE)
    rng = random.Random(seed)
    times = sorted(start + length * rng.random() ** 1.6 for _ in range(density))
    levels = [0.3 + 0.7 * rng.random() for _ in times]
    return mul(bandpass(noise(seconds, seed + 3), centre, 0.9), bursts(n, times, 0.0018, levels=levels))


def _wind(seconds: float, seed: int, attack: float, release: float, low: float = 380.0, high: float = 1400.0) -> list[float]:
    """Air rushing past: two bands of noise wavering slowly, in over {attack} and out over {release}."""
    n = int(seconds * RATE)
    a = bandpass(noise(seconds, seed), low, 1.2)
    b = bandpass(noise(seconds, seed + 1), high, 1.6)
    out = [0.0] * n
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * min(1.0, (seconds - t) / release)
        wobble = 0.75 + 0.25 * math.sin(2 * math.pi * 3.1 * t + 1.3)
        out[i] = env * (a[i] * wobble + 0.55 * b[i] * (1.6 - wobble))
    return out


# ---------------------------------------------------------------- blur_on

def blur_on_breath() -> list[float]:
    """1: breath. A short, hard breath out through the teeth, and the air starting to tear past behind it."""
    s = 0.8
    return finish(room(mix((1.0, _breath(s, 11, 0.0, 0.3, 1300)), (0.55, delay(_wind(0.6, 13, 0.25, 0.3), 0.12)),
                       (0.35, _grit(s, 15, 0.08, 0.3, 26))), 0.6, 0.08))


def blur_on_snap() -> list[float]:
    """2: snap. A quicker exhale with a sharp scuff of a foot setting, drier."""
    s = 0.6
    scuff = mul(bandpass(noise(s, 21), 1800, 1.0), bursts(int(s * RATE), [0.05], 0.03))
    return finish(room(mix((0.9, _breath(s, 23, 0.0, 0.2, 1500)), (0.8, scuff), (0.4, _grit(s, 25, 0.05, 0.15, 18))), 0.5, 0.06))


def blur_on_low() -> list[float]:
    """3: low. A deeper breath and a low gust of air, heavier and slower."""
    s = 1.0
    return finish(room(mix((0.8, _breath(s, 31, 0.0, 0.45, 800)), (0.8, delay(_wind(0.8, 33, 0.35, 0.4, 260, 900), 0.08))), 0.8, 0.12))


# ---------------------------------------------------------------- closing_line_launch

def launch_crack() -> list[float]:
    """1: crack. A heel planted hard (a deep thump), the ground cracking under it, gravel sprayed and the air torn open."""
    s = 1.0
    thump = _thud(s, 70, 0.11, sweep=0.5, seed=41, body=0.7)
    crack = _crack(s, 43, 2200, length=0.006, level=1.0)
    grit = _grit(s, 45, 0.01, 0.35, 70)
    air = delay(_swish(0.6, 47, 0.32, 500, 2600), 0.03)
    return deep_finish(room(mix((1.0, thump), (0.8, crack), (0.45, grit), (0.6, air)), 0.7, 0.1), 0.95)


def launch_gravel() -> list[float]:
    """2: gravel. Less crack, more of the ground thrown up: a long spray of grit over the thump."""
    s = 1.1
    thump = _thud(s, 62, 0.13, sweep=0.45, seed=51, body=0.8)
    grit = mix((1.0, _grit(s, 53, 0.0, 0.6, 140, 2200)), (0.6, _crunch(s, 55, 0.0, 0.08, 30, 1500)))
    air = delay(_swish(0.6, 57, 0.28, 400, 2000), 0.05)
    return deep_finish(room(mix((1.0, thump), (0.55, grit), (0.5, air)), 0.8, 0.12), 0.95)


def launch_boom() -> list[float]:
    """3: boom. The air does most of it: a low boom and a hard rush past, the step under it."""
    s = 1.0
    boom = mix((1.0, _thud(s, 48, 0.18, sweep=0.35, seed=61, body=0.5)), (0.7, _rumble(s, 63, 0.25)))
    air = _swish(0.7, 65, 0.4, 300, 2400)
    return deep_finish(room(mix((1.0, boom), (0.9, air), (0.3, _grit(s, 67, 0.0, 0.2, 30))), 0.9, 0.14), 0.95)


# ---------------------------------------------------------------- wall_crash

def crash_thud() -> list[float]:
    """1: thud. A body slammed into stone: a heavy, dull thud, stone crunching and grit falling after."""
    s = 1.0
    body = _thud(s, 85, 0.09, sweep=0.55, seed=71, body=0.9)
    crunch = _crunch(s, 73, 0.0, 0.06, 40, 1700)
    fall = delay(_grit(0.6, 75, 0.0, 0.4, 40, 2400), 0.1)
    return deep_finish(room(mix((1.0, body), (0.75, crunch), (0.35, fall)), 0.7, 0.1), 0.95)


def crash_slam() -> list[float]:
    """2: slam. Harder and shorter: a crack of impact on top of the thud, little after."""
    s = 0.7
    body = _thud(s, 95, 0.07, sweep=0.5, seed=81, body=0.8)
    crack = _crack(s, 83, 1600, length=0.008)
    return deep_finish(room(mix((1.0, body), (0.9, crack), (0.4, _crunch(s, 85, 0.0, 0.03, 20, 2000))), 0.5, 0.07), 0.95)


def crash_crumble() -> list[float]:
    """3: crumble. The wall taking it: a thud and a longer crumble of stone pieces settling."""
    s = 1.3
    body = _thud(s, 75, 0.1, sweep=0.5, seed=91, body=0.8)
    crumble = mix((1.0, _crunch(s, 93, 0.02, 0.5, 90, 1300)), (0.6, _grit(s, 95, 0.1, 0.8, 60, 2000)))
    return deep_finish(room(mix((1.0, body), (0.7, crumble), (0.4, _rumble(s, 97, 0.2))), 0.9, 0.12), 0.95)


CANDIDATES = {
    "blur_on_1_breath": blur_on_breath, "blur_on_2_snap": blur_on_snap, "blur_on_3_low": blur_on_low,
    "closing_line_launch_1_crack": launch_crack, "closing_line_launch_2_gravel": launch_gravel, "closing_line_launch_3_boom": launch_boom,
    "wall_crash_1_thud": crash_thud, "wall_crash_2_slam": crash_slam, "wall_crash_3_crumble": crash_crumble,
}


# ---------------------------------------------------------------- the rest

def blur_wind() -> list[float]:
    """Blur running: half a second of air tearing past, soft at both ends, so it can play over itself while they run."""
    return finish(_wind(0.7, 101, 0.15, 0.25), 0.7)


def closing_line_arrive() -> list[float]:
    """Arriving: a hard skid, soles grinding over the ground, grit thrown ahead, slowing to a stop."""
    s = 0.7
    n = int(s * RATE)
    scrape = bandpass(noise(s, 111), 1800, 0.8)
    env = [math.exp(-(i / RATE) / 0.18) * (0.7 + 0.3 * math.sin(2 * math.pi * 23 * i / RATE)) for i in range(n)]
    return finish(room(mix((1.0, mul(scrape, env)), (0.6, _grit(s, 113, 0.0, 0.4, 60)), (0.5, _thud(s, 110, 0.05, seed=115, body=0.3))),
                       0.5, 0.06))


def winded() -> list[float]:
    """Winded: three ragged breaths, in and out, each a little weaker."""
    s = 2.0
    parts = []
    for k, (at, level) in enumerate(((0.0, 1.0), (0.55, 0.8), (1.1, 0.6))):
        parts.append((level, _breath(s, 121 + k, at, 0.35, 900 + 100 * k)))
        parts.append((level * 0.6, _breath(s, 131 + k, at + 0.22, 0.25, 1400)))
    return finish(room(mix(*parts), 0.5, 0.06), 0.8)


SOUNDS = {
    "blur_on": blur_on_breath, "closing_line_launch": launch_crack, "wall_crash": crash_thud,
    "blur_wind": blur_wind, "closing_line_arrive": closing_line_arrive, "winded": winded,
}
