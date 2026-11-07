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
    _fix_serial(path)


def _crc(data: bytes) -> int:
    """Ogg's page checksum: CRC-32, polynomial 0x04c11db7, not reflected, starting from 0."""
    crc = 0
    for b in data:
        crc ^= b << 24
        for _ in range(8):
            crc = ((crc << 1) ^ 0x04C11DB7) if crc & 0x80000000 else crc << 1
            crc &= 0xFFFFFFFF
    return crc


def _fix_serial(path: Path) -> None:
    """libsndfile gives every stream a random serial number, so the same sound came out different bytes each build. Every page
    gets one taken from the file's name instead (and its checksum again): the same sound, the same file."""
    data = bytearray(path.read_bytes())
    serial = _crc(path.name.encode()).to_bytes(4, "little")
    i = 0
    while i < len(data):
        if data[i:i + 4] != b"OggS":
            raise OSError(f"{path}: no Ogg page at byte {i}")
        segments = data[i + 26]
        size = 27 + segments + sum(data[i + 27:i + 27 + segments])
        data[i + 14:i + 18] = serial
        data[i + 22:i + 26] = b"\0\0\0\0"
        data[i + 22:i + 26] = _crc(bytes(data[i:i + size])).to_bytes(4, "little")
        i += size
    path.write_bytes(bytes(data))


def channels(path: Path) -> int:
    """The channel count in an OGG Vorbis file's identification header."""
    data = path.read_bytes()
    i = data.index(b"\x01vorbis")
    return data[i + 11]
