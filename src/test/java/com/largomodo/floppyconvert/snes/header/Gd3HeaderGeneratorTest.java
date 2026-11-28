// createRom helpers use checksum=0x0001, complement=0xFFFE to satisfy the SnesRom invariant. (ref: DL-003)
package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesRom;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

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

    private SnesRom loRomWithChipset(int chipset) {
        byte[] rawData = new byte[1024 * 1024];
        rawData[0x7FB0 + 0x26] = (byte) chipset;
        return new SnesRom(rawData, RomType.LoROM, 0, "TEST TITLE", chipset == 0x14, 0, 0, 0, 0x0001, 0xFFFE);
    }

    private SnesRom loRom96(int sramSize, String id) {
        byte[] data = new byte[96 * SnesConstants.MBIT];
        System.arraycopy(id.getBytes(StandardCharsets.US_ASCII), 0, data, 0x7FB0, id.length());
        return new SnesRom(data, RomType.LoROM, sramSize, "TEST 96", false, 0, 0, 0, 0x0001, 0xFFFE);
    }

    @Test
    // A 96 Mbit LoROM fills 24 units, so its memory map is sequential from 0x20 and the LoROM SRAM bytes do not apply.
    void loRom96MbitUsesSequentialMap() {
        byte[] header = generator.generateHeader(loRom96(0, "XXXXX"), 8 * SnesConstants.MBIT, 0, false, (byte) 0x00);

        for (int i = 0; i < 24; i++) {
            assertEquals(0x20 + i, header[0x11 + i] & 0xFF, "map entry " + i);
        }
        assertEquals((byte) 0x80, header[0x10]);
        assertNotEquals(0x40, header[0x24] & 0xFF, "LoROM SRAM bytes do not apply to the 96 Mbit class");
    }

    @Test
    void starOcean96SetsItsSramBytes() {
        byte[] header = generator.generateHeader(loRom96(8192, "B4ARF"), 8 * SnesConstants.MBIT, 0, false, (byte) 0x00);

        assertEquals(0x81, header[0x10] & 0xFF);
        assertEquals(0x00, header[0x24] & 0xFF, "map entry 19");
        assertEquals(0x08, header[0x29] & 0xFF);
    }

    private static byte[] realHeader(String resource) throws java.io.IOException {
        try (java.io.InputStream in = Gd3HeaderGeneratorTest.class.getResourceAsStream(resource)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(in != null, "real fixture absent: " + resource);
            return in.readNBytes(512);
        }
    }

    @Test
    void headerForTheStarOceanSfcEqualsTheHardwareVerifiedGd3Header(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws java.io.IOException {
        byte[] expected = realHeader("/snes/SF96soe");
        try (java.io.InputStream in = Gd3HeaderGeneratorTest.class.getResourceAsStream(
                "/snes/Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.sfc")) {
            org.junit.jupiter.api.Assumptions.assumeTrue(in != null, "Star Ocean .sfc absent");
            java.nio.file.Path sfc = tempDir.resolve("star-ocean.sfc");
            java.nio.file.Files.copy(in, sfc);
            SnesRom rom = new com.largomodo.floppyconvert.snes.SnesRomReader()
                    .load(sfc, com.largomodo.floppyconvert.format.CopierFormat.GD3);

            assertArrayEquals(expected, generator.generateHeader(rom, 8 * SnesConstants.MBIT, 0, false, (byte) 0x40));
        }
    }

    @Test
    // The DSP bytes follow the raw chipset byte, so SuperFX gets none and the Seta DSP (0xF6) does.
    void superFxLoRomGetsNoDspBytes() {
        byte[] header = generator.generateHeader(loRomWithChipset(0x14), 1024 * 1024, 0, false, (byte) 0x00);

        assertNotEquals((byte) 0x60, header[0x14]);
        assertNotEquals((byte) 0x60, header[0x1C]);
    }

    @Test
    void setaDspLoRomGetsDspBytes() {
        byte[] header = generator.generateHeader(loRomWithChipset(0xF6), 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals((byte) 0x60, header[0x14]);
        assertEquals((byte) 0x60, header[0x1C]);
    }

    @Test
    void twentyMbitHiRomUsesThe24MbitMap() {
        SnesRom rom = createTestRom(RomType.HiROM, 20 * SnesConstants.MBIT, 0, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
                0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
                0x24, 0x25, 0x23, 0x00, 0x24, 0x25, 0x23, 0x00
        };
        assertArrayEquals(expectedMap, Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length),
                "A 20 Mbit HiROM is padded to 24 Mbit and selects MAP_HI_24MB");
    }

    @Test
    void extendedHiRom48MbitUsesMap48() {
        SnesRom rom = new SnesRom(new byte[48 * SnesConstants.MBIT], RomType.HiROM, 0, "TEST TITLE", false,
                0, 0, 0, 0x0001, 0xFFFE, SnesRom.EXTENDED_BASE, false, false);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        byte[] expectedMap = new byte[]{
                0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
                0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27,
                0x22, 0x23, 0x22, 0x23, 0x28, 0x29, 0x2A, 0x2B
        };
        assertArrayEquals(expectedMap, Arrays.copyOfRange(header, 0x11, 0x11 + expectedMap.length),
                "Extended HiROM 48 Mbit should use MAP_HI_48MB");
    }

    @Provide
    Arbitrary<RomType> romTypes() {
        return Arbitraries.of(RomType.LoROM, RomType.HiROM);
    }

    @Provide
    Arbitrary<Integer> nonZeroSplitPartIndex() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> megabytes() {
        // 4 MB = 32 Mbit: the largest size both HiROM and LoROM convert without an extended header
        return Arbitraries.integers().between(1, 4);
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
        if (hasDsp) {
            // The generator reads the raw chipset byte (internal header offset 0x26), not hasDsp()
            rawData[(type == RomType.HiROM ? 0xFFB0 : 0x7FB0) + 0x26] = 0x03;
        }
        return new SnesRom(
                rawData,
                type,
                sramSize,
                "TEST TITLE",
                hasDsp,
                (byte) 0x00,  // region
                (byte) 0x00,  // maker
                (byte) 0x00,  // version
                0x0001,       // checksum
                0xFFFE        // complement
        );
    }

    private SnesRom extendedRom(int sizeMbit, int sramSize, int maker, String id) {
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        byte[] idBytes = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idBytes, 0, data, SnesRom.EXTENDED_BASE + 0xFFB0, idBytes.length);
        return new SnesRom(data, RomType.HiROM, sramSize, "EXTENDED TITLE", false, 0, maker, 0, 0x0001, 0xFFFE,
                SnesRom.EXTENDED_BASE, false, false);
    }

    @Test
    // The special SRAM bytes apply only when the extended header, maker and ID bytes all match.
    void talesOfPhantasiaSramBytesNeedExtendedHeaderMakerAndId() {
        byte[] header = generator.generateHeader(extendedRom(48, 8192, 0x33, "AFATV"), 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals(0x40, header[0x17] & 0xFF);
        assertEquals(0x40, header[0x18] & 0xFF);
        assertEquals(0x40, header[0x23] & 0xFF);
        assertEquals(0x40, header[0x24] & 0xFF);
        assertEquals(0x00, header[0x29] & 0xFF);
        assertEquals(0x0F, header[0x2A] & 0xFF);
    }

    @Test
    void daiKaijuMonogatari2SetsOnlyTheSramMap() {
        byte[] header = generator.generateHeader(extendedRom(40, 8192, 0x33, "18AE6"), 1024 * 1024, 0, false, (byte) 0x00);

        assertNotEquals(0x40, header[0x17] & 0xFF);
        assertEquals(0x00, header[0x29] & 0xFF);
        assertEquals(0x0F, header[0x2A] & 0xFF);
    }

    @Test
    void trialsOfManaGetsTheOrdinaryHiRomSramBytes() {
        byte[] header = generator.generateHeader(extendedRom(48, 8192, 0x33, "AFZJ6"), 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals(0x0C, header[0x29] & 0xFF);
        assertEquals(0x0C, header[0x2A] & 0xFF);
        assertNotEquals(0x40, header[0x17] & 0xFF);
    }

    @Test
    void talesOfPhantasiaIdWithoutExtendedHeaderGetsTheOrdinaryBytes() {
        byte[] data = new byte[8 * SnesConstants.MBIT];
        System.arraycopy("AFATV".getBytes(StandardCharsets.US_ASCII), 0, data, 0xFFB0, 5);
        SnesRom rom = new SnesRom(data, RomType.HiROM, 8192, "TITLE", false, 0, 0x33, 0, 0x0001, 0xFFFE);

        byte[] header = generator.generateHeader(rom, 1024 * 1024, 0, false, (byte) 0x00);

        assertEquals(0x0C, header[0x29] & 0xFF);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"40,0x22", "48,0x24", "64,0x28"})
    void extendedMapsAreSelectedBySize(int sizeMbit, String markerHex) {
        byte[] header = generator.generateHeader(extendedRom(sizeMbit, 0, 0x00, "XXXXX"), 1024 * 1024, 0, false, (byte) 0x00);

        // 9th map byte (offset 0x11 + 8) differs between MAP_HI_40MB (0x22), MAP_HI_48MB (0x24) and MAP_HI_64MB (0x28)
        assertEquals(Integer.decode(markerHex), header[0x11 + 8] & 0xFF);
    }
}
