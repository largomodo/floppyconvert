package com.largomodo.floppyconvert.snes;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Registry of game-specific ROM handling quirks requiring non-standard header values.
 * Static utility pattern matches immutable ROM records (no state needed).
 * Detection logic ported from ucon64 snes.c snes_set_gd3_header function.
 */
public class SpecialCaseRegistry {

    // Both extended special cases carry this maker code.
    private static final int MAKER_NAMCO_HUDSON = 0x33;
    private static final int ID_LENGTH = 5;

    private SpecialCaseRegistry() {
    }

    /**
     * Detects Tales of Phantasia (Japan) requiring special GD3 SRAM mapping.
     * ucon64 snes.c:1647-1660 compares 5 bytes at the start of the extended internal header to "AFATV"
     * (a title and maker match misses every translation); only a ROM with an extended header qualifies.
     */
    public static boolean isTalesOfPhantasia(SnesRom rom) {
        return hasExtendedId(rom, "AFATV");
    }

    /**
     * Detects Dai Kaiju Monogatari 2 (Japan) requiring special GD3 SRAM mapping.
     * ucon64 snes.c:1647-1660 compares the same 5 bytes to "18AE6".
     */
    public static boolean isDaiKaijuMonogatari2(SnesRom rom) {
        return hasExtendedId(rom, "18AE6");
    }

    /**
     * Detects the 96 Mbit pre-decompressed Star Ocean build (game code "ARF", maker "B4" at the start of the
     * internal header), which needs a non-default SRAM map. Named on purpose: the rule is inferred from one
     * hardware-verified file, so it matches this game's code and maker only; one sample cannot separate the game's
     * SRAM from a property of the whole class.
     */
    public static boolean isStarOcean96(SnesRom rom, Gd3Layout layout) {
        // The 96 Mbit class keeps its internal header at the base-0 position.
        return layout.isLoRom96()
                && Arrays.equals(rom.headerBytes(0x00, ID_LENGTH), "B4ARF".getBytes(StandardCharsets.US_ASCII));
    }

    // ucon64 also requires maker 0x33 (Namco / Hudson Soft) and snes_header_base == SNES_EROM. (ref: DL-013)
    private static boolean hasExtendedId(SnesRom rom, String id) {
        if (!rom.isExtended() || rom.maker() != MAKER_NAMCO_HUDSON) {
            return false;
        }
        return Arrays.equals(rom.headerBytes(0x00, ID_LENGTH), id.getBytes(StandardCharsets.US_ASCII));
    }
}
