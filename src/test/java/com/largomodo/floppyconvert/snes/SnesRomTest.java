package com.largomodo.floppyconvert.snes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnesRomTest {

    @Test
    void testConstructorCreatesValidRom() {
        byte[] data = new byte[4 * 1024 * 1024];
        SnesRom rom = new SnesRom(data, RomType.LoROM, 0, "TEST TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);

        assertNotNull(rom);
        assertEquals(RomType.LoROM, rom.type());
        assertEquals("TEST TITLE", rom.title());
    }

    @Test
    void testNullRawDataThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
                new SnesRom(null, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0)
        );
    }

    @Test
    void testNullRomTypeThrowsNPE() {
        byte[] data = new byte[1024];
        assertThrows(NullPointerException.class, () ->
                new SnesRom(data, null, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0)
        );
    }

    @Test
    void testNullTitleThrowsNPE() {
        byte[] data = new byte[1024];
        assertThrows(NullPointerException.class, () ->
                new SnesRom(data, RomType.LoROM, 0, null, false, (byte) 0, (byte) 0, (byte) 0, 0, 0)
        );
    }

    @Test
    void testIsHiRomReturnsTrueForHiROM() {
        byte[] data = new byte[1024];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);
        assertTrue(rom.isHiRom());
    }

    @Test
    void testIsHiRomReturnsTrueForExHiROM() {
        byte[] data = new byte[1024];
        SnesRom rom = new SnesRom(data, RomType.ExHiROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);
        assertTrue(rom.isHiRom());
    }

    @Test
    void testIsHiRomReturnsFalseForLoROM() {
        byte[] data = new byte[1024];
        SnesRom rom = new SnesRom(data, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);
        assertFalse(rom.isHiRom());
    }

    @Test
    void testEqualsAndHashCode() {
        byte[] data1 = new byte[]{1, 2, 3};
        byte[] data2 = new byte[]{1, 2, 3};

        SnesRom rom1 = new SnesRom(data1, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);
        SnesRom rom2 = new SnesRom(data2, RomType.LoROM, 0, "TITLE", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);

        assertEquals(rom1, rom2);
        assertEquals(rom1.hashCode(), rom2.hashCode());
    }
}
