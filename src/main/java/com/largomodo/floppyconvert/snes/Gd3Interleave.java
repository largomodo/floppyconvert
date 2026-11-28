package com.largomodo.floppyconvert.snes;

/**
 * Block-interleave scheme of a Game Doctor image, one value per branch of ucon64's
 * {@code snes_convert_to_gd} (snes.c 1818-1855) and {@code snes_deinterleave} (snes.c 3859).
 */
public enum Gd3Interleave {
    /** LoROM data and sub-4 Mbit pass-through: bytes stay in ROM order. */
    NONE,
    /** One pass over the whole image: lower 32 KB of each 64 KB block goes to the second half. */
    GLOBAL,
    /** 24 Mbit HiROM: a 16 Mbit region and an 8 Mbit region interleaved separately. */
    TWO_REGION_24,
    /** Extended HiROM: the expansion past 32 Mbit is placed first, the 32 Mbit base after it. */
    EXTENDED
}
