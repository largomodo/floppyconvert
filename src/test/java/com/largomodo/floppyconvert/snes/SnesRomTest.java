// All SnesRom constructor call sites use checksum=0x0001, complement=0xFFFE:
// the pair satisfies the arithmetic invariant (checksum+complement)&0xFFFF==0xFFFF. (ref: DL-003)
package com.largomodo.floppyconvert.snes;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;

class SnesRomTest {

    @Test
    void testConstructorCreatesValidRom() {
        byte[] data = new byte[4 * 1024 * 1024];
        SnesRom rom = new SnesRom(data, RomType.LoROM, 0, "TEST TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE);

        assertNotNull(rom);
        assertEquals(RomType.LoROM, rom.type());
        assertEquals("TEST TITLE", rom.title());
    }

    @Test
    void testNullRawDataThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
                new SnesRom(null, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE)
        );
    }

    @Test
    void testNullRomTypeThrowsNPE() {
        byte[] data = new byte[1024];
        assertThrows(NullPointerException.class, () ->
                new SnesRom(data, null, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE)
        );
    }

    @Test
    void testNullTitleThrowsNPE() {
        byte[] data = new byte[1024];
        assertThrows(NullPointerException.class, () ->
                new SnesRom(data, RomType.LoROM, 0, null, false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE)
        );
    }

    @Test
    void testIsHiRomReturnsTrueForHiROM() {
        byte[] data = new byte[1024];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE);
        assertTrue(rom.isHiRom());
    }

    @Test
    void testIsHiRomReturnsTrueForExHiROM() {
        byte[] data = new byte[1024];
        SnesRom rom = new SnesRom(data, RomType.ExHiROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE);
        assertTrue(rom.isHiRom());
    }

    @Test
    void testIsHiRomReturnsFalseForLoROM() {
        byte[] data = new byte[1024];
        SnesRom rom = new SnesRom(data, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE);
        assertFalse(rom.isHiRom());
    }

    @Test
    void testEqualsAndHashCode() {
        byte[] data1 = new byte[]{1, 2, 3};
        byte[] data2 = new byte[]{1, 2, 3};

        SnesRom rom1 = new SnesRom(data1, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE);
        SnesRom rom2 = new SnesRom(data2, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0x0001, 0xFFFE);

        assertEquals(rom1, rom2);
        assertEquals(rom1.hashCode(), rom2.hashCode());
    }

    @Property
    void validChecksumComplementPairConstructsSuccessfully(
            @ForAll("validPairs") int checksum) {
        int complement = 0xFFFF - checksum;
        byte[] data = new byte[1024];
        assertDoesNotThrow(() -> new SnesRom(data, RomType.LoROM, 0, "TITLE", false, 0, 0, 0, checksum, complement));
    }

    @Property
    void invalidChecksumComplementPairThrowsIllegalArgumentException(
            @ForAll("invalidPairs") int checksum) {
        int complement = 0;
        byte[] data = new byte[1024];
        assertThrows(IllegalArgumentException.class,
                () -> new SnesRom(data, RomType.LoROM, 0, "TITLE", false, 0, 0, 0, checksum, complement));
    }

    @Provide
    Arbitrary<Integer> validPairs() {
        return Arbitraries.integers().between(1, 0xFFFE);
    }

    @Provide
    Arbitrary<Integer> invalidPairs() {
        // checksum != 0 and complement == 0 ensures sum != 0xFFFF
        return Arbitraries.integers().between(1, 0xFFFE);
    }
}
