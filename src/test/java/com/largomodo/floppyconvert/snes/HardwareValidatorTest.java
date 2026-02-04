package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HardwareValidatorTest {

    @Property
    void ufoAccepts32MbitHiRom(@ForAll("hiRom32Mbit") SnesRom rom) {
        UfoHardwareValidator validator = new UfoHardwareValidator();
        assertDoesNotThrow(() -> validator.validate(rom, CopierFormat.UFO));
    }

    @Property
    void ufoRejects33MbitHiRom(@ForAll("hiRom33Mbit") SnesRom rom) {
        UfoHardwareValidator validator = new UfoHardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> validator.validate(rom, CopierFormat.UFO));
    }

    @Property
    void gd3Accepts48MbitExHiRom(@ForAll("exHiRom48Mbit") SnesRom rom) {
        Gd3HardwareValidator validator = new Gd3HardwareValidator();
        assertDoesNotThrow(() -> validator.validate(rom, CopierFormat.GD3));
    }

    @Property
    void gd3Rejects33MbitStandardHiRom(@ForAll("hiRom33Mbit") SnesRom rom) {
        Gd3HardwareValidator validator = new Gd3HardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> validator.validate(rom, CopierFormat.GD3));
    }

    @Property
    void gd3Rejects33MbitLoRom(@ForAll("loRom33Mbit") SnesRom rom) {
        Gd3HardwareValidator validator = new Gd3HardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> validator.validate(rom, CopierFormat.GD3));
    }

    @Test
    void ufoAcceptsExactly32MbitHiRom() {
        SnesRom rom = createHiRom(32 * SnesConstants.MBIT);
        UfoHardwareValidator validator = new UfoHardwareValidator();
        assertDoesNotThrow(() -> validator.validate(rom, CopierFormat.UFO));
    }

    @Test
    void ufoRejects32MbitPlus1ByteHiRom() {
        SnesRom rom = createHiRom(32 * SnesConstants.MBIT + 1);
        UfoHardwareValidator validator = new UfoHardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> validator.validate(rom, CopierFormat.UFO));
    }

    @Test
    void bothValidatorsReject64MbitStandardHiRom() {
        SnesRom rom = createHiRom(64 * SnesConstants.MBIT);

        UfoHardwareValidator ufoValidator = new UfoHardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> ufoValidator.validate(rom, CopierFormat.UFO));

        Gd3HardwareValidator gd3Validator = new Gd3HardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> gd3Validator.validate(rom, CopierFormat.GD3));
    }

    @Property
    void ufoAccepts32MbitLoRom(@ForAll("loRom32Mbit") SnesRom rom) {
        UfoHardwareValidator validator = new UfoHardwareValidator();
        assertDoesNotThrow(() -> validator.validate(rom, CopierFormat.UFO));
    }

    @Provide
    Arbitrary<SnesRom> hiRom32Mbit() {
        return Arbitraries.just(createHiRom(32 * SnesConstants.MBIT));
    }

    @Provide
    Arbitrary<SnesRom> hiRom33Mbit() {
        return Arbitraries.just(createHiRom(33 * SnesConstants.MBIT));
    }

    @Provide
    Arbitrary<SnesRom> exHiRom48Mbit() {
        return Arbitraries.just(createExHiRom(48 * SnesConstants.MBIT));
    }

    @Provide
    Arbitrary<SnesRom> loRom33Mbit() {
        return Arbitraries.just(createLoRom(33 * SnesConstants.MBIT));
    }

    @Provide
    Arbitrary<SnesRom> loRom32Mbit() {
        return Arbitraries.just(createLoRom(32 * SnesConstants.MBIT));
    }

    private SnesRom createHiRom(int sizeBytes) {
        return new SnesRom(
                new byte[sizeBytes],
                RomType.HiROM,
                0,
                "Test HiROM",
                false,
                0x00,
                0x00,
                0x00,
                0x0000,
                0xFFFF
        );
    }

    private SnesRom createExHiRom(int sizeBytes) {
        return new SnesRom(
                new byte[sizeBytes],
                RomType.ExHiROM,
                0,
                "Test ExHiROM",
                false,
                0x00,
                0x00,
                0x00,
                0x0000,
                0xFFFF
        );
    }

    private SnesRom createLoRom(int sizeBytes) {
        return new SnesRom(
                new byte[sizeBytes],
                RomType.LoROM,
                0,
                "Test LoROM",
                false,
                0x00,
                0x00,
                0x00,
                0x0000,
                0xFFFF
        );
    }
}
