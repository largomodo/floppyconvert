// createRom helpers use checksum=0x0001, complement=0xFFFE to satisfy the SnesRom invariant. (ref: DL-003)
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
    void gd3Accepts48MbitExtendedHiRom(@ForAll("exHiRom48Mbit") SnesRom rom) {
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
    // The size message keeps byte resolution: 32 Mbit plus one byte reads 32.00001.
    void gd3MessageReadsByteResolutionSizeFor32MbitPlus1Byte() {
        SnesRom rom = createHiRom(32 * SnesConstants.MBIT + 1);
        Gd3HardwareValidator validator = new Gd3HardwareValidator();

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> validator.validate(rom, CopierFormat.GD3));

        assertEquals("ROM size is 32.00001 Mbit -- conversion not yet implemented/verified", e.getMessage());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {9, 11, 13, 14, 15})
    void gd3RejectsPartCountsUcon64CannotConvert(int parts) {
        // HiROM converts for 2 Mbit..8 parts and for exactly 10, 12 and 16 parts of 4 Mbit
        SnesRom rom = createExHiRom((parts * 4 - 1) * SnesConstants.MBIT);
        Gd3HardwareValidator validator = new Gd3HardwareValidator();

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> validator.validate(rom, CopierFormat.GD3));

        assertTrue(e.getMessage().startsWith("ROM size is "), e.getMessage());
    }

    @Test
    void gd3RejectsHiRomBelow2Mbit() {
        SnesRom rom = createHiRom(SnesConstants.MBIT);
        Gd3HardwareValidator validator = new Gd3HardwareValidator();

        assertThrows(UnsupportedHardwareException.class, () -> validator.validate(rom, CopierFormat.GD3));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 48, 64})
    // These are exactly the extended sizes ucon64 converts; the validator hands them on to the layout.
    void gd3AcceptsExtendedHiRomUpTo64Mbit(int sizeMbit) {
        SnesRom rom = createExHiRom(sizeMbit * SnesConstants.MBIT);
        assertDoesNotThrow(() -> new Gd3HardwareValidator().validate(rom, CopierFormat.GD3));
    }

    @Test
    void gd3Rejects72MbitExtendedHiRomAsSize() {
        SnesRom rom = createExHiRom(72 * SnesConstants.MBIT);

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(rom, CopierFormat.GD3));

        assertEquals(UnsupportedHardwareException.Reason.SIZE, e.reason());
    }

    private SnesRom createLoRom96(int sramSize, String id) {
        byte[] data = new byte[96 * SnesConstants.MBIT];
        byte[] idBytes = id.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, data, 0x7FB0, idBytes.length);
        return new SnesRom(data, RomType.LoROM, sramSize, "TEST 96", false, 0, 0, 0, 0x0001, 0xFFFE);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {32, 64})
    // The 96 Mbit class needs 128 Mbit of DRAM; the message names the option.
    void gd3Rejects96MbitBelow128MbitDram(int dramMbit) {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator(Gd3Memory.of(dramMbit)).validate(createLoRom96(0, "XXXXX"), CopierFormat.GD3));

        assertEquals(UnsupportedHardwareException.Reason.HARDWARE, e.reason());
        assertTrue(e.getMessage().contains("--gd3-dram 128"), e.getMessage());
    }

    @Test
    void gd3Accepts96MbitWithoutSramAt128MbitDram() {
        assertDoesNotThrow(() -> new Gd3HardwareValidator(Gd3Memory.of(128))
                .validate(createLoRom96(0, "XXXXX"), CopierFormat.GD3));
    }

    @Test
    void gd3Accepts96MbitStarOceanWithSramAt128MbitDram() {
        assertDoesNotThrow(() -> new Gd3HardwareValidator(Gd3Memory.of(128))
                .validate(createLoRom96(8192, "B4ARF"), CopierFormat.GD3));
    }

    @Test
    void gd3Rejects96MbitWithSramThatIsNotStarOceanAsSize() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator(Gd3Memory.of(128)).validate(createLoRom96(8192, "XXXXX"), CopierFormat.GD3));

        assertEquals(UnsupportedHardwareException.Reason.SIZE, e.reason());
    }

    @Test
    void gd3Rejects64MbitExtendedAtDram32AsHardware() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator(Gd3Memory.of(32))
                        .validate(createExHiRom(64 * SnesConstants.MBIT), CopierFormat.GD3));

        assertEquals(UnsupportedHardwareException.Reason.HARDWARE, e.reason());
    }

    @Test
    void gd3Rejects96MbitLoRomAsHardwareAndNamesTheOption() {
        SnesRom rom = createLoRom(96 * SnesConstants.MBIT);

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(rom, CopierFormat.GD3));

        assertEquals(UnsupportedHardwareException.Reason.HARDWARE, e.reason());
        assertTrue(e.getMessage().contains("--gd3-dram 128"), e.getMessage());
    }

    @Test
    void gd3SizeRejectionsCarryReasonSize() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(createHiRom(33 * SnesConstants.MBIT), CopierFormat.GD3));

        assertEquals(UnsupportedHardwareException.Reason.SIZE, e.reason());
    }

    @Test
    void gd3LoRomRejectionUsesUcon64Wording() {
        SnesRom rom = createLoRom(33 * SnesConstants.MBIT);
        Gd3HardwareValidator validator = new Gd3HardwareValidator();

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> validator.validate(rom, CopierFormat.GD3));

        assertEquals("LoROM > 32 Mbit -- cannot convert", e.getMessage());
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
                0x0001,
                0xFFFE
        );
    }

    private SnesRom createExHiRom(int sizeBytes) {
        return new SnesRom(
                new byte[sizeBytes],
                RomType.HiROM,
                0,
                "Test extended HiROM",
                false,
                0x00,
                0x00,
                0x00,
                0x0001,
                0xFFFE,
                SnesRom.EXTENDED_BASE,
                false,
                false
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
                0x0001,
                0xFFFE
        );
    }
}
