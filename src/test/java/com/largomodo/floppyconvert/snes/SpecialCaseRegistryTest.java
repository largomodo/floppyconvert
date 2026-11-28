// createRom helpers use checksum=0x0001, complement=0xFFFE to satisfy the SnesRom invariant. (ref: DL-003)
package com.largomodo.floppyconvert.snes;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialCaseRegistryTest {

    private static final int EXTENDED_HEADER = SnesRom.EXTENDED_BASE + 0xFFB0;

    @Test
    void exactTalesOfPhantasiaMatch() {
        SnesRom rom = createRom(0x33, "AFATV", true);
        assertTrue(SpecialCaseRegistry.isTalesOfPhantasia(rom));
        assertFalse(SpecialCaseRegistry.isDaiKaijuMonogatari2(rom));
    }

    @Test
    void exactDaiKaijuMonogatari2Match() {
        SnesRom rom = createRom(0x33, "18AE6", true);
        assertTrue(SpecialCaseRegistry.isDaiKaijuMonogatari2(rom));
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(rom));
    }

    @Test
    // The ID bytes alone are not enough: maker 0x33 is also required.
    void correctIdWrongMaker() {
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(createRom(0x36, "AFATV", true)));
        assertFalse(SpecialCaseRegistry.isDaiKaijuMonogatari2(createRom(0x18, "18AE6", true)));
    }

    @Test
    void correctMakerWrongId() {
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(createRom(0x33, "AFATX", true)));
        assertFalse(SpecialCaseRegistry.isDaiKaijuMonogatari2(createRom(0x33, "18AE7", true)));
    }

    @Test
    void idWithoutExtendedHeaderDoesNotMatch() {
        // ucon64 only checks the ID when snes_header_base == SNES_EROM
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(createRom(0x33, "AFATV", false)));
        assertFalse(SpecialCaseRegistry.isDaiKaijuMonogatari2(createRom(0x33, "18AE6", false)));
    }

    @Test
    void titleAndMakerOfTheOldRuleNoLongerMatch() {
        SnesRom rom = new SnesRom(new byte[EXTENDED_HEADER + 0x50], RomType.HiROM, 0, "TALES OF PHANTASIA", false,
                0, 0x36, 0, 0x0001, 0xFFFE, SnesRom.EXTENDED_BASE, false, false);
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(rom));
    }

    private static SnesRom loRom(int sizeMbit, String id) {
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, data, 0x7FB0, idBytes.length);
        return new SnesRom(data, RomType.LoROM, 0, "TITLE", false, 0, 0, 0, 0x0001, 0xFFFE);
    }

    @Test
    // The rule is the named Star Ocean ID bytes on a 96 Mbit LoROM, not a general 96 Mbit SRAM rule.
    void starOcean96MatchesTheNamedGameOnly() {
        SnesRom starOcean = loRom(96, "B4ARF");
        assertTrue(SpecialCaseRegistry.isStarOcean96(starOcean, Gd3Layout.of(starOcean)));
    }

    @Test
    void starOcean96RejectsOtherIdsAndSizes() {
        SnesRom sfa2 = loRom(96, "XXXXX");
        assertFalse(SpecialCaseRegistry.isStarOcean96(sfa2, Gd3Layout.of(sfa2)));

        SnesRom starOcean16 = loRom(16, "B4ARF");
        assertFalse(SpecialCaseRegistry.isStarOcean96(starOcean16, Gd3Layout.of(starOcean16)));
    }

    private SnesRom createRom(int maker, String id, boolean extended) {
        byte[] data = new byte[EXTENDED_HEADER + 0x50];
        int headerOffset = extended ? EXTENDED_HEADER : 0xFFB0;
        byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, data, headerOffset, idBytes.length);
        return new SnesRom(data, RomType.HiROM, 0, "TITLE", false, 0, maker, 0, 0x0001, 0xFFFE,
                extended ? SnesRom.EXTENDED_BASE : 0, false, false);
    }
}
