// createRom helpers use checksum=0x0001, complement=0xFFFE to satisfy the SnesRom invariant. (ref: DL-003)
package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HardwareValidatorTest {

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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 48, 64})
    // These are exactly the extended sizes ucon64 converts; the validator hands them on to the layout.
    void gd3AcceptsExtendedHiRomUpTo64Mbit(int sizeMbit) {
        SnesRom rom = createExHiRom(sizeMbit * SnesConstants.MBIT);
        assertDoesNotThrow(() -> new Gd3HardwareValidator().validate(rom, CopierFormat.GD3));
    }

    @Test
    void gd3Rejects72MbitExtendedHiRom() {
        SnesRom rom = createExHiRom(72 * SnesConstants.MBIT);

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(rom, CopierFormat.GD3));

        assertTrue(e.getMessage().contains("conversion not yet implemented"), e.getMessage());
    }

    private SnesRom createLoRom96(int sramSize, String id) {
        byte[] data = new byte[96 * SnesConstants.MBIT];
        byte[] idBytes = id.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, data, 0x7FB0, idBytes.length);
        return new SnesRom(data, RomType.LoROM, sramSize, "TEST 96", false, 0, 0, 0, 0x0001, 0xFFFE);
    }

    @Test
    void gd3Accepts96MbitWithoutSram() {
        assertDoesNotThrow(() -> new Gd3HardwareValidator().validate(createLoRom96(0, "XXXXX"), CopierFormat.GD3));
    }

    @Test
    void gd3Accepts96MbitStarOceanWithSram() {
        assertDoesNotThrow(() -> new Gd3HardwareValidator().validate(createLoRom96(8192, "B4ARF"), CopierFormat.GD3));
    }

    @Test
    void gd3Rejects96MbitWithSramThatIsNotStarOcean() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(createLoRom96(8192, "XXXXX"), CopierFormat.GD3));

        assertTrue(e.getMessage().contains("only converted for Star Ocean"), e.getMessage());
    }

    @Test
    void gd3RejectsNonExtendedHiRomAbove32Mbit() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(createHiRom(33 * SnesConstants.MBIT), CopierFormat.GD3));

        assertTrue(e.getMessage().contains("conversion not yet implemented"), e.getMessage());
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
    void ufoRejects33MbitHiRom() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new UfoHardwareValidator().validate(createHiRom(33 * SnesConstants.MBIT), CopierFormat.UFO));

        assertEquals("UFO hardware does not support HiROM > 32Mbit: ROM is 33 Mbit", e.getMessage());
    }

    @Test
    // LoROM is cut in 4 Mbit parts and the unit takes 9 of them, so 36 Mbit is the last size that fits.
    void ufoAcceptsLoRomUpTo36Mbit() {
        UfoHardwareValidator validator = new UfoHardwareValidator();
        assertDoesNotThrow(() -> validator.validate(createLoRom(32 * SnesConstants.MBIT), CopierFormat.UFO));
        assertDoesNotThrow(() -> validator.validate(createLoRom(36 * SnesConstants.MBIT), CopierFormat.UFO));
    }

    @Test
    void ufoRejectsLoRomAbove36Mbit() {
        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new UfoHardwareValidator().validate(createLoRom(36 * SnesConstants.MBIT + 1), CopierFormat.UFO));

        assertTrue(e.getMessage().contains("max 9 parts"), e.getMessage());
    }

    @Test
    // Each validator guards its own copier only: a ROM too large for it still passes for any other format.
    void validatorsIgnoreForeignFormats() {
        SnesRom oversized = createHiRom(64 * SnesConstants.MBIT);

        assertDoesNotThrow(() -> new UfoHardwareValidator().validate(oversized, CopierFormat.GD3));
        assertDoesNotThrow(() -> new Gd3HardwareValidator().validate(oversized, CopierFormat.UFO));
    }

    @Test
    // Extended LoROM is the one class the layout does not size-check, so only the 16-part limit stops it.
    void gd3RejectsExtendedLoRomNeedingMoreThan16Parts() {
        SnesRom rom = new SnesRom(new byte[72 * SnesConstants.MBIT], RomType.LoROM, 0, "Test extended LoROM",
                false, 0, 0, 0, 0x0001, 0xFFFE, SnesRom.EXTENDED_BASE, false, false);

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class,
                () -> new Gd3HardwareValidator().validate(rom, CopierFormat.GD3));

        assertEquals("ROM needs 18 parts of 4 Mbit; GD3 hardware supports at most 16", e.getMessage());
    }

    @Test
    void bothValidatorsReject64MbitStandardHiRom() {
        SnesRom rom = createHiRom(64 * SnesConstants.MBIT);

        UfoHardwareValidator ufoValidator = new UfoHardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> ufoValidator.validate(rom, CopierFormat.UFO));

        Gd3HardwareValidator gd3Validator = new Gd3HardwareValidator();
        assertThrows(UnsupportedHardwareException.class, () -> gd3Validator.validate(rom, CopierFormat.GD3));
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
