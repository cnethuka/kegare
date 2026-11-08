"""The Impact System's hit sounds (DESIGN §3.23), played with vanilla's own hurt sound, never instead of it.

- punch: an empty hand. A heavy thud with a crack on top.
- blade: a sword. A sharp swish with a slice.
- heavy: an axe or a mace. A heavier thud with a crunch.
- boom: big hits of any kind, underneath the rest: deep, and played louder and lower as the tier grows.

Each has three versions (CANDIDATES, written to run/sounds_preview/impact/ with --preview); the first of each ships for now.
Curio Burst's thump is in puppet_manipulation.py. Everything is made here from noise and tones: no recordings.
"""
from __future__ import annotations

import math
import random

from synth import RATE, bandpass, bursts, fade_tail, highpass, lowpass, mix, mul, noise, normalize, room, tone, trim


def deep_finish(x: list[float], peak: float = 0.89) -> list[float]:
    """As synth.finish, but keeping the lows: its 120 Hz highpass would take a boom's body away. Only rumble below 30 Hz goes."""
    return fade_tail(normalize(trim(highpass(x, 30.0)), peak))


def _thud(seconds: float, freq: float, decay: float, sweep: float = 0.45, seed: int = 1, body: float = 0.6) -> list[float]:
    """A body taking a hit: a low sine that drops in pitch as it dies, and a puff of muffled noise for the flesh of it."""
    n = int(seconds * RATE)
    low = tone(seconds, freq, decay, sweep=sweep)
    flesh = mul(lowpass(noise(seconds, seed), 420), bursts(n, [0.0], decay * 0.45))
    return mix((1.0, low), (body, flesh))


def _crack(seconds: float, seed: int, centre: float, length: float = 0.004, level: float = 1.0) -> list[float]:
    """A crack: a few milliseconds of bright noise, starting hard."""
    n = int(seconds * RATE)
    return mul(highpass(bandpass(noise(seconds, seed), centre, 0.8), 700), bursts(n, [0.0], length, levels=[level]))


def _swish(seconds: float, seed: int, length: float, low: float, high: float) -> list[float]:
    """Air cut fast: noise swelling and falling away, its pitch rising through it (three bands, each a little later and higher)."""
    n = int(seconds * RATE)
    out = [0.0] * n
    for k, f in enumerate((low, math.sqrt(low * high), high)):
        band = bandpass(noise(seconds, seed + k), f, 2.2)
        peak = length * (0.35 + 0.22 * k)
        env = [0.0] * n
        for i in range(n):
            t = i / RATE
            if t < peak:
                env[i] = (t / peak) ** 2
            else:
                env[i] = math.exp(-(t - peak) / (length * 0.22))
        out = mix((1.0, out), (0.8, mul(band, env)))
    return out


def _slice(seconds: float, start: float, base: float, decay: float) -> list[float]:
    """Steel through: a bright, inharmonic ring (a blade's partials) dying fast."""
    partials = [(1.0, 1.0), (2.76, 0.6), (5.4, 0.35), (8.9, 0.2)]
    return mix(*[(g, tone(seconds, base * r, decay / (1 + 0.6 * k), start=start)) for k, (r, g) in enumerate(partials)])


def _crunch(seconds: float, seed: int, start: float, spread: float, count: int, centre: float) -> list[float]:
    """Something giving way: a fast scatter of tiny hard clicks, crowded at the front."""
    n = int(seconds * RATE)
    rng = random.Random(seed)
    times = sorted(start + spread * rng.random() ** 2.2 for _ in range(count))
    levels = [0.5 + 0.5 * rng.random() for _ in times]
    return mul(bandpass(noise(seconds, seed + 7), centre, 1.1), bursts(n, times, 0.0025, levels=levels))


def _rumble(seconds: float, seed: int, decay: float, cutoff: float = 140.0) -> list[float]:
    n = int(seconds * RATE)
    return mul(lowpass(lowpass(noise(seconds, seed), cutoff), cutoff), bursts(n, [0.0], decay))


# ---------------------------------------------------------------- punch

def punch_tight() -> list[float]:
    """A tight thud at about 95 Hz, a dry crack on top."""
    s = 0.35
    return deep_finish(room(mix((1.0, _thud(s, 95, 0.07, seed=11)), (0.95, _crack(s, 12, 2600, length=0.006))), 0.5, 0.08))


def punch_meaty() -> list[float]:
    """Lower and longer, more flesh in it; the crack softer, and a slap of skin."""
    s = 0.45
    slap = _crack(s, 22, 1500, length=0.012, level=0.7)
    return deep_finish(room(mix((1.0, _thud(s, 75, 0.1, seed=21, body=0.9)), (0.75, _crack(s, 23, 1900, length=0.006)), (0.5, slap)), 0.55, 0.1))


def punch_snappy() -> list[float]:
    """A short, hard knock with the crack brighter and up front."""
    s = 0.3
    return deep_finish(room(mix((0.8, _thud(s, 115, 0.05, seed=31)), (1.1, _crack(s, 32, 3400, length=0.004))), 0.45, 0.06))


# ---------------------------------------------------------------- blade

def blade_clean() -> list[float]:
    """A quick swish rising into a bright slice."""
    s = 0.45
    return deep_finish(room(mix((0.7, _swish(s, 41, 0.11, 900, 3600)), (0.35, _slice(s, 0.085, 1900, 0.09)),
                                (0.5, _crack(s, 42, 4200, length=0.003))), 0.6, 0.12))


def blade_heavy() -> list[float]:
    """A longer, lower swish and a deeper slice, with a little thud of the hit under it."""
    s = 0.55
    return deep_finish(room(mix((0.8, _swish(s, 51, 0.16, 600, 2600)), (0.35, _slice(s, 0.13, 1300, 0.12)),
                                (0.4, delay_to(_thud(s, 110, 0.05, seed=52), 0.13))), 0.6, 0.12))


def blade_whip() -> list[float]:
    """A short whip of air and a crisp tick of steel."""
    s = 0.35
    return deep_finish(room(mix((0.7, _swish(s, 61, 0.07, 1400, 5200)), (0.3, _slice(s, 0.05, 2600, 0.05)),
                                (0.6, delay_to(_crack(s, 62, 5000, length=0.002), 0.05))), 0.5, 0.1))


def delay_to(x: list[float], seconds: float) -> list[float]:
    """{x} starting {seconds} later, the same length."""
    pad = int(seconds * RATE)
    return ([0.0] * pad + x)[:len(x)]


# ---------------------------------------------------------------- heavy

def heavy_crunch() -> list[float]:
    """A heavier thud at about 70 Hz, a crunch spilling off it."""
    s = 0.55
    return deep_finish(room(mix((1.0, _thud(s, 70, 0.11, seed=71, body=0.8)), (1.1, _crunch(s, 72, 0.004, 0.07, 22, 1400)),
                                (0.6, _crack(s, 73, 2100, length=0.006))), 0.6, 0.12))


def heavy_bone() -> list[float]:
    """Lower still, a sharp crack and a shorter, harder crunch: something breaking under it."""
    s = 0.6
    return deep_finish(room(mix((1.0, _thud(s, 60, 0.13, seed=81, body=0.9)), (0.7, _crack(s, 82, 2400, length=0.006)),
                                (1.0, _crunch(s, 83, 0.01, 0.045, 16, 1900))), 0.6, 0.12))


def heavy_clang() -> list[float]:
    """The thud and crunch with a short dull clank of metal in it (a mace's head)."""
    s = 0.6
    clank = _slice(s, 0.002, 520, 0.08)
    return deep_finish(room(mix((1.0, _thud(s, 72, 0.1, seed=91)), (0.3, clank), (0.9, _crunch(s, 92, 0.006, 0.06, 18, 1200))), 0.6, 0.12))


# ---------------------------------------------------------------- boom

def boom_deep() -> list[float]:
    """Deep and round: a 48 Hz drop with a rumble under it, a soft knock at the front."""
    s = 1.1
    return deep_finish(room(mix((1.0, tone(s, 48, 0.32, sweep=0.35)), (0.6, _rumble(s, 101, 0.28)),
                                (0.35, _thud(s, 140, 0.03, seed=102))), 0.9, 0.18))


def boom_rumble() -> list[float]:
    """More rumble than tone: like something far bigger landing, rolling on longer."""
    s = 1.4
    return deep_finish(room(mix((0.7, tone(s, 42, 0.4, sweep=0.3)), (1.0, _rumble(s, 111, 0.45, 110)),
                                (0.3, _thud(s, 120, 0.04, seed=112))), 1.0, 0.22))


def boom_punchy() -> list[float]:
    """Shorter, with a hard click to start it: it hits rather than rolls."""
    s = 0.8
    return deep_finish(room(mix((1.0, tone(s, 55, 0.2, sweep=0.5)), (0.5, _rumble(s, 121, 0.16)),
                                (0.5, _crack(s, 122, 1800, length=0.003))), 0.8, 0.14))


CANDIDATES = {
    "punch_1_tight": punch_tight, "punch_2_meaty": punch_meaty, "punch_3_snappy": punch_snappy,
    "blade_1_clean": blade_clean, "blade_2_heavy": blade_heavy, "blade_3_whip": blade_whip,
    "heavy_1_crunch": heavy_crunch, "heavy_2_bone": heavy_bone, "heavy_3_clang": heavy_clang,
    "boom_1_deep": boom_deep, "boom_2_rumble": boom_rumble, "boom_3_punchy": boom_punchy,
}

# The first of each ships.
SOUNDS = {"punch": punch_tight, "blade": blade_clean, "heavy": heavy_crunch, "boom": boom_deep}
