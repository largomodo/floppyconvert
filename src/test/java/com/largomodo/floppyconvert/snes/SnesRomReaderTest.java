package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.E2ETestRomRegistry;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.generators.ReferenceInterleaver;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SnesRomReaderTest {

    enum Kind {
        LOROM_8(false, false, Gd3Interleave.NONE),
        HIROM_16(true, false, Gd3Interleave.GLOBAL),
        HIROM_24(true, false, Gd3Interleave.TWO_REGION_24),
        EXTENDED_HIROM_48(true, true, Gd3Interleave.EXTENDED),
        EXTENDED_LOROM_48(false, true, Gd3Interleave.NONE);

        final boolean hiRom;
        final boolean extended;
        final Gd3Interleave nativeScheme;

        Kind(boolean hiRom, boolean extended, Gd3Interleave nativeScheme) {
            this.hiRom = hiRom;
            this.extended = extended;
            this.nativeScheme = nativeScheme;
        }
    }

    enum CopierHeader {NONE, SMC, GD3}

    private final SnesRomReader reader = new SnesRomReader();
    @TempDir
    Path tempDir;

    // The GD3 pipeline over (type, extended) x (no header, SMC header, GD3 header) x (linear, interleaved):
    // detection and the linear body must not depend on the copier header or the source interleave. (ref: DL-002, DL-019)
    static Stream<Arguments> gd3PipelineMatrix() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (Kind kind : Kind.values()) {
            for (CopierHeader header : CopierHeader.values()) {
                cases.add(Arguments.of(kind, header, false));
                // Synthetic extended images carry map byte 0x25, which the interleave probe does not accept;
                // real interleaved extended dumps are covered by the sf48Tal fixture test.
                if (kind.hiRom && !kind.extended) {
                    cases.add(Arguments.of(kind, header, true));
                }
            }
        }
        return cases.build();
    }

    @ParameterizedTest(name = "{0} / {1} header / interleaved={2}")
    @MethodSource("gd3PipelineMatrix")
    void gd3PipelineRecoversLinearBodyAndDetection(Kind kind, CopierHeader header, boolean interleaved) throws IOException {
        byte[] linear = Files.readAllBytes(generate(kind));
        byte[] body = linear;
        if (interleaved) {
            // ucon64 uses the Game Doctor tables only for GD3 and headerless files; any other copier header means GLOBAL
            Gd3Interleave scheme = header == CopierHeader.SMC ? Gd3Interleave.GLOBAL : kind.nativeScheme;
            body = ReferenceInterleaver.interleave(linear, scheme);
        }
        Path file = tempDir.resolve("matrix.sfc");
        Files.write(file, withHeader(body, header));

        SnesRom rom = reader.load(file, CopierFormat.GD3);

        assertEquals(kind.hiRom ? RomType.HiROM : RomType.LoROM, rom.type());
        assertEquals(kind.extended ? SnesRom.EXTENDED_BASE : 0, rom.headerBase());
        assertEquals(interleaved, rom.interleavedSource());
        assertEquals(header == CopierHeader.GD3, rom.gd3Headered());
        assertArrayEquals(linear, rom.rawData(), "rawData must be the linear body");
    }

    @Property(tries = 20)
    void detectionIsInvariantUnderAddingACopierHeader(@ForAll Kind kind, @ForAll CopierHeader header) throws IOException {
        // jqwik properties do not get JUnit's @TempDir injection, so the property owns its directory
        Path dir = Files.createTempDirectory("reader-header-invariance-");
        byte[] linear = Files.readAllBytes(generate(kind, dir));
        Path bare = dir.resolve("bare-" + kind + header + ".sfc");
        Path headered = dir.resolve("headered-" + kind + header + ".sfc");
        Files.write(bare, linear);
        Files.write(headered, withHeader(linear, header));

        for (CopierFormat format : CopierFormat.values()) {
            SnesRom a = reader.load(bare, format);
            SnesRom b = reader.load(headered, format);
            assertEquals(a.type(), b.type(), format + " type");
            assertEquals(a.headerBase(), b.headerBase(), format + " header base");
            assertEquals(a.title(), b.title(), format + " title");
            assertEquals(a.rawData().length, b.rawData().length, format + " body length");
        }
    }

    @Test
    void legacyPipelineKeepsStrictExtendedScoreRule() throws IOException {
        Path singleHeader = tempDir.resolve("single");
        Files.createDirectories(singleHeader);
        // One extended header only: the legacy scorer picks it because it strictly beats both base-0 positions
        byte[] data = Files.readAllBytes(generate(Kind.EXTENDED_HIROM_48));
        Arrays.fill(data, 0xFFB0, 0xFFB0 + 0x50, (byte) 0);
        Path file = singleHeader.resolve("single-header.sfc");
        Files.write(file, data);

        SnesRom rom = reader.load(file);

        assertEquals(RomType.HiROM, rom.type());
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase());
        assertFalse(rom.interleavedSource());
    }

    @Test
    void realSf48TalIsDetectedAsInterleavedExtendedHiRomWithGd3Header() throws IOException {
        Path file = realOrSkip(E2ETestRomRegistry.GD3_SF48TAL);

        SnesRom rom = reader.load(file, CopierFormat.GD3);

        assertEquals(RomType.HiROM, rom.type());
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase());
        assertTrue(rom.interleavedSource());
        assertTrue(rom.gd3Headered());
        assertEquals("ToP English (C) DeJap", rom.title());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {E2ETestRomRegistry.GD3_SF96SOE, E2ETestRomRegistry.GD3_SF96SFA2})
    void real96MbitGd3FilesAreLinearLoRom(String resource) throws IOException {
        Path file = realOrSkip(resource);

        SnesRom rom = reader.load(file, CopierFormat.GD3);

        assertEquals(RomType.LoROM, rom.type());
        assertEquals(0, rom.headerBase());
        assertFalse(rom.interleavedSource());
        assertTrue(rom.gd3Headered());
        assertEquals(96 * SnesConstants.MBIT, rom.rawData().length);
    }

    @Test
    void realThracia776IsExtendedLoRom() throws IOException {
        Path file = realOrSkip(E2ETestRomRegistry.THRACIA_776_LIL_MANSTER);

        SnesRom rom = reader.load(file, CopierFormat.GD3);

        assertEquals(RomType.LoROM, rom.type());
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase());
    }

    @Test
    void realTalesOfPhantasiaLCaseIsExtendedHiRom() throws IOException {
        Path file = realOrSkip(E2ETestRomRegistry.TALES_OF_PHANTASIA_LCASE);

        SnesRom rom = reader.load(file, CopierFormat.GD3);

        assertEquals(RomType.HiROM, rom.type());
        assertEquals(SnesRom.EXTENDED_BASE, rom.headerBase());
    }

    private Path generate(Kind kind) throws IOException {
        return generate(kind, Files.createTempDirectory(tempDir, "gen-"));
    }

    private static Path generate(Kind kind, Path dir) throws IOException {
        return switch (kind) {
            case LOROM_8 -> SyntheticRomFactory.generateLoRom(8, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, dir);
            case HIROM_16 -> SyntheticRomFactory.generateHiRom(16, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, dir);
            case HIROM_24 -> SyntheticRomFactory.generateHiRom(24, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, dir);
            case EXTENDED_HIROM_48 -> SyntheticRomFactory.generateExHiRom(48, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, "SYNEX48T0", dir);
            case EXTENDED_LOROM_48 -> SyntheticRomFactory.generateExLoRom(48, SyntheticRomFactory.SRAM_0KB, DspChipset.ABSENT, "SYNEXLO48", dir);
        };
    }

    private static byte[] withHeader(byte[] body, CopierHeader header) {
        if (header == CopierHeader.NONE) {
            return body;
        }
        byte[] out = new byte[512 + body.length];
        if (header == CopierHeader.GD3) {
            byte[] magic = "GAME DOCTOR SF 3".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(magic, 0, out, 0, magic.length);
        }
        System.arraycopy(body, 0, out, 512, body.length);
        return out;
    }

    private Path realOrSkip(String resource) throws IOException {
        try (InputStream in = SnesRomReaderTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "real fixture absent: " + resource);
            Path target = tempDir.resolve(resource.substring(resource.lastIndexOf('/') + 1));
            Files.copy(in, target);
            return target;
        }
    }

    @Property
    void loRomDataIsDetectedAsLoRom(@ForAll("loRomData") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertEquals(RomType.LoROM, rom.type(), "LoROM data should be detected as LoROM");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void hiRomDataIsDetectedAsHiRom(@ForAll("hiRomData") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertEquals(RomType.HiROM, rom.type(), "HiROM data should be detected as HiROM");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void copierHeaderIsStrippedCorrectly(@ForAll("loRomDataWithCopierHeader") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertEquals(data.length - 512, rom.rawData().length,
                    "Copier header should be stripped, leaving original ROM size");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void sramSizeIsCalculatedCorrectly(@ForAll("sramSizeByte") int sramSizeByte) throws Exception {
        byte[] data = createRomWithSramSize((byte) sramSizeByte);
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            int expectedSram = (sramSizeByte == 0) ? 0 : (1024 << sramSizeByte);
            assertEquals(expectedSram, rom.sramSize(),
                    "SRAM size should be calculated as: sramSizeByte == 0 ? 0 : 1KB << sramSizeByte");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void dspDetectionWorksForKnownTypes(@ForAll("dspRomType") int romTypeByte) throws Exception {
        byte[] data = createRomWithRomType((byte) romTypeByte);
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertTrue(rom.hasDsp(), "ROM type " + String.format("0x%02X", romTypeByte) + " should be detected as DSP");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void checksumValidationWorks(@ForAll("loRomData") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            int sum = rom.checksum() + rom.complement();
            assertEquals(0xFFFF, sum, "Checksum + Complement should equal 0xFFFF");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void hiRomWith32MbitAndSramIsDetectedAsHiRom() throws IOException {
        Path romPath = SyntheticRomFactory.generateHiRom(32, 8, DspChipset.ABSENT, "SYNHI32T8", tempDir);

        SnesRom rom = reader.load(romPath);
        assertEquals(RomType.HiROM, rom.type(), "32Mbit HiROM should be detected as HiROM");
        assertTrue(rom.sramSize() > 0, "32Mbit HiROM with SRAM should have SRAM");
    }

    @Test
    void loRomWith4MbitIsDetectedAsLoRom() throws IOException {
        Path romPath = SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, "SYNLO4T0", tempDir);

        SnesRom rom = reader.load(romPath);
        assertEquals(RomType.LoROM, rom.type(), "4Mbit LoROM should be detected as LoROM");
    }

    @Test
    void tooSmallRomThrowsIOException() {
        byte[] tooSmall = new byte[100];
        Path tempFile = null;
        try {
            tempFile = writeToTempFile(tooSmall);
            Path finalTempFile = tempFile;
            assertThrows(IOException.class, () -> reader.load(finalTempFile),
                    "ROM too small to contain valid header should throw IOException");
        } catch (IOException e) {
            fail("Failed to create temp file for test");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Provide
    Arbitrary<byte[]> loRomData() {
        return RomDataGenerator.loRomData();
    }

    @Provide
    Arbitrary<byte[]> hiRomData() {
        return RomDataGenerator.hiRomData();
    }

    @Provide
    Arbitrary<byte[]> loRomDataWithCopierHeader() {
        return RomDataGenerator.withCopierHeader(RomDataGenerator.loRomData());
    }

    @Provide
    Arbitrary<Integer> sramSizeByte() {
        return Arbitraries.integers().between(0, 7);
    }

    @Provide
    Arbitrary<Integer> dspRomType() {
        return Arbitraries.of(0x03, 0x05, 0x13, 0x14, 0x15, 0x1A);
    }

    private Path writeToTempFile(byte[] data) throws IOException {
        Path tempFile = Files.createTempFile("snes-rom-test-", ".sfc");
        Files.write(tempFile, data);
        return tempFile;
    }

    private byte[] createRomWithSramSize(byte sramSize) {
        int headerBase = 0x7FB0;
        int size = headerBase + 0x50;
        byte[] data = new byte[size];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }

        writeString(data, headerBase + 0x10, "TEST ROM            ", 21);
        data[headerBase + 0x25] = 0x20;
        data[headerBase + 0x26] = 0x00;
        data[headerBase + 0x27] = 0x09;
        data[headerBase + 0x28] = sramSize;
        data[headerBase + 0x29] = 0x01;
        data[headerBase + 0x2A] = 0x00;
        data[headerBase + 0x2B] = 0x00;

        int checksum = calculateChecksum(data);
        int complement = checksum ^ 0xFFFF;
        writeWord(data, headerBase + 0x2C, complement);
        writeWord(data, headerBase + 0x2E, checksum);

        data[headerBase + 0x3C] = (byte) 0x00;
        data[headerBase + 0x3D] = (byte) 0x80;

        return data;
    }

    private byte[] createRomWithRomType(byte romType) {
        int headerBase = 0x7FB0;
        int size = headerBase + 0x50;
        byte[] data = new byte[size];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }

        writeString(data, headerBase + 0x10, "TEST ROM            ", 21);
        data[headerBase + 0x25] = 0x20;
        data[headerBase + 0x26] = romType;
        data[headerBase + 0x27] = 0x09;
        data[headerBase + 0x28] = 0x00;
        data[headerBase + 0x29] = 0x01;
        data[headerBase + 0x2A] = 0x00;
        data[headerBase + 0x2B] = 0x00;

        int checksum = calculateChecksum(data);
        int complement = checksum ^ 0xFFFF;
        writeWord(data, headerBase + 0x2C, complement);
        writeWord(data, headerBase + 0x2E, checksum);

        data[headerBase + 0x3C] = (byte) 0x00;
        data[headerBase + 0x3D] = (byte) 0x80;

        return data;
    }

    private void writeString(byte[] data, int offset, String s, int maxLen) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int len = Math.min(bytes.length, maxLen);
        System.arraycopy(bytes, 0, data, offset, len);
    }

    private void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum & 0xFFFF;
    }
}
