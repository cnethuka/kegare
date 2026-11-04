package com.cnethuka.kegare.trait;

/** Who inherits an inheritable unique trait when its bearer loses it. */
public enum TransferMode {
    /** A random eligible online player. */
    RANDOM,
    /** The killer, if eligible and online; otherwise falls back to RANDOM. */
    KILLER
}
