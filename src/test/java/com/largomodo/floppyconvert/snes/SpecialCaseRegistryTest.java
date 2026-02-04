package com.largomodo.floppyconvert.snes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialCaseRegistryTest {

    @Test
    void exactTalesOfPhantasiaMatch() {
        SnesRom rom = createRom(0x36, "TALES OF PHANTASIA");
        assertTrue(SpecialCaseRegistry.isTalesOfPhantasia(rom));
    }

    @Test
    void exactDaiKaijuMonogatari2Match() {
        SnesRom rom = createRom(0x18, "DAIKAIJYUMONOGATARI2");
        assertTrue(SpecialCaseRegistry.isDaiKaijuMonogatari2(rom));
    }

    @Test
    void similarTitleWrongMaker() {
        SnesRom rom = createRom(0x99, "TALES OF PHANTASIA");
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(rom));
    }

    @Test
    void correctMakerWrongTitle() {
        SnesRom rom = createRom(0x36, "TALES OF DESTINY");
        assertFalse(SpecialCaseRegistry.isTalesOfPhantasia(rom));
    }

    @Test
    void caseSensitiveTitle() {
        SnesRom rom = createRom(0x36, "TALES OF PHANTASIA");
        assertTrue(SpecialCaseRegistry.isTalesOfPhantasia(rom));
    }

    @Test
    void daiKaijuSimilarTitleWrongMaker() {
        SnesRom rom = createRom(0x99, "DAIKAIJYUMONOGATARI2");
        assertFalse(SpecialCaseRegistry.isDaiKaijuMonogatari2(rom));
    }

    @Test
    void daiKaijuCorrectMakerWrongTitle() {
        SnesRom rom = createRom(0x18, "DAIKAIJYUMONOGATARI3");
        assertFalse(SpecialCaseRegistry.isDaiKaijuMonogatari2(rom));
    }

    private SnesRom createRom(int maker, String title) {
        return new SnesRom(
                new byte[1024],
                RomType.HiROM,
                0,
                title,
                false,
                0,
                maker,
                0,
                0,
                0
        );
    }
}
