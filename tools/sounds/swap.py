"""Swap's sounds (DESIGN §3.19): the clap and its relatives. A hand clap is a few sharp bursts of noise a few milliseconds
apart (the palms meeting unevenly), band-limited around 1-2 kHz, then a short decay; a cupped clap adds a hollow tone.

SOUNDS maps the file each one ships as (assets/kegare/sounds/swap/<name>.ogg) to how it's made. CLAP_CANDIDATES are the
versions of the clap made for choosing (run/sounds_preview/clap/, with --preview); the first is the one that ships as clap.
"""
from __future__ import annotations

from synth import RATE, bandpass, bursts, delay, finish, highpass, lowpass, mix, mul, noise, room, tone


def _clap_body(seed: int, times: list[float], centre: float, q: float, decay: float, tail: float, tail_level: float,
               air: float = 0.25, seconds: float = 0.4) -> list[float]:
    n = int(seconds * RATE)
    env = bursts(n, times, decay, tail_at=times[-1] + 0.002, tail_decay=tail, tail_level=tail_level)
    raw = noise(seconds, seed)
    body = lowpass(bandpass(raw, centre, q), centre * 3.0)
    bright = lowpass(highpass(bandpass(raw, centre * 2.6, 0.9), 2500), 7000)
    return mul(mix((1.0, body), (air, bright)), env)


def crisp(seed: int = 11) -> list[float]:
    """1: crisp. Three bursts 7 and 8 ms apart around 1.6 kHz with a little air, a short decay, a small dry room."""
    x = _clap_body(seed, [0.0, 0.007, 0.015], 1600, 1.1, 0.0032, 0.042, 0.55)
    return finish(room(x, 0.8, 0.12))


def studio(seed: int = 23) -> list[float]:
    """2: studio. Four even bursts 10 ms apart around 1.1 kHz, a longer reverberant tail (a drum machine's clap)."""
    x = _clap_body(seed, [0.0, 0.010, 0.020, 0.030], 1100, 1.8, 0.004, 0.09, 0.6, air=0.15)
    return finish(room(x, 1.2, 0.28, 3000))


def full(seed: int = 37) -> list[float]:
    """3: full. Two bursts, lower (900 Hz) with the palms' low knock under it: rounder, heavier."""
    x = _clap_body(seed, [0.0, 0.006], 900, 1.0, 0.004, 0.05, 0.5, air=0.18)
    knock = tone(0.4, 210, 0.014, sweep=0.25)
    return finish(room(mix((1.0, x), (0.55, knock)), 0.9, 0.14))


def slap(seed: int = 41) -> list[float]:
    """4: slap. One very sharp burst, bright (2.4 kHz), almost no tail: the driest, snappiest."""
    x = _clap_body(seed, [0.0, 0.003], 2400, 0.9, 0.0025, 0.022, 0.4, air=0.35, seconds=0.25)
    return finish(room(x, 0.6, 0.08))


CLAP_CANDIDATES = {"1_crisp": crisp, "2_studio": studio, "3_full": full, "4_slap": slap}


def clap() -> list[float]:
    """The clap every swap makes: candidate 1."""
    return crisp()


def double_clap() -> list[float]:
    """Switchback: the clap twice, 0.13 s apart, the second a touch brighter and louder."""
    a = _clap_body(11, [0.0, 0.007, 0.015], 1600, 1.1, 0.0032, 0.042, 0.55, seconds=0.35)
    b = _clap_body(12, [0.0, 0.006, 0.013], 1800, 1.1, 0.003, 0.045, 0.55, air=0.32, seconds=0.35)
    return finish(room(mix((0.85, a), (1.0, delay(b, 0.13))), 0.8, 0.12))


def snap() -> list[float]:
    """The swap back of a Switchback: the same clap's single sharpest burst, higher, a click that answers the double clap."""
    x = _clap_body(13, [0.0, 0.004], 2200, 1.0, 0.0024, 0.03, 0.45, air=0.4, seconds=0.25)
    tick = tone(0.25, 3100, 0.004)
    return finish(room(mix((1.0, x), (0.25, tick)), 0.7, 0.1))


def cupped_clap() -> list[float]:
    """Mimic: hands cupped, so a hollow pop: low (700 Hz) and narrow, with the cupped air ringing a moment."""
    x = _clap_body(17, [0.0, 0.005], 700, 2.6, 0.004, 0.035, 0.45, air=0.08, seconds=0.35)
    hollow = tone(0.35, 640, 0.03, sweep=0.12)
    return finish(room(mix((1.0, x), (0.7, hollow)), 0.8, 0.12))


def big_clap() -> list[float]:
    """Total Swap: three claps landing together (a few ms apart, each its own pitch), a deep knock and a long room."""
    a = _clap_body(19, [0.0, 0.007, 0.015], 1500, 1.0, 0.004, 0.07, 0.6, seconds=0.7)
    b = _clap_body(29, [0.0, 0.008, 0.016], 1150, 1.2, 0.004, 0.08, 0.6, seconds=0.7)
    c = _clap_body(31, [0.0, 0.006], 1950, 1.0, 0.0035, 0.06, 0.5, air=0.4, seconds=0.7)
    knock = lowpass(tone(0.7, 95, 0.05, sweep=0.35), 400)
    return finish(room(mix((1.0, a), (0.9, delay(b, 0.006)), (0.7, delay(c, 0.011)), (0.9, knock)), 1.5, 0.3, 2800))


def flip() -> list[float]:
    """Awareness's card flip: six tiny clicks over 0.12 s, like a card riffled under the thumb."""
    seconds = 0.3
    n = int(seconds * RATE)
    times = [0.0, 0.022, 0.041, 0.062, 0.08, 0.1]
    env = bursts(n, times, 0.0018, levels=[0.7, 0.8, 0.85, 0.9, 1.0, 0.75])
    x = mul(highpass(bandpass(noise(seconds, 43), 3600, 1.4), 1800), env)
    return finish(room(x, 0.5, 0.08), 0.7)


def flick() -> list[float]:
    """Anchor's throw: a thumb flicking a small token, a dry click, and the token ringing as it spins away (two bright inharmonic
    partials, a little detuned so they beat, a quarter second)."""
    seconds = 0.4
    n = int(seconds * RATE)
    click = mul(highpass(bandpass(noise(seconds, 47), 2800, 1.6), 1500), bursts(n, [0.0, 0.003], 0.0016))
    ring = mix((1.0, tone(seconds, 3150, 0.09, start=0.004)), (0.6, tone(seconds, 4880, 0.06, start=0.004)),
               (0.45, tone(seconds, 3195, 0.08, start=0.004)))
    return finish(room(mix((0.9, click), (0.32, ring)), 0.6, 0.1), 0.75)


def token_land() -> list[float]:
    """The token sticking where it lands: a small dull tick, and a short low chime that says it's waiting."""
    seconds = 0.45
    n = int(seconds * RATE)
    tick = mul(lowpass(bandpass(noise(seconds, 53), 1300, 1.2), 3000), bursts(n, [0.0], 0.0022))
    chime = mix((1.0, tone(seconds, 1870, 0.12, start=0.006)), (0.4, tone(seconds, 2805, 0.07, start=0.006)))
    return finish(room(mix((1.0, tick), (0.35, chime)), 0.7, 0.12), 0.7)


def shove() -> list[float]:
    """Send Off: the clap, then both palms pushed out, a short low rush of air swelling and cut off, as if something was sent away."""
    seconds = 0.5
    n = int(seconds * RATE)
    c = _clap_body(59, [0.0, 0.007, 0.014], 1450, 1.1, 0.0032, 0.04, 0.5, seconds=seconds)
    swell = [0.0] * n
    start, peak, end = int(0.07 * RATE), int(0.16 * RATE), int(0.24 * RATE)
    for i in range(start, end):
        swell[i] = (i - start) / (peak - start) if i < peak else max(0.0, 1 - (i - peak) / (end - peak)) ** 1.5
    push = mul(lowpass(bandpass(noise(seconds, 61), 520, 0.9), 1600), swell)
    return finish(room(mix((1.0, c), (0.75, push)), 0.8, 0.12))


SOUNDS = {"clap": clap, "double_clap": double_clap, "snap": snap, "cupped_clap": cupped_clap, "big_clap": big_clap, "flip": flip,
          "flick": flick, "token_land": token_land, "shove": shove}
