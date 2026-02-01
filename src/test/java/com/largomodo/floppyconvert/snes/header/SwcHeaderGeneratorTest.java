package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwcHeaderGeneratorTest {

    private final SwcHeaderGenerator generator = new SwcHeaderGenerator();

    @Property
    void headerSizeIsAlways512Bytes(@ForAll("anyRom") SnesRom rom,
                                     @ForAll int partIndex,
                                     @ForAll boolean isLastPart) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, partIndex, isLastPart, (byte) 0x00);
        assertEquals(512, header.length, "SWC header must always be exactly 512 bytes");
    }

    @Property
    void hiRomProducesEmulationByte0x30(@ForAll("hiRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x30, emulationByte & 0x30,
                "HiROM should have emulation mode bits 0x30 set");
    }

    @Property
    void loRomProducesEmulationByteWithout0x30(@ForAll("loRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0, emulationByte & 0x30,
                "LoROM should not have emulation mode bits 0x30 set");
    }

    @Property
    void sramSize0ProducesEncoding0x0C(@ForAll("romWithSramSize0") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x0C, emulationByte & 0x0C,
                "SRAM size 0 should encode as 0x0C");
    }

    @Property
    void sramSize2KBProducesEncoding0x08(@ForAll("romWithSramSize2KB") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x08, emulationByte & 0x0C,
                "SRAM size <= 2KB should encode as 0x08");
    }

    @Property
    void sramSize8KBProducesEncoding0x04(@ForAll("romWithSramSize8KB") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x04, emulationByte & 0x0C,
                "SRAM size <= 8KB should encode as 0x04");
    }

    @Property
    void sramSize32KBProducesEncoding0x00(@ForAll("romWithSramSize32KB") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x00, emulationByte & 0x0C,
                "SRAM size > 8KB should encode as 0x00");
    }

    @Property
    void multiFileFlagSetWhenNotLastPart(@ForAll("anyRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, false, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x40, emulationByte & 0x40,
                "Multi-file flag (0x40) should be set when not last part");
    }

    @Property
    void multiFileFlagNotSetWhenLastPart(@ForAll("anyRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);
        int emulationByte = header[2] & 0xFF;
        assertEquals(0x00, emulationByte & 0x40,
                "Multi-file flag (0x40) should not be set when last part");
    }

    @Test
    void fixedIDsArePresentAtCorrectOffsets() {
        SnesRom rom = createTestRom(RomType.LoROM, 0, 1024 * 1024);
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals((byte) 0xAA, header[8], "Fixed ID 0xAA should be at offset 8");
        assertEquals((byte) 0xBB, header[9], "Fixed ID 0xBB should be at offset 9");
        assertEquals((byte) 0x04, header[10], "Fixed ID 0x04 (SNES Program) should be at offset 10");
    }

    @Test
    void sizeCalculationFor4MbitRom() {
        int romSize = 4 * 1024 * 1024;
        int partSize = 512 * 1024;
        SnesRom rom = createTestRom(RomType.LoROM, 0, romSize);
        byte[] header = generator.generateHeader(rom, partSize, 0, true, (byte) 0x00);

        int expectedBlocks = partSize / 8192;
        int actualBlocks = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);

        assertEquals(expectedBlocks, actualBlocks,
                "Size field should contain part size in 8KB blocks");
        assertEquals(64, actualBlocks, "512KB part should be 64 blocks");
    }

    @Test
    void sizeCalculationFor8MbitRom() {
        int romSize = 8 * 1024 * 1024;
        int partSize = 512 * 1024;
        SnesRom rom = createTestRom(RomType.LoROM, 0, romSize);
        byte[] header = generator.generateHeader(rom, partSize, 0, true, (byte) 0x00);

        int expectedBlocks = partSize / 8192;
        int actualBlocks = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);

        assertEquals(expectedBlocks, actualBlocks,
                "Size field should contain part size in 8KB blocks");
        assertEquals(64, actualBlocks, "512KB part should be 64 blocks");
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
    Arbitrary<SnesRom> hiRom() {
        return RomDataGenerator.hiRomData()
                .map(data -> createRomFromData(data, RomType.HiROM, 0));
    }

    @Provide
    Arbitrary<SnesRom> loRom() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0));
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

    private SnesRom createTestRom(RomType type, int sramSize, int romSize) {
        byte[] data = new byte[romSize];
        return createRomFromData(data, type, sramSize);
    }
}
