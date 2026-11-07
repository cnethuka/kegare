"""Writes mono OGG Vorbis through the system's libsndfile (ctypes; no Python packages needed)."""
from __future__ import annotations

import ctypes
import ctypes.util
from pathlib import Path

SF_FORMAT_OGG, SF_FORMAT_VORBIS, SFM_WRITE = 0x200000, 0x0060, 0x20
SFC_SET_VBR_ENCODING_QUALITY = 0x1300


class _Info(ctypes.Structure):
    _fields_ = [("frames", ctypes.c_int64), ("samplerate", ctypes.c_int), ("channels", ctypes.c_int),
                ("format", ctypes.c_int), ("sections", ctypes.c_int), ("seekable", ctypes.c_int)]


def _lib():
    name = ctypes.util.find_library("sndfile") or "libsndfile.so.1"
    lib = ctypes.CDLL(name)
    lib.sf_open.restype = ctypes.c_void_p
    lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(_Info)]
    lib.sf_writef_float.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int64]
    lib.sf_writef_float.restype = ctypes.c_int64
    lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
    lib.sf_close.argtypes = [ctypes.c_void_p]
    lib.sf_strerror.argtypes = [ctypes.c_void_p]
    lib.sf_strerror.restype = ctypes.c_char_p
    return lib


def write(path: Path, samples: list[float], rate: int, quality: float = 0.7) -> None:
    """Mono float samples in [-1, 1] -> an .ogg at {path}. quality is Vorbis VBR quality from 0 to 1."""
    lib = _lib()
    path.parent.mkdir(parents=True, exist_ok=True)
    info = _Info(0, rate, 1, SF_FORMAT_OGG | SF_FORMAT_VORBIS, 0, 0)
    handle = lib.sf_open(str(path).encode(), SFM_WRITE, ctypes.byref(info))
    if not handle:
        raise OSError(f"libsndfile can't write {path}: {lib.sf_strerror(None).decode()}")
    q = ctypes.c_double(quality)
    lib.sf_command(handle, SFC_SET_VBR_ENCODING_QUALITY, ctypes.byref(q), ctypes.sizeof(q))
    buf = (ctypes.c_float * len(samples))(*samples)
    written = lib.sf_writef_float(handle, buf, len(samples))
    lib.sf_close(handle)
    if written != len(samples):
        raise OSError(f"wrote {written} of {len(samples)} frames to {path}")


def channels(path: Path) -> int:
    """The channel count in an OGG Vorbis file's identification header."""
    data = path.read_bytes()
    i = data.index(b"\x01vorbis")
    return data[i + 11]
