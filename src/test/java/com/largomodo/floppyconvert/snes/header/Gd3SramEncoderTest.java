package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Gd3SramEncoderTest {

    private final Gd3SramEncoder encoder = new Gd3SramEncoder();

    @Property
    void sramSize8KBProducesCode0x81(@ForAll("romWith8KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals((byte) 0x81, header[16], "SRAM size 8KB should encode as 0x81");
    }

    @Property
    void sramSize2KBProducesCode0x82(@ForAll("romWith2KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals((byte) 0x82, header[16], "SRAM size 2KB should encode as 0x82");
    }

    @Property
    void sramSize0ProducesCode0x80(@ForAll("romWithoutSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals((byte) 0x80, header[16], "SRAM size 0 should encode as 0x80");
    }

    @Property
    void sramSize256KBProducesCode0x80(@ForAll("romWith256KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals((byte) 0x80, header[16], "SRAM size 256KB should encode as 0x80");
    }

    @Property
    void encoderOnlyModifiesByte16(@ForAll("anyRom") SnesRom rom) {
        byte[] header = new byte[512];
        Arrays.fill(header, (byte) 0xFF);

        encoder.encodeSram(header, rom);

        for (int i = 0; i < 512; i++) {
            if (i == 16) {
                continue;
            }
            assertEquals((byte) 0xFF, header[i],
                    "Encoder should only modify byte 16, but byte " + i + " was changed");
        }
    }

    @Test
    void encoderOverwritesByte16Completely() {
        SnesRom rom = createTestRom(RomType.LoROM, 8192);
        byte[] header = new byte[512];
        header[16] = (byte) 0xFF;

        encoder.encodeSram(header, rom);

        assertEquals((byte) 0x81, header[16], "Encoder should overwrite byte 16 completely");
    }

    @Test
    void allValidSramSizesProduceValidCodes() {
        int[] validSramSizes = {0, 2048, 8192, 32768};
        byte[] expectedCodes = {(byte) 0x80, (byte) 0x82, (byte) 0x81, (byte) 0x80};

        for (int i = 0; i < validSramSizes.length; i++) {
            SnesRom rom = createTestRom(RomType.LoROM, validSramSizes[i]);
            byte[] header = new byte[512];
            encoder.encodeSram(header, rom);

            assertEquals(expectedCodes[i], header[16],
                    "SRAM size " + validSramSizes[i] + " should produce code " +
                            String.format("0x%02X", expectedCodes[i] & 0xFF));
        }
    }

    @Provide
    Arbitrary<SnesRom> anyRom() {
        return Combinators.combine(
                RomDataGenerator.loRomData(),
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                Arbitraries.of(0, 2048, 8192, 32768)
        ).as((data, type, sramSize) ->
                createRomFromData(data, type, sramSize));
    }

    @Provide
    Arbitrary<SnesRom> romWithoutSram() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0));
    }

    @Provide
    Arbitrary<SnesRom> romWith2KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 2048));
    }

    @Provide
    Arbitrary<SnesRom> romWith8KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 8192));
    }

    @Provide
    Arbitrary<SnesRom> romWith256KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 32768));
    }

    private SnesRom createRomFromData(byte[] data, RomType type, int sramSize) {
        return new SnesRom(
                data,
                type,
                sramSize,
                "TEST ROM",
                false,
                0,
                0,
                0,
                0,
                0
        );
    }

    private SnesRom createTestRom(RomType type, int sramSize) {
        byte[] data = new byte[1024 * 1024];
        return createRomFromData(data, type, sramSize);
    }
}
