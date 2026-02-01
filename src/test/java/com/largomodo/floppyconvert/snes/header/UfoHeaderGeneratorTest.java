package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class UfoHeaderGeneratorTest {

    private final UfoHeaderGenerator generator = new UfoHeaderGenerator();
    private final SnesRomReader reader = new SnesRomReader();

    @Property
    void headerSizeIsAlways512Bytes(@ForAll("snesRom") SnesRom rom,
                                     @ForAll int splitPartIndex,
                                     @ForAll boolean isLastPart) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, splitPartIndex, isLastPart, (byte) 0x00);

        assertEquals(512, header.length, "Header must always be exactly 512 bytes");
    }

    @Property
    void headerContainsSuperUfoId(@ForAll("snesRom") SnesRom rom,
                                   @ForAll int splitPartIndex,
                                   @ForAll boolean isLastPart) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, splitPartIndex, isLastPart, (byte) 0x00);

        byte[] expected = "SUPERUFO".getBytes(StandardCharsets.US_ASCII);
        byte[] actual = Arrays.copyOfRange(header, 8, 16);

        assertArrayEquals(expected, actual, "Header must contain 'SUPERUFO' at offset 8");
    }

    @Property
    void hiRomProducesBankTypeZero(@ForAll("hiRomRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(0, header[18] & 0xFF, "HiROM must have bank type 0");
    }

    @Property
    void loRomProducesBankTypeOne(@ForAll("loRomRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(1, header[18] & 0xFF, "LoROM must have bank type 1");
    }

    @Property
    void sramSizeCodeZeroForNoSram(@ForAll("romWithNoSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(0, header[19] & 0xFF, "SRAM size 0 must produce code 0");
    }

    @Property
    void sramSizeCodeOneForSmallSram(@ForAll("romWithSmallSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(1, header[19] & 0xFF, "SRAM size > 0 and <= 2048 (16kbit) must produce code 1");
    }

    @Property
    void sramSizeCodeTwoForMediumSram(@ForAll("romWithMediumSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(2, header[19] & 0xFF, "SRAM size > 2048 and <= 8192 (64kbit) must produce code 2");
    }

    @Property
    void sramSizeCodeThreeForLargeSram(@ForAll("romWithLargeSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(3, header[19] & 0xFF, "SRAM size > 8192 and <= 32768 (256kbit) must produce code 3");
    }

    @Property
    void sramSizeCodeEightForVeryLargeSram(@ForAll("romWithVeryLargeSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(8, header[19] & 0xFF, "SRAM size > 32768 (>256kbit) must produce code 8");
    }

    @Property
    void hiRomSramMappingControlsSetCorrectly(@ForAll("hiRomWithSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(0, header[23] & 0xFF, "HiROM SRAM type must be 0");
        assertEquals(0x0C, header[21] & 0xFF, "HiROM A20/A21 mapping must be 0x0C when SRAM present");
        assertEquals(0x02, header[22] & 0xFF, "HiROM A22/A23 mapping must be 0x02");
    }

    @Property
    void loRomWithSramMappingControlsSetCorrectly(@ForAll("loRomWithSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(3, header[23] & 0xFF, "LoROM SRAM type must be 3");
        assertEquals(2, header[20] & 0xFF, "LoROM A15 mapping must be 2 when SRAM present");
        assertEquals(0x0F, header[21] & 0xFF, "LoROM A20/A21 mapping must be 0x0F when SRAM present");
        assertEquals(3, header[22] & 0xFF, "LoROM A22/A23 mapping must be 3 when SRAM present");
    }

    @Property
    void loRomWithoutSramAndWithDspMappingControlsSetCorrectly(@ForAll("loRomWithDspNoSram") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(1, header[20] & 0xFF, "LoROM A15 mapping must be 1 when DSP present and no SRAM");
        assertEquals(0x0C, header[21] & 0xFF, "LoROM A20/A21 mapping must be 0x0C when DSP present and no SRAM");
    }

    @Property
    void loRomWithoutSramAndWithoutDspMappingControlsSetCorrectly(@ForAll("loRomWithoutSramOrDsp") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(2, header[22] & 0xFF, "LoROM A22/A23 mapping must be 2 when no SRAM and no DSP");
        assertEquals(0, header[23] & 0xFF, "LoROM SRAM type must be 0 when no SRAM and no DSP");
    }

    @Property
    void sizeMbitIsCalculatedCorrectly(@ForAll("snesRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        int expectedMbit = rom.rawData().length / 131072;
        int actualMbit = header[17] & 0xFF;

        assertEquals(expectedMbit, actualMbit, "Size in Mbit must be ROM size / 131072");
    }

    @Property
    void multiSplitFlagSetWhenNotLastPart(@ForAll("snesRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, false, (byte) 0x40);

        assertEquals(0x40, header[2] & 0xFF, "Multi/split flag must be 0x40 when not last part");
    }

    @Property
    void multiSplitFlagNotSetWhenLastPart(@ForAll("snesRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        assertEquals(0, header[2] & 0xFF, "Multi/split flag must be 0 when last part");
    }

    @Property
    void usesSramFlagSetCorrectly(@ForAll("snesRom") SnesRom rom) {
        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        int expectedFlag = rom.sramSize() > 0 ? 1 : 0;
        int actualFlag = header[16] & 0xFF;

        assertEquals(expectedFlag, actualFlag, "Uses SRAM flag must be 1 when SRAM > 0, else 0");
    }

    @Property
    void sizeIn8KBBlocksCalculatedCorrectly(@ForAll("snesRom") SnesRom rom) {
        int partSize = 512 * 1024;
        byte[] header = generator.generateHeader(rom, partSize, 0, true, (byte) 0x00);

        int expectedBlocks = partSize / 8192;
        int actualBlocks = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);

        assertEquals(expectedBlocks, actualBlocks, "Size in 8KB blocks must be part size / 8192");
    }

    @Test
    void chronoTrigger32MbitRomHasCorrectSizeCode() {
        int chronoTriggerSize = 4 * 1024 * 1024;
        byte[] data = new byte[chronoTriggerSize];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "CHRONO TRIGGER", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);

        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        int sizeMbit = header[17] & 0xFF;
        assertEquals(32, sizeMbit, "32 Mbit ROM (4MB) should have size code 32");
    }

    @Test
    void header8MbitRomHasCorrectSizeCode() {
        int size8Mbit = 1024 * 1024;
        byte[] data = new byte[size8Mbit];
        SnesRom rom = new SnesRom(data, RomType.LoROM, 0, "TEST ROM", false, (byte) 0, (byte) 0, (byte) 0, 0, 0);

        byte[] header = generator.generateHeader(rom, 512 * 1024, 0, true, (byte) 0x00);

        int sizeMbit = header[17] & 0xFF;
        assertEquals(8, sizeMbit, "8 Mbit ROM (1MB) should have size code 8");
    }

    @Provide
    Arbitrary<SnesRom> snesRom() {
        return Combinators.combine(
                Arbitraries.of(RomType.LoROM, RomType.HiROM, RomType.ExHiROM),
                romSize(),
                sramSize(),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> hiRomRom() {
        return Combinators.combine(
                Arbitraries.of(RomType.HiROM, RomType.ExHiROM),
                romSize(),
                sramSize(),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> loRomRom() {
        return Combinators.combine(
                Arbitraries.just(RomType.LoROM),
                romSize(),
                sramSize(),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> romWithNoSram() {
        return Combinators.combine(
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                romSize(),
                Arbitraries.just(0),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> romWithSmallSram() {
        return Combinators.combine(
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                romSize(),
                Arbitraries.integers().between(1, 2048),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> romWithMediumSram() {
        return Combinators.combine(
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                romSize(),
                Arbitraries.integers().between(2049, 8192),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> romWithLargeSram() {
        return Combinators.combine(
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                romSize(),
                Arbitraries.integers().between(8193, 32768),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> romWithVeryLargeSram() {
        return Combinators.combine(
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                romSize(),
                Arbitraries.integers().between(32769, 262144),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> hiRomWithSram() {
        return Combinators.combine(
                Arbitraries.of(RomType.HiROM, RomType.ExHiROM),
                romSize(),
                Arbitraries.integers().between(16384, 262144),
                hasDsp()
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> loRomWithSram() {
        return Combinators.combine(
                Arbitraries.just(RomType.LoROM),
                romSize(),
                Arbitraries.integers().between(16384, 262144),
                Arbitraries.just(false)
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> loRomWithDspNoSram() {
        return Combinators.combine(
                Arbitraries.just(RomType.LoROM),
                romSize(),
                Arbitraries.just(0),
                Arbitraries.just(true)
        ).as(this::createRom);
    }

    @Provide
    Arbitrary<SnesRom> loRomWithoutSramOrDsp() {
        return Combinators.combine(
                Arbitraries.just(RomType.LoROM),
                romSize(),
                Arbitraries.just(0),
                Arbitraries.just(false)
        ).as(this::createRom);
    }

    private Arbitrary<Integer> romSize() {
        return Arbitraries.integers()
                .between(1, 8)
                .map(mbit -> mbit * 131072);
    }

    private Arbitrary<Integer> sramSize() {
        return Arbitraries.of(0, 2048, 8192, 16384, 32768, 65536, 131072, 262144);
    }

    private Arbitrary<Boolean> hasDsp() {
        return Arbitraries.of(true, false);
    }

    private SnesRom createRom(RomType type, int size, int sramSize, boolean hasDsp) {
        byte[] data = new byte[size];
        return new SnesRom(
                data,
                type,
                sramSize,
                "TEST ROM",
                hasDsp,
                (byte) 0,
                (byte) 0,
                (byte) 0,
                0,
                0
        );
    }
}
