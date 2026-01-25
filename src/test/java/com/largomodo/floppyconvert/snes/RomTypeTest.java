package com.largomodo.floppyconvert.snes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RomTypeTest {

    @Test
    void testEnumHasExactlyThreeValues() {
        RomType[] values = RomType.values();
        assertEquals(3, values.length);
    }

    @Test
    void testEnumConstantNames() {
        assertNotNull(RomType.valueOf("LoROM"));
        assertNotNull(RomType.valueOf("HiROM"));
        assertNotNull(RomType.valueOf("ExHiROM"));

        assertThrows(IllegalArgumentException.class, () -> RomType.valueOf("INVALID"));
    }
}
