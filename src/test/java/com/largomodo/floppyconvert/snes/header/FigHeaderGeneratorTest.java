package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FigHeaderGeneratorTest {

    private final FigHeaderGenerator generator = new FigHeaderGenerator();

    @Property(tries = 100)
    void headerSizeIsAlways512Bytes(@ForAll("anyRom") SnesRom rom,
                                    @ForAll int splitPartIndex,
                                    @ForAll boolean isLastPart) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, splitPartIndex, isLastPart);
        assertEquals(512, header.length, "FIG header must always be exactly 512 bytes");
    }

    @Property(tries = 100)
    void hiRomFlagIsSetForHiRomRoms(@ForAll("hiRomRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);
        assertEquals((byte) 0x80, header[3], "HiROM flag should be 0x80 at offset 3");
    }

    @Property(tries = 100)
    void hiRomFlagIsNotSetForLoRomRoms(@ForAll("loRomRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);
        assertEquals((byte) 0x00, header[3], "HiROM flag should be 0x00 for LoROM at offset 3");
    }

    @Property(tries = 100)
    void multiFileFlag0x40WhenNotLastPart(@ForAll("anyRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, false);
        assertEquals((byte) 0x40, header[2], "Multi-file flag should be 0x40 when not last part");
    }

    @Property(tries = 100)
    void multiFileFlag0x00WhenLastPart(@ForAll("anyRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);
        assertEquals((byte) 0x00, header[2], "Multi-file flag should be 0x00 when last part");
    }

    @Property(tries = 100)
    void sizeFieldContainsCorrectBlockCount(@ForAll("anyRom") SnesRom rom) {
        int partSize = 512 * 1024;
        byte[] header = generator.generateHeader(rom, partSize, 0, true);

        int blocks = partSize / 8192;
        int headerBlocks = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);

        assertEquals(blocks, headerBlocks, "Size field should contain part size in 8KB blocks");
    }

    @Property(tries = 100)
    void dspFlagIsSetInEmulationBytesForDspRoms(@ForAll("dspRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);

        int emu1 = header[4] & 0xFF;

        if (rom.isHiRom()) {
            assertTrue((emu1 & 0xF0) == 0xF0,
                "DSP flag 0xF0 should be set in emu1 for HiROM DSP ROMs");
        } else {
            assertTrue((emu1 & 0x40) == 0x40,
                "DSP flag 0x40 should be set in emu1 for LoROM DSP ROMs");
        }
    }

    @Property(tries = 100)
    void loRomWithNoSramHasCorrectEmulationBytes(@ForAll("loRomNoSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);

        assertEquals((byte) 0x77, header[4], "LoROM without SRAM should have emu1 = 0x77");
        assertEquals((byte) 0x83, header[5], "LoROM without SRAM should have emu2 = 0x83");
    }

    @Property(tries = 100)
    void loRomWithSmallSramHasCorrectEmu2(@ForAll("loRomSmallSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);

        assertEquals((byte) 0x80, header[5], "LoROM with SRAM <= 8KB should have emu2 = 0x80");
    }

    @Property(tries = 100)
    void hiRomWithSramHasCorrectEmu1(@ForAll("hiRomWithSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);

        int emu1 = header[4] & 0xFF;
        assertTrue((emu1 & 0xDD) == 0xDD,
            "HiROM with SRAM should have 0xDD bits set in emu1");
    }

    @Property(tries = 100)
    void hiRomHasEmu2Bit0x02Set(@ForAll("hiRomRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true);

        int emu2 = header[5] & 0xFF;
        assertTrue((emu2 & 0x02) == 0x02,
            "HiROM should have 0x02 bit set in emu2");
    }

    @Property(tries = 100)
    void headerGeneratedForAllSplitParts(@ForAll("anyRom") SnesRom rom,
                                         @ForAll("splitPartIndex") int splitPartIndex) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, splitPartIndex, false);

        assertEquals(512, header.length,
            "FIG header should be generated for ALL parts (index " + splitPartIndex + ")");

        assertNotEquals(0, header[0],
            "Header should contain valid size data for part " + splitPartIndex);
    }

    @Test
    void chronoTriggerHeaderHasCorrectSize() {
        byte[] romData = new byte[4 * 1024 * 1024];
        int partSize = 512 * 1024;
        SnesRom rom = new SnesRom(romData, RomType.HiROM, 8192, "CHRONO TRIGGER",
            false, 0x01, 0x00, 0x00, 0, 0);

        byte[] header = generator.generateHeader(rom, partSize, 0, true);

        assertEquals(512, header.length);

        int blocks = partSize / 8192;
        int headerBlocks = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);
        assertEquals(blocks, headerBlocks, "512KB part should have 64 blocks (512KB / 8KB)");
    }

    @Test
    void headerContainsValidDataForFirstPart() {
        byte[] romData = new byte[2 * 1024 * 1024];
        SnesRom rom = new SnesRom(romData, RomType.LoROM, 0, "TEST ROM",
            false, 0x01, 0x00, 0x00, 0, 0);

        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, false);

        assertEquals(512, header.length);
        assertEquals((byte) 0x40, header[2], "First part should have multi-file flag");
        assertEquals((byte) 0x00, header[3], "LoROM should have HiROM flag = 0x00");
    }

    @Test
    void headerContainsValidDataForLastPart() {
        byte[] romData = new byte[2 * 1024 * 1024];
        SnesRom rom = new SnesRom(romData, RomType.HiROM, 8192, "TEST ROM",
            false, 0x01, 0x00, 0x00, 0, 0);

        byte[] header = generator.generateHeader(rom, 512 * 1024, 2, true);

        assertEquals(512, header.length);
        assertEquals((byte) 0x00, header[2], "Last part should have multi-file flag = 0x00");
        assertEquals((byte) 0x80, header[3], "HiROM should have HiROM flag = 0x80");
    }

    @Provide
    Arbitrary<SnesRom> anyRom() {
        return Arbitraries.oneOf(loRomRom(), hiRomRom());
    }

    @Provide
    Arbitrary<SnesRom> loRomRom() {
        return RomDataGenerator.loRomData().map(this::createLoRomFromData);
    }

    @Provide
    Arbitrary<SnesRom> hiRomRom() {
        return RomDataGenerator.hiRomData().map(this::createHiRomFromData);
    }

    @Provide
    Arbitrary<SnesRom> dspRom() {
        return Arbitraries.oneOf(
            RomDataGenerator.loRomData().map(data -> createRomWithDsp(data, RomType.LoROM)),
            RomDataGenerator.hiRomData().map(data -> createRomWithDsp(data, RomType.HiROM))
        );
    }

    @Provide
    Arbitrary<SnesRom> loRomNoSram() {
        return RomDataGenerator.loRomData().map(data ->
            new SnesRom(data, RomType.LoROM, 0, "TEST ROM", false, 0x01, 0x00, 0x00, 0, 0)
        );
    }

    @Provide
    Arbitrary<SnesRom> loRomSmallSram() {
        return RomDataGenerator.loRomData().map(data ->
            new SnesRom(data, RomType.LoROM, 2048, "TEST ROM", false, 0x01, 0x00, 0x00, 0, 0)
        );
    }

    @Provide
    Arbitrary<SnesRom> hiRomWithSram() {
        return RomDataGenerator.hiRomData().map(data ->
            new SnesRom(data, RomType.HiROM, 8192, "TEST ROM", false, 0x01, 0x00, 0x00, 0, 0)
        );
    }

    @Provide
    Arbitrary<Integer> splitPartIndex() {
        return Arbitraries.integers().between(0, 10);
    }

    private SnesRom createLoRomFromData(byte[] data) {
        return new SnesRom(data, RomType.LoROM, 0, "TEST ROM", false, 0x01, 0x00, 0x00, 0, 0);
    }

    private SnesRom createHiRomFromData(byte[] data) {
        return new SnesRom(data, RomType.HiROM, 0, "TEST ROM", false, 0x01, 0x00, 0x00, 0, 0);
    }

    private SnesRom createRomWithDsp(byte[] data, RomType type) {
        return new SnesRom(data, type, 0, "TEST ROM", true, 0x01, 0x00, 0x00, 0, 0);
    }
}
