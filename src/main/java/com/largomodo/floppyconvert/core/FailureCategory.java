package com.largomodo.floppyconvert.core;

/**
 * Why a ROM failed in a batch run; the name is the token in the failures.log line, so failures can be grouped
 * with grep or cut. HARDWARE marks the cases a larger --gd3-dram would convert; SIZE marks those no Game Doctor
 * configuration converts.
 */
public enum FailureCategory {
    /** The SNES checksum/complement pair is invalid. */
    CHECKSUM,
    /** The ROM size or layout is not convertible by the target format. */
    SIZE,
    /** The configured hardware is too small; a larger configuration would convert it. */
    HARDWARE,
    /** The file cannot hold an internal SNES header. */
    HEADER,
    /** Anything else, including I/O and disk packing failures. */
    OTHER
}
