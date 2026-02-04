package com.largomodo.floppyconvert.snes;

/**
 * Registry of game-specific ROM handling quirks requiring non-standard header values.
 * Static utility pattern matches immutable ROM records (no state needed).
 * Detection logic ported from ucon64 snes.c snes_set_gd3_header function.
 */
public class SpecialCaseRegistry {

    private SpecialCaseRegistry() {
    }

    /**
     * Detects Tales of Phantasia (Japan) requiring special GD3 SRAM mapping.
     * ucon64 snes.c:955-1010 documents this game requires non-standard header byte 0x17 = 0x40
     */
    public static boolean isTalesOfPhantasia(SnesRom rom) {
        return rom.maker() == 0x36 && rom.title().startsWith("TALES OF PHANTASIA");
    }

    /**
     * Detects Dai Kaiju Monogatari 2 (Japan) requiring special GD3 SRAM mapping.
     * ucon64 snes.c snes_set_gd3_header function documents this game requires non-standard header values
     */
    public static boolean isDaiKaijuMonogatari2(SnesRom rom) {
        return rom.maker() == 0x18 && rom.title().startsWith("DAIKAIJYUMONOGATARI2");
    }
}
