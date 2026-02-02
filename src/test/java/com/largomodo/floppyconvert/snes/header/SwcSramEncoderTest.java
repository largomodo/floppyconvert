package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwcSramEncoderTest {

    private final SwcSramEncoder encoder = new SwcSramEncoder();

    @Property
    void sramSize0ProducesEncoding0x0C(@ForAll("romWithSramSize0") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        int sramBits = header[2] & 0x0C;
        assertEquals(0x0C, sramBits, "SRAM size 0 should encode as 0x0C");
    }

    @Property
    void sramSize2KBProducesEncoding0x08(@ForAll("romWithSramSize2KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        int sramBits = header[2] & 0x0C;
        assertEquals(0x08, sramBits, "SRAM size <= 2KB should encode as 0x08");
    }

    @Property
    void sramSize8KBProducesEncoding0x04(@ForAll("romWithSramSize8KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        int sramBits = header[2] & 0x0C;
        assertEquals(0x04, sramBits, "SRAM size <= 8KB should encode as 0x04");
    }

    @Property
    void sramSize32KBProducesEncoding0x00(@ForAll("romWithSramSize32KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        int sramBits = header[2] & 0x0C;
        assertEquals(0x00, sramBits, "SRAM size > 8KB should encode as 0x00");
    }

    @Property
    void encoderOnlyModifiesSramBitsInByte2(@ForAll("anyRom") SnesRom rom) {
        byte[] header = new byte[512];
        Arrays.fill(header, (byte) 0xFF);

        encoder.encodeSram(header, rom);

        for (int i = 0; i < 512; i++) {
            if (i == 2) {
                continue;
            }
            assertEquals((byte) 0xFF, header[i],
                    "Encoder should only modify byte 2, but byte " + i + " was changed");
        }
    }

    @Test
    void encoderPreservesExistingBitsInByte2() {
        SnesRom rom = createTestRom(RomType.LoROM, 0);
        byte[] header = new byte[512];
        header[2] = 0x30;

        encoder.encodeSram(header, rom);

        int result = header[2] & 0xFF;
        assertEquals(0x30, result & 0x30, "Existing bits should be preserved");
        assertEquals(0x0C, result & 0x0C, "SRAM bits should be added");
    }

    @Provide
    Arbitrary<SnesRom> anyRom() {
        return Combinators.combine(
                RomDataGenerator.loRomData(),
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                Arbitraries.integers().between(0, 32768)
        ).as(this::createRomFromData);
    }

    @Provide
    Arbitrary<SnesRom> romWithSramSize0() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0));
    }

    @Provide
    Arbitrary<SnesRom> romWithSramSize2KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 2048));
    }

    @Provide
    Arbitrary<SnesRom> romWithSramSize8KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 8192));
    }

    @Provide
    Arbitrary<SnesRom> romWithSramSize32KB() {
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
