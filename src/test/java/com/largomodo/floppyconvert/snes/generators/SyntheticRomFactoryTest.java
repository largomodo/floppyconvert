package com.largomodo.floppyconvert.snes.generators;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticRomFactoryTest {

    private static final int LOROM_HEADER_OFFSET = 0x7FB0;
    private static final int HIROM_HEADER_OFFSET = 0xFFB0;
    private static final int EXHIROM_HEADER_OFFSET = 0x40FFB0;
    private static final int TITLE_OFFSET = 0x10;
    private static final int TITLE_LENGTH = 21;
    private static final int BLOCK_BYTES = 0x8000;

    private final SnesRomReader reader = new SnesRomReader();

    @Property(tries = 30)
    void bodyBlocksAreDistinctAndHeaderRoundTrips(@ForAll("blockUniquenessSizes") int sizeMbit,
                                                  @ForAll boolean hiRom) throws IOException {
        Path tempDir = Files.createTempDirectory("block-uniqueness-test-");
        try {
            Path romPath = hiRom
                    ? SyntheticRomFactory.generateHiRom(sizeMbit, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, tempDir)
                    : SyntheticRomFactory.generateLoRom(sizeMbit, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            Set<ByteBuffer> blocks = new HashSet<>();
            for (int offset = 0; offset < data.length; offset += BLOCK_BYTES) {
                byte[] block = Arrays.copyOfRange(data, offset, offset + BLOCK_BYTES);
                assertTrue(blocks.add(ByteBuffer.wrap(block)),
                        "32 KB block at offset " + offset + " repeats an earlier block");
            }

            SnesRom rom = reader.load(romPath);
            assertEquals(hiRom ? RomType.HiROM : RomType.LoROM, rom.type(), "Detected type must match the requested type");
            assertEquals((hiRom ? "SYNHI" : "SYNLO") + sizeMbit + "T0", rom.title());
            assertEquals(0, rom.sramSize());
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property
    void customTitleAppearsAtCorrectHeaderOffsetForLoRom(@ForAll("validSizes") int sizeMbit,
                                                         @ForAll("validSramSizes") int sramSizeKb) throws IOException {
        Path tempDir = Files.createTempDirectory("lorom-custom-title-test-");
        try {
            String customTitle = "CUSTOM LOROM TITLE";

            Path romPath = SyntheticRomFactory.generateLoRom(sizeMbit, sramSizeKb, DspChipset.ABSENT, customTitle, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            String actualTitle = extractTitle(data, LOROM_HEADER_OFFSET + TITLE_OFFSET);

            assertEquals(customTitle, actualTitle.trim(),
                    "LoROM custom title should appear at offset 0x7FB0 + 0x10 = 0x7FC0");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property
    void customTitleAppearsAtCorrectHeaderOffsetForHiRom(@ForAll("validSizes") int sizeMbit,
                                                         @ForAll("validSramSizes") int sramSizeKb) throws IOException {
        Path tempDir = Files.createTempDirectory("hirom-custom-title-test-");
        try {
            String customTitle = "CUSTOM HIROM TITLE";

            Path romPath = SyntheticRomFactory.generateHiRom(sizeMbit, sramSizeKb, DspChipset.ABSENT, customTitle, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            String actualTitle = extractTitle(data, HIROM_HEADER_OFFSET + TITLE_OFFSET);

            assertEquals(customTitle, actualTitle.trim(),
                    "HiROM custom title should appear at offset 0xFFB0 + 0x10 = 0xFFC0");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property
    void customTitleAppearsAtCorrectHeaderOffsetForExHiRom(@ForAll("exHiRomValidSizes") int sizeMbit) throws IOException {
        Path tempDir = Files.createTempDirectory("exhirom-custom-title-test-");
        try {
            String customTitle = "CUSTOM EXHIROM TITLE";

            Path romPath = SyntheticRomFactory.generateExHiRom(sizeMbit, 0, DspChipset.ABSENT, customTitle, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            String actualTitle = extractTitle(data, EXHIROM_HEADER_OFFSET + TITLE_OFFSET);

            assertEquals(customTitle, actualTitle.trim(),
                    "extended HiROM custom title should appear at offset 0x40FFB0 + 0x10 = 0x40FFC0");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property
    void autoGeneratedTitleFollowsPatternForLoRom(@ForAll("validSizes") int sizeMbit,
                                                  @ForAll("validSramSizes") int sramSizeKb,
                                                  @ForAll("dspStatus") DspChipset dsp) throws IOException {
        Path tempDir = Files.createTempDirectory("lorom-auto-title-test-");
        try {
            Path romPath = SyntheticRomFactory.generateLoRom(sizeMbit, sramSizeKb, dsp, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            String actualTitle = extractTitle(data, LOROM_HEADER_OFFSET + TITLE_OFFSET);

            String dspSuffix = (dsp == DspChipset.PRESENT) ? "D" : "";
            String expectedTitle = String.format("SYNLO%dT%d%s", sizeMbit, sramSizeKb, dspSuffix);

            assertEquals(expectedTitle, actualTitle.trim(),
                    "LoROM auto-generated title should follow pattern SYNLO{size}T{sram}{dsp}");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property
    void autoGeneratedTitleFollowsPatternForHiRom(@ForAll("validSizes") int sizeMbit,
                                                  @ForAll("validSramSizes") int sramSizeKb,
                                                  @ForAll("dspStatus") DspChipset dsp) throws IOException {
        Path tempDir = Files.createTempDirectory("hirom-auto-title-test-");
        try {
            Path romPath = SyntheticRomFactory.generateHiRom(sizeMbit, sramSizeKb, dsp, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            String actualTitle = extractTitle(data, HIROM_HEADER_OFFSET + TITLE_OFFSET);

            String dspSuffix = (dsp == DspChipset.PRESENT) ? "D" : "";
            String expectedTitle = String.format("SYNHI%dT%d%s", sizeMbit, sramSizeKb, dspSuffix);

            assertEquals(expectedTitle, actualTitle.trim(),
                    "HiROM auto-generated title should follow pattern SYNHI{size}T{sram}{dsp}");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property
    void autoGeneratedTitleFollowsPatternForExHiRom(@ForAll("exHiRomValidSizes") int sizeMbit,
                                                    @ForAll("dspStatus") DspChipset dsp) throws IOException {
        Path tempDir = Files.createTempDirectory("exhirom-auto-title-test-");
        try {
            String dspSuffix = (dsp == DspChipset.PRESENT) ? "D" : "";
            String title = String.format("SYNEX%dT0%s", sizeMbit, dspSuffix);

            Path romPath = SyntheticRomFactory.generateExHiRom(sizeMbit, 0, dsp, title, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            String actualTitle = extractTitle(data, EXHIROM_HEADER_OFFSET + TITLE_OFFSET);

            assertEquals(title, actualTitle.trim(),
                    "extended HiROM auto-generated title should follow pattern SYNEX{size}T{sram}{dsp}");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Test
    void exHiRomHeaderAt0x40FFB0DetectedBySnesRomReader(@TempDir Path tempDir) throws IOException {
        Path romPath = SyntheticRomFactory.generateExHiRom(48, 0, DspChipset.ABSENT, "SYNEX48T0", tempDir);

        SnesRom rom = reader.load(romPath, CopierFormat.GD3);

        assertEquals(RomType.HiROM, rom.type(),
                "SnesRomReader should detect HiROM for the extended header at 0x40FFB0");
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase(),
                "The 0x40FFB0 checksum pair should set the extended header base");
        assertEquals("SYNEX48T0", rom.title().trim(),
                "extended HiROM title should be correctly parsed");
    }

    @Test
    void exHiRom64MbitMaximumSizeGeneratesValidRom(@TempDir Path tempDir) throws IOException {
        Path romPath = SyntheticRomFactory.generateExHiRom(64, 0, DspChipset.ABSENT, "SYNEX64T0", tempDir);

        assertTrue(Files.exists(romPath), "extended HiROM file should be created");

        SnesRom rom = reader.load(romPath, CopierFormat.GD3);
        assertEquals(RomType.HiROM, rom.type(), "64Mbit extended HiROM should be valid");
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase());

        int expectedSize = 64 * 1024 * 1024 / 8;
        assertEquals(expectedSize, Files.size(romPath),
                "64Mbit ROM should be 8MB (64 megabits / 8 bits per byte)");
    }

    @Test
    void exHiRomBelowMinimumSizeThrowsIllegalArgumentException(@TempDir Path tempDir) {
        assertDoesNotThrow(() ->
                        SyntheticRomFactory.generateExHiRom(40, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM at 40Mbit is the smallest valid size");

        assertThrows(IllegalArgumentException.class, () ->
                        SyntheticRomFactory.generateExHiRom(39, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM below 40Mbit should throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () ->
                        SyntheticRomFactory.generateExHiRom(32, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM at 32Mbit should throw IllegalArgumentException");
    }

    @Test
    void exLoRomHeaderAt0x407FB0DetectedBySnesRomReader(@TempDir Path tempDir) throws IOException {
        Path romPath = SyntheticRomFactory.generateExLoRom(48, 0, DspChipset.ABSENT, "SYNEXLO48", tempDir);

        SnesRom rom = reader.load(romPath, CopierFormat.GD3);

        assertEquals(RomType.LoROM, rom.type());
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase());
        assertEquals("SYNEXLO48", rom.title());
    }

    @Test
    void exLoRomOutsideSizeRangeThrowsIllegalArgumentException(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () ->
                SyntheticRomFactory.generateExLoRom(39, 0, DspChipset.ABSENT, "TEST", tempDir));
        assertThrows(IllegalArgumentException.class, () ->
                SyntheticRomFactory.generateExLoRom(65, 0, DspChipset.ABSENT, "TEST", tempDir));
    }

    @Test
    void exHiRomAboveMaximumSizeThrowsIllegalArgumentException(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () ->
                        SyntheticRomFactory.generateExHiRom(72, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM above 64Mbit should throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () ->
                        SyntheticRomFactory.generateExHiRom(96, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM at 96Mbit should throw IllegalArgumentException");
    }

    @Test
    void exHiRomZeroSizeThrowsIllegalArgumentException(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () ->
                        SyntheticRomFactory.generateExHiRom(0, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM with 0 size should throw IllegalArgumentException");
    }

    @Test
    void exHiRomNegativeSizeThrowsIllegalArgumentException(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () ->
                        SyntheticRomFactory.generateExHiRom(-8, 0, DspChipset.ABSENT, "TEST", tempDir),
                "extended HiROM with negative size should throw IllegalArgumentException");
    }

    @Property
    void generatedRomPassesChecksumValidation(@ForAll("validSizes") int sizeMbit,
                                              @ForAll("validSramSizes") int sramSizeKb) throws IOException {
        Path tempDir = Files.createTempDirectory("checksum-test-");
        try {
            Path romPath = SyntheticRomFactory.generateLoRom(sizeMbit, sramSizeKb, DspChipset.ABSENT, tempDir);

            byte[] data = Files.readAllBytes(romPath);
            int headerOffset = LOROM_HEADER_OFFSET;

            int complement = readWord(data, headerOffset + 0x2C);
            int checksum = readWord(data, headerOffset + 0x2E);

            assertEquals(0xFFFF, complement + checksum,
                    "Checksum validation: complement + checksum should equal 0xFFFF");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private String extractTitle(byte[] data, int offset) {
        byte[] titleBytes = new byte[TITLE_LENGTH];
        System.arraycopy(data, offset, titleBytes, 0, TITLE_LENGTH);
        return new String(titleBytes, StandardCharsets.US_ASCII);
    }

    private int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore cleanup failures in tests
                        }
                    });
        }
    }

    @Provide
    Arbitrary<Integer> validSizes() {
        return Arbitraries.integers().between(4, 32);
    }

    @Provide
    Arbitrary<Integer> exHiRomValidSizes() {
        return Arbitraries.integers().between(40, 64);
    }

    @Provide
    Arbitrary<Integer> blockUniquenessSizes() {
        return Arbitraries.integers().between(2, 32);
    }

    @Provide
    Arbitrary<Integer> validSramSizes() {
        return Arbitraries.of(
                SyntheticRomFactory.SRAM_0KB,
                SyntheticRomFactory.SRAM_2KB,
                SyntheticRomFactory.SRAM_8KB,
                SyntheticRomFactory.SRAM_64KB,
                SyntheticRomFactory.SRAM_256KB
        );
    }

    @Provide
    Arbitrary<DspChipset> dspStatus() {
        return Arbitraries.of(DspChipset.PRESENT, DspChipset.ABSENT);
    }
}
