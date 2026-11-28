package com.largomodo.floppyconvert.core;

/**
 * Why a ROM failed in a batch run; the name is the token in the failures.log line, so failures can be grouped
 * with grep or cut.
 */
public enum FailureCategory {
    /** The SNES checksum/complement pair is invalid. */
    CHECKSUM,
    /** The ROM size or layout is not convertible by the target format. */
    SIZE,
    /** The file cannot hold an internal SNES header. */
    HEADER,
    /** Anything else, including I/O and disk packing failures. */
    OTHER
}
