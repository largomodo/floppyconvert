package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import net.jqwik.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Gd3HeaderGeneratorTest {

    private final Gd3HeaderGenerator generator = new Gd3HeaderGenerator();

    @Property(tries = 100)
    void headerNotGeneratedForNonZeroSplitPartIndex(@ForAll("nonZeroSplitPartIndex") int splitPartIndex) {
        SnesRom rom = createTestRom(RomType.LoROM, 4 * 1024 * 1024, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, splitPartIndex, false, (byte) 0x00);

        assertEquals(0, header.length, "Header should be empty for splitPartIndex > 0");
    }

    @Property(tries = 100)
    void headerGeneratedFor512BytesWhenSplitPartIndexIsZero(
            @ForAll("romTypes") RomType romType,
            @ForAll("megabytes") int megabytes
    ) {
        SnesRom rom = createTestRom(romType, megabytes * 1024 * 1024, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals(512, header.length, "Header should be 512 bytes for splitPartIndex == 0");
    }

    @Property(tries = 100)
    void headerContainsGameDoctorIdString(
            @ForAll("romTypes") RomType romType,
            @ForAll("megabytes") int megabytes
    ) {
        SnesRom rom = createTestRom(romType, megabytes * 1024 * 1024, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] idBytes = Arrays.copyOfRange(header, 0, 16);
        String id = new String(idBytes, StandardCharsets.US_ASCII);
        assertEquals("GAME DOCTOR SF 3", id, "Header should contain 'GAME DOCTOR SF 3' ID");
    }

    @Property(tries = 100)
    void sramSizeCodeCorrectFor64KB() {
        SnesRom rom = createTestRom(RomType.LoROM, 4 * 1024 * 1024, 8192, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x81, header[16], "SRAM size code should be 0x81 for 64KB (8192 bytes)");
    }

    @Property(tries = 100)
    void sramSizeCodeCorrectFor16KB() {
        SnesRom rom = createTestRom(RomType.LoROM, 4 * 1024 * 1024, 2048, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x82, header[16], "SRAM size code should be 0x82 for 16KB (2048 bytes)");
    }

    @Property(tries = 100)
    void sramSizeCodeDefaultForOtherSizes(@ForAll("sramSizesOther") int sramSize) {
        SnesRom rom = createTestRom(RomType.LoROM, 4 * 1024 * 1024, sramSize, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x80, header[16], "SRAM size code should be 0x80 for other sizes");
    }

    @Property(tries = 100)
    void hiRom8MBUsesCorrectMemoryMap() {
        int size = 1024 * 1024;
        SnesRom rom = createTestRom(RomType.HiROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "HiROM 1MB (<=2 parts) should use MAP_HI_8MB");
    }

    @Property(tries = 100)
    void hiRom16MBUsesCorrectMemoryMap() {
        int size = 2 * 1024 * 1024;
        SnesRom rom = createTestRom(RomType.HiROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
                0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
                0x22, 0x23, 0x22, 0x23, 0x22, 0x23, 0x22, 0x23
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "HiROM 2MB (>2, <=4 parts) should use MAP_HI_16MB");
    }

    @Property(tries = 100)
    void hiRom24MBUsesCorrectMemoryMap() {
        int size = 3 * 1024 * 1024;
        SnesRom rom = createTestRom(RomType.HiROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
                0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
                0x24, 0x25, 0x23, 0x00, 0x24, 0x25, 0x23, 0x00
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "HiROM 3MB (>4, <=6 parts) should use MAP_HI_24MB");
    }

    @Property(tries = 100)
    void hiRom32MBUsesCorrectMemoryMap() {
        // 32 Mbit = 4 MB
        int size = 4 * 1024 * 1024;
        SnesRom rom = createTestRom(RomType.HiROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
                0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "HiROM 32Mbit should use MAP_HI_32MB");
    }

    @Property(tries = 100)
    void loRom4MBUsesCorrectMemoryMap() {
        int size = 512 * 1024;
        SnesRom rom = createTestRom(RomType.LoROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
                0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "LoROM 512KB (<=1 part) should use MAP_LO_4MB");
    }

    @Property(tries = 100)
    void loRom8MBUsesCorrectMemoryMap() {
        int size = 1024 * 1024;
        SnesRom rom = createTestRom(RomType.LoROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
                0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
                0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "LoROM 1MB (>1, <=2 parts) should use MAP_LO_8MB");
    }

    @Property(tries = 100)
    void loRom16MBUsesCorrectMemoryMap() {
        int size = 2 * 1024 * 1024;
        SnesRom rom = createTestRom(RomType.LoROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "LoROM 2MB (>2, <=4 parts) should use MAP_LO_16MB");
    }

    @Property(tries = 100)
    void loRom32MBUsesCorrectMemoryMap() {
        // 32 Mbit = 4 MB
        int size = 4 * 1024 * 1024;
        SnesRom rom = createTestRom(RomType.LoROM, size, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "LoROM 32Mbit should use MAP_LO_32MB");
    }

    @Property(tries = 100)
    void hiRomSramMappingSetWhenSramPresent(@ForAll("positiveSramSize") int sramSize) {
        SnesRom rom = createTestRom(RomType.HiROM, 2 * 1024 * 1024, sramSize, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x0C, header[0x29], "HiROM SRAM mapping should be 0x0C at offset 0x29");
        assertEquals((byte) 0x0C, header[0x2A], "HiROM SRAM mapping should be 0x0C at offset 0x2A");
    }

    @Property(tries = 100)
    void hiRomSramMappingNotSetWhenNoSram() {
        SnesRom rom = createTestRom(RomType.HiROM, 2 * 1024 * 1024, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x00, header[0x29], "HiROM SRAM mapping should be 0x00 at offset 0x29 when no SRAM");
        assertEquals((byte) 0x00, header[0x2A], "HiROM SRAM mapping should be 0x00 at offset 0x2A when no SRAM");
    }

    @Property(tries = 100)
    void loRomDspFlagSetWhenHasDsp() {
        SnesRom rom = createTestRom(RomType.LoROM, 1024 * 1024, 0, true);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x60, header[0x14], "LoROM DSP flag should be 0x60 at offset 0x14");
        assertEquals((byte) 0x60, header[0x1C], "LoROM DSP flag should be 0x60 at offset 0x1C");
    }

    @Property(tries = 100)
    void loRomDspFlagNotSetWhenNoDsp() {
        SnesRom rom = createTestRom(RomType.LoROM, 1024 * 1024, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertNotEquals((byte) 0x60, header[0x14], "LoROM DSP flag should not be 0x60 at offset 0x14 when no DSP");
        assertNotEquals((byte) 0x60, header[0x1C], "LoROM DSP flag should not be 0x60 at offset 0x1C when no DSP");
    }

    @Property(tries = 100)
    void loRomSramMappingSetWhenSramPresent(@ForAll("positiveSramSize") int sramSize) {
        SnesRom rom = createTestRom(RomType.LoROM, 1024 * 1024, sramSize, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x40, header[0x24], "LoROM SRAM mapping should be 0x40 at offset 0x24");
        assertEquals((byte) 0x40, header[0x28], "LoROM SRAM mapping should be 0x40 at offset 0x28");
    }

    @Property(tries = 100)
    void loRomSramMappingNotSetWhenNoSram() {
        SnesRom rom = createTestRom(RomType.LoROM, 1024 * 1024, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertNotEquals((byte) 0x40, header[0x24], "LoROM SRAM mapping should not be 0x40 at offset 0x24 when no SRAM");
        assertNotEquals((byte) 0x40, header[0x28], "LoROM SRAM mapping should not be 0x40 at offset 0x28 when no SRAM");
    }

    @Example
    void chronoTriggerExampleTest() {
        // 32 Mbit = 4 MB
        int chronoTriggerSize = 4 * 1024 * 1024;
        SnesRom rom = createTestRom(RomType.HiROM, chronoTriggerSize, 8192, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals(512, header.length, "Header should be 512 bytes");

        String id = new String(Arrays.copyOfRange(header, 0, 16), StandardCharsets.US_ASCII);
        assertEquals("GAME DOCTOR SF 3", id, "Should contain Game Doctor ID");

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
                0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
                0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27
        };
        byte[] actualMap = Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length);
        assertArrayEquals(expectedMap, actualMap, "Should use MAP_HI_32MB for Chrono Trigger");

        assertEquals((byte) 0x81, header[16], "SRAM size code should be 0x81 for 64KB");
        assertEquals((byte) 0x0C, header[0x29], "HiROM SRAM mapping at 0x29");
        assertEquals((byte) 0x0C, header[0x2A], "HiROM SRAM mapping at 0x2A");
    }

    @Provide
    Arbitrary<RomType> romTypes() {
        return Arbitraries.of(RomType.LoROM, RomType.HiROM, RomType.ExHiROM);
    }

    @Provide
    Arbitrary<Integer> nonZeroSplitPartIndex() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> megabytes() {
        return Arbitraries.integers().between(1, 8);
    }

    @Provide
    Arbitrary<Integer> sramSizesOther() {
        return Arbitraries.integers().between(0, 32768).filter(size -> size != 8192 && size != 2048);
    }

    @Provide
    Arbitrary<Integer> positiveSramSize() {
        return Arbitraries.integers().between(1, 32768);
    }

    private SnesRom createTestRom(RomType type, int size, int sramSize, boolean hasDsp) {
        byte[] rawData = new byte[size];
        return new SnesRom(
                rawData,
                type,
                sramSize,
                "TEST TITLE",
                hasDsp,
                (byte) 0x00,  // region
                (byte) 0x00,  // maker
                (byte) 0x00,  // version
                0,            // checksum
                0             // complement
        );
    }
}
