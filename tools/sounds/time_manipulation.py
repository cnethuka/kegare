"""Time Manipulation's sounds (DESIGN §3.26): a pocket watch, made from nothing. A watch's tick is a click a few milliseconds
long (the escapement letting go), band-limited high, over a short ring of small metal parts (a few inharmonic partials dying in
milliseconds) and a faint low knock of the case. The rest are built from that: wound (a ratchet of small clicks), played backward
(the ring swelling into the click), run together (a whirr), slowed (the ring drawn out like glass), stopped and started again.

SOUNDS maps the file each one ships as (assets/kegare/sounds/time_manipulation/<name>.ogg) to how it's made. CANDIDATES are the
versions of the four main sounds made for choosing (run/sounds_preview/time_manipulation/, with --preview): click, tick, stop and
resume, three of each; the first of each ships.
"""
from __future__ import annotations

import math

from synth import RATE, bandpass, bursts, delay, finish, highpass, lowpass, mix, mul, noise, room, tone, trim, fade_tail, normalize


def _metal(seconds: float, partials: list[tuple[float, float, float]], start: float = 0.0) -> list[float]:
    """Small metal parts ringing: (frequency, decay, level) sines, all struck at {start}."""
    return mix(*[(level, tone(seconds, f, decay, start=start)) for f, decay, level in partials])


def _click(seconds: float, seed: int, centre: float, length: float = 0.0018, q: float = 1.4, start: float = 0.0) -> list[float]:
    """One click: a burst of noise {length} long, band-passed round {centre}."""
    n = int(seconds * RATE)
    env = bursts(n, [start], length)
    return mul(lowpass(bandpass(noise(seconds, seed), centre, q), centre * 2.2), env)


def _tick(seconds: float, seed: int, centre: float = 4200.0, ring: float = 1.0, knock: float = 0.35, pitch: float = 1.0,
          start: float = 0.0, ring_decay: float = 1.0) -> list[float]:
    """A watch tick: the escapement's click, its ring and the case's knock."""
    c = _click(seconds, seed, centre * pitch, start=start)
    r = _metal(seconds, [(3150 * pitch, 0.006 * ring_decay, 0.5), (5270 * pitch, 0.004 * ring_decay, 0.35),
                         (7930 * pitch, 0.0025 * ring_decay, 0.25)], start=start)
    k = tone(seconds, 880 * pitch, 0.004, start=start, sweep=0.2)
    return mix((1.0, c), (0.55 * ring, r), (knock, k))


def _reverse(x: list[float]) -> list[float]:
    return list(reversed(x))


# ---------------------------------------------------------------- click (Point Maker, Decelerate's strike)

def click_lid() -> list[float]:
    """1: lid. A watch case snapping shut: a latch click then the snap 9 ms later, bright, with a short ring. Crisp and dry."""
    s = 0.3
    a = _click(s, 101, 3600, 0.0012)
    b = _click(s, 102, 2600, 0.0022, start=0.009)
    ring = _metal(s, [(2470, 0.012, 0.45), (5310, 0.008, 0.35), (8890, 0.004, 0.2)], start=0.009)
    body = tone(s, 640, 0.006, start=0.009, sweep=0.25)
    return finish(room(mix((0.6, a), (1.0, b), (0.6, ring), (0.4, body)), 0.5, 0.08))


def click_latch() -> list[float]:
    """2: latch. One single, very short, very high click and almost nothing after it: the driest."""
    s = 0.2
    a = _click(s, 111, 5200, 0.0009, q=1.0)
    ring = _metal(s, [(6120, 0.005, 0.4), (9370, 0.003, 0.25)])
    return finish(room(mix((1.0, a), (0.5, ring)), 0.4, 0.05))


def click_crown() -> list[float]:
    """3: crown. A crown pushed home: a lower, rounder double click, more case than bell."""
    s = 0.3
    a = _click(s, 121, 2200, 0.0018)
    b = _click(s, 122, 1900, 0.0025, start=0.006)
    body = tone(s, 520, 0.01, start=0.006, sweep=0.2)
    ring = _metal(s, [(2890, 0.01, 0.3), (4710, 0.006, 0.2)], start=0.006)
    return finish(room(mix((0.7, a), (1.0, b), (0.55, body), (0.5, ring)), 0.6, 0.1))


# ---------------------------------------------------------------- tick (Accelerate, Decelerate's hit, Stasis catching)

def tick_escapement() -> list[float]:
    """1: escapement. A clean pocket-watch tick: high click, a thin ring, a faint knock."""
    return finish(room(_tick(0.25, 201), 0.5, 0.07))


def tick_bright() -> list[float]:
    """2: bright. Higher and glassier, the ring a little longer: more jewel than steel."""
    return finish(room(_tick(0.3, 211, centre=5600, ring=1.4, knock=0.15, pitch=1.25, ring_decay=1.8), 0.5, 0.08))


def tick_case() -> list[float]:
    """3: case. Lower and woodier, a mantel clock's tick: more knock, less ring."""
    return finish(room(_tick(0.3, 221, centre=2600, ring=0.5, knock=0.8, pitch=0.7), 0.7, 0.12))


# ---------------------------------------------------------------- stop (Standstill)

def stop_snap() -> list[float]:
    """1: snap. The sharpest click there is, with a low weight under it, and then nothing: the ring is cut dead after 60 ms, as if
    the sound itself stopped."""
    s = 0.5
    a = _click(s, 301, 4800, 0.0011, q=0.9)
    b = _click(s, 302, 3000, 0.002, start=0.004)
    ring = _metal(s, [(2210, 0.05, 0.5), (3870, 0.04, 0.4), (6650, 0.02, 0.3), (10100, 0.01, 0.2)], start=0.004)
    sub = tone(s, 110, 0.05, start=0.004, sweep=0.3)
    x = mix((0.8, a), (1.0, b), (0.7, ring), (0.7, sub))
    cut = int(0.06 * RATE)
    x = [v if i < cut else v * math.exp(-(i - cut) / (0.002 * RATE)) for i, v in enumerate(x)]
    return fade_tail(normalize(trim(highpass(x, 60.0)), 0.95))


def stop_drop() -> list[float]:
    """2: drop. The click, then its ring falls an octave in a breath and stops, a mechanism running down."""
    s = 0.6
    a = _click(s, 311, 4200, 0.0012)
    n = int(s * RATE)
    phase = 0.0
    fall = [0.0] * n
    for i in range(n):
        t = i / RATE
        f = 2600 * (0.5 + 0.5 * math.exp(-t / 0.06))
        phase += 2 * math.pi * f / RATE
        fall[i] = math.sin(phase) * math.exp(-t / 0.08)
    sub = tone(s, 95, 0.08, sweep=0.4)
    return fade_tail(normalize(trim(highpass(mix((1.0, a), (0.45, fall), (0.6, sub)), 60.0)), 0.95))


def stop_bell() -> list[float]:
    """3: bell. The click with one cold, high bell over it, left to ring into the silence."""
    s = 1.2
    a = _click(s, 321, 4600, 0.0011)
    bell = _metal(s, [(1760, 0.5, 0.5), (4310, 0.3, 0.35), (7090, 0.15, 0.2)], start=0.002)
    sub = tone(s, 120, 0.04, sweep=0.3)
    return finish(room(mix((1.0, a), (0.5, bell), (0.5, sub)), 1.0, 0.2), 0.92)


# ---------------------------------------------------------------- resume (Standstill)

def resume_tock() -> list[float]:
    """1: tock. One loud tick with a heavy case under it and a room: the world's clock catching again."""
    s = 0.8
    t = _tick(s, 401, centre=3400, ring=1.2, knock=0.0, pitch=0.85, ring_decay=3.0)
    body = tone(s, 180, 0.06, sweep=0.35)
    thump = mul(lowpass(noise(s, 402), 260), bursts(int(s * RATE), [0.0], 0.03))
    return finish(room(mix((1.0, t), (0.9, body), (0.6, thump)), 1.1, 0.25), 0.95)


def resume_crack() -> list[float]:
    """2: crack. Brighter and harder: a tick like a snapped twig, with a quick bright tail."""
    s = 0.6
    a = _click(s, 411, 5200, 0.0016, q=0.8)
    ring = _metal(s, [(2810, 0.03, 0.5), (5930, 0.02, 0.35), (9110, 0.01, 0.25)])
    body = tone(s, 240, 0.03, sweep=0.3)
    return finish(room(mix((1.0, a), (0.6, ring), (0.6, body)), 0.9, 0.2), 0.95)


def resume_rush() -> list[float]:
    """3: rush. The world's sound swelling back for a quarter second into the tick that lets it go."""
    s = 0.9
    swell = lowpass(highpass(noise(s, 421), 400), 3000)
    n = int(s * RATE)
    at = int(0.25 * RATE)
    env = [((i / at) ** 3 if i < at else math.exp(-(i - at) / (0.05 * RATE))) for i in range(n)]
    t = delay(_tick(0.6, 422, centre=3400, ring=1.2, knock=0.6, pitch=0.85, ring_decay=3.0), 0.25)
    return finish(room(mix((0.35, mul(swell, env)), (1.0, t)), 1.0, 0.22), 0.95)


CANDIDATES = {
    "click_1_lid": click_lid, "click_2_latch": click_latch, "click_3_crown": click_crown,
    "tick_1_escapement": tick_escapement, "tick_2_bright": tick_bright, "tick_3_case": tick_case,
    "stop_1_snap": stop_snap, "stop_2_drop": stop_drop, "stop_3_bell": stop_bell,
    "resume_1_tock": resume_tock, "resume_2_crack": resume_crack, "resume_3_rush": resume_rush,
}


# ---------------------------------------------------------------- the rest

def wind() -> list[float]:
    """Accelerate on: a crown wound, a ratchet of small clicks quickening and rising, then a firm click."""
    s = 0.6
    times, t, gap = [], 0.0, 0.045
    while t < 0.38:
        times.append(t)
        t += gap
        gap = max(0.014, gap * 0.86)
    layers = [(0.45 + 0.4 * i / len(times), _click(s, 500 + i, 3000 + 140 * i, 0.0012, start=at)) for i, at in enumerate(times)]
    last = _tick(s, 520, centre=4400, ring=1.1, start=times[-1] + 0.05)
    return finish(room(mix(*layers, (1.0, last)), 0.5, 0.08))


def reverse_tick() -> list[float]:
    """Rewind: a tick played backward, its ring and room swelling up into the click that cuts it off, then a softer one after."""
    one = _reverse(room(_tick(0.45, 601, ring=1.4, ring_decay=6.0), 0.9, 0.35))
    two = _reverse(room(_tick(0.35, 602, centre=3600, ring=1.2, ring_decay=5.0, pitch=0.9), 0.7, 0.3))
    return finish(mix((1.0, one), (0.55, delay(two, 0.42))))


def whirl() -> list[float]:
    """Fast Forward: ticks running together faster and faster into a gear's whirr, rising, then gone."""
    s = 0.85
    times, t, gap = [], 0.0, 0.08
    while t < 0.62:
        times.append(t)
        t += gap
        gap = max(0.012, gap * 0.82)
    layers = [(0.5, _click(s, 700 + i, 3400 + 60 * i, 0.001, start=at)) for i, at in enumerate(times)]
    n = int(s * RATE)
    buzz = bandpass(noise(s, 720), 2200, 2.0)
    env = [min(1.0, (i / (0.55 * RATE)) ** 2) * (1.0 if i < 0.62 * RATE else math.exp(-(i - 0.62 * RATE) / (0.04 * RATE))) for i in range(n)]
    return finish(room(mix(*layers, (0.35, mul(buzz, env))), 0.6, 0.1))


def throw() -> list[float]:
    """Decelerate: a thin blade thrown, a short bright swish rising past you, with a tick at its start."""
    s = 0.4
    n = int(s * RATE)
    raw = noise(s, 801)
    hi = highpass(bandpass(raw, 3600, 1.6), 1800)
    env = [math.sin(math.pi * min(1.0, i / (0.22 * RATE))) ** 2 if i < 0.22 * RATE else 0.0 for i in range(n)]
    tick = _tick(s, 802, centre=5000, ring=0.8, knock=0.1, pitch=1.15)
    return finish(room(mix((0.7, mul(hi, env)), (0.6, tick)), 0.5, 0.06))


def stasis() -> list[float]:
    """Stasis Field: a tick slowed down: pitched low, its ring drawn out like glass, wavering slowly as it fades."""
    s = 1.6
    t = _tick(s, 901, centre=1800, ring=1.6, knock=0.5, pitch=0.5, ring_decay=60.0)
    glass = _metal(s, [(1240, 0.7, 0.4), (2930, 0.45, 0.3), (4460, 0.25, 0.15)])
    wobble = [0.75 + 0.25 * math.cos(2 * math.pi * 3.2 * i / RATE) for i in range(int(s * RATE))]
    return finish(room(mix((1.0, t), (0.6, mul(glass, wobble))), 1.0, 0.22))


def stop_tick() -> list[float]:
    """Standstill's slow tick, the one sound while it stands: a low, soft tick in a large, empty room."""
    return finish(room(_tick(0.9, 1001, centre=2400, ring=1.3, knock=0.6, pitch=0.6, ring_decay=4.0), 1.6, 0.35, 2500), 0.7)


SOUNDS = {
    "click": click_lid, "tick": tick_escapement, "stop": stop_snap, "resume": resume_tock,
    "wind": wind, "reverse_tick": reverse_tick, "whirl": whirl, "throw": throw, "stasis": stasis, "stop_tick": stop_tick,
}
