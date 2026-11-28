// createRom helpers use checksum=0x0001, complement=0xFFFE to satisfy the SnesRom checksum invariant.
package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.split.SplitStrategyFactory;
import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for NativeRomSplitter with mocked dependencies.
 * <p>
 * Tests verify NativeRomSplitter orchestration: filename construction (FIG/UFO/GD3
 * SF-Code, X-padding, sanitization), padding boundaries, validator wiring, IOException
 * propagation, null-input and non-directory work-dir guards, and extended HiROM
 * acceptance/rejection. (ref: DL-005)
 * <p>
 * Split-mechanics (chunk count, flag sequencing, HiROM threshold dispatch)
 * are covered by StandardSplitStrategyTest, Gd3SplitStrategyTest,
 * UfoHiRomSplitStrategyTest, and SplitStrategyFactoryTest.strategyMatrix. (ref: DL-004)
 */
class NativeRomSplitterTest {

    @TempDir
    Path tempDir;

    private SnesRomReader mockReader;
    private HeaderGeneratorFactory mockHeaderFactory;
    private HeaderGenerator mockHeaderGenerator;
    private NativeRomSplitter splitter;

    @BeforeEach
    void setUp() {
        mockReader = mock(SnesRomReader.class);
        mockHeaderFactory = mock(HeaderGeneratorFactory.class);
        mockHeaderGenerator = mock(HeaderGenerator.class);
        // Real strategies and a real interleaver: GD3 padding, interleave and naming are the behaviour under
        // test; only the reader and the header generators are mocked. (ref: DL-004, DL-007)
        splitter = new NativeRomSplitter(mockReader, mockHeaderFactory, new SplitStrategyFactory(), new SnesInterleaver());
    }

    @Test
    void testSnesRomReaderThrowsIOException() throws IOException {
        File inputRom = createRomFile("test.sfc");

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenThrow(new IOException("Failed to read ROM"));

        IOException exception = assertThrows(IOException.class, () ->
                splitter.split(inputRom, tempDir, CopierFormat.SWC)
        );

        assertEquals("Failed to read ROM", exception.getMessage());
        verify(mockReader, times(1)).load(eq(inputRom.toPath()), any(CopierFormat.class));
        verifyNoInteractions(mockHeaderFactory);
    }

    @Test
    void testFigFileNaming() throws IOException {
        File inputRom = createRomFile("game.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit = 2 parts
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.FIG)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.FIG);

        assertEquals(2, parts.size());
        assertEquals("game.1", parts.getFirst().getName());
        assertEquals("game.2", parts.get(1).getName());
    }

    @Test
    void testUfoFileNaming() throws IOException {
        File inputRom = createRomFile("game.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit = 2 parts
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(2, parts.size());
        assertEquals("game.1gm", parts.getFirst().getName());
        assertEquals("game.2gm", parts.get(1).getName());
    }

    @Test
    void testGd3SingleFileNaming() throws IOException {
        File inputRom = createRomFile("SuperMario.sfc");
        byte[] romData = createRomData(512 * 1024);
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(1, parts.size());
        assertEquals("SF4SUP.078", parts.getFirst().getName(),
                "A single GD3 part carries neither a part letter nor underscore padding");
    }

    @Test
    void testGd3MultiFileLoRomNaming() throws IOException {
        File inputRom = createRomFile("StreetFighter.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size());
        assertEquals("SF16STRA.078", parts.getFirst().getName());
        assertEquals("SF16STRB.078", parts.get(1).getName());
    }

    @Test
    // X-padding triggers when the size digits are below 10 — see testGd3_8MbitHiRom_ForceSplitTo4Mbit
    void testGd3MultiFileHiRomNoXPadding16Mbit() throws IOException {
        File inputRom = createRomFile("ChronoTrigger.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size(), "A 16 Mbit HiROM is split in two halves");
        assertEquals("SF16CHRA.078", parts.getFirst().getName());
        assertEquals("SF16CHRB.078", parts.get(1).getName());
    }

    @Test
    void testGd3MultiFileHiRomWithoutXPadding() throws IOException {
        File inputRom = createRomFile("TestGame.sfc");
        byte[] romData = createRomData(3 * 1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(3, parts.size());
        assertEquals("SF24TESA.078", parts.getFirst().getName());
        assertEquals("SF24TESB.078", parts.get(1).getName());
        assertEquals("SF24TESC.078", parts.get(2).getName());
    }

    @Test
    void testGd3NameSanitization() throws IOException {
        // ucon64 takes the first 3 characters of the file name as they are; '-' survives, only ' ' and '.' become '_'
        File inputRom = createRomFile("My-Game!.sfc");
        byte[] romData = createRomData(512 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size());
        assertEquals("SF4MY-XA.078", parts.getFirst().getName());
        assertEquals("SF4MY-XB.078", parts.get(1).getName());
    }

    @Test
    // Spaces and dots at index 3 or later would break the part file name, so they become underscores.
    void testGd3SpacesAndDotsBecomeUnderscoresAfterTheSizeDigit() throws IOException {
        File inputRom = createRomFile("a b.sfc");
        byte[] romData = createRomData(512 * 1024);
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(1, parts.size());
        assertEquals("SF4A_B.078", parts.getFirst().getName());
    }

    @Test
    void testGd3SourceAlreadyNamedWithSizePrefixKeepsItsName() throws IOException {
        File inputRom = createRomFile("sf16Chr.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals("SF16CHRA.078", parts.getFirst().getName());
    }

    @Test
    void testGd3HiRom10MbitNameUsesPaddedSize() throws IOException {
        File inputRom = createRomFile("ActRaiser.sfc");
        byte[] romData = createRomData(10 * SnesConstants.MBIT);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size(), "10 Mbit pads to 12 Mbit and splits in two 6 Mbit halves");
        assertEquals("SF12ACTA.078", parts.getFirst().getName());
        assertEquals("SF12ACTB.078", parts.get(1).getName());
        // The mocked header generator returns 512 bytes for every part
        assertEquals(6 * SnesConstants.MBIT + 512, parts.getFirst().length());
        assertEquals(6 * SnesConstants.MBIT + 512, parts.get(1).length());
    }

    @Test
    void testGd3NineMbitLoRomKeepsDistinctPartNames() throws IOException {
        // ucon64 writes the part letter past the end of the 6-character name, so both parts get one name and the
        // second overwrites the first. A '_' at index 6 keeps the set usable. (ref: DL-021)
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(9 * SnesConstants.MBIT);
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size());
        assertEquals("SF9TES_A.078", parts.getFirst().getName());
        assertEquals("SF9TES_B.078", parts.get(1).getName());
    }

    @Test
    void testNonExistentInputRom() {
        File nonExistentRom = new File("/nonexistent/path/test.sfc");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                splitter.split(nonExistentRom, tempDir, CopierFormat.SWC)
        );

        assertTrue(exception.getMessage().contains("Input ROM does not exist"));
    }

    @Test
    void testNullInputRom() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                splitter.split(null, tempDir, CopierFormat.SWC)
        );

        assertTrue(exception.getMessage().contains("Input ROM does not exist"));
    }

    @Test
    void testNullWorkDir() throws IOException {
        File inputRom = createRomFile("test.sfc");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                splitter.split(inputRom, null, CopierFormat.SWC)
        );

        assertTrue(exception.getMessage().contains("Work directory is not a directory"));
    }

    @Test
    void testNonDirectoryWorkDir() throws IOException {
        File inputRom = createRomFile("test.sfc");
        Path notADirectory = tempDir.resolve("notADirectory.txt");
        Files.write(notADirectory, new byte[]{0x01});

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                splitter.split(inputRom, notADirectory, CopierFormat.SWC)
        );

        assertTrue(exception.getMessage().contains("Work directory is not a directory"));
    }

    // Padding tests verify sub-chunk-size ROMs are mirrored to the nearest format-appropriate
    // Mbit boundary before splitting. UFO HiROM uses chunker-supported sizes {2,4,12,20,32}.
    // All others use power-of-2 boundaries {2,4,8,16,32}.

    @Test
    void testSmallRomPaddedToMbitBoundary_2Mbit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(128 * 1024); // 1 Mbit = 128 KB
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        assertEquals(1, parts.size(), "1Mbit ROM should produce single part after padding to 2Mbit");
        // 2Mbit = 256KB + 512-byte header
        assertEquals(256 * 1024 + 512, parts.getFirst().length(),
                "Padded 1Mbit ROM should produce 2Mbit data (256KB) + 512-byte header");
    }

    @Test
    void testSmallRomPaddedToMbitBoundary_4Mbit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(384 * 1024); // 3 Mbit = 384 KB
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.FIG)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.FIG);

        assertEquals(1, parts.size(), "3Mbit ROM should produce single part after padding to 4Mbit");
        // 4Mbit = 512KB + 512-byte header
        assertEquals(512 * 1024 + 512, parts.getFirst().length(),
                "Padded 3Mbit ROM should produce 4Mbit data (512KB) + 512-byte header");
    }

    @Test
    void testExactMbitBoundaryNotPadded() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(512 * 1024); // Exactly 4 Mbit
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        assertEquals(1, parts.size(), "Exact 4Mbit ROM should produce single part without padding");
        assertEquals(512 * 1024 + 512, parts.getFirst().length(),
                "Exact 4Mbit ROM should produce exactly 512KB + 512-byte header");
    }

    @Test
    void testGd3HiRomPaddingHandledByInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(384 * 1024); // 3 Mbit - small HiROM
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        // 3 Mbit stays 3 Mbit (rounded up to 1 Mbit only) and splits in two halves
        assertEquals(2, parts.size());
        assertEquals(192 * 1024 + 512, parts.getFirst().length());
        assertEquals(192 * 1024 + 512, parts.get(1).length());
    }

    @Test
    void testGd3LoRomSmallRomNotPadded() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(128 * 1024); // 1 Mbit LoROM
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        // GD3 LoROM is never padded, interleaved or truncated
        assertEquals(1, parts.size(), "GD3 LoROM 1Mbit should produce single part");
        assertEquals(128 * 1024 + 512, parts.getFirst().length(),
                "GD3 LoROM keeps its 128KB size: data + 512-byte header (only first part gets header)");
    }

    @Test
    // The input header is on part A unchanged and the body is not re-interleaved.
    void testGd3PreHeaderedInputIsSplitAsIs() throws IOException {
        int bodySize = 16 * SnesConstants.MBIT;
        byte[] body = createRomData(bodySize);
        byte[] sourceHeader = new byte[512];
        for (int i = 0; i < sourceHeader.length; i++) {
            sourceHeader[i] = (byte) (0x5A + i);
        }
        Path input = tempDir.resolve("game.sfc");
        byte[] file = new byte[512 + bodySize];
        System.arraycopy(sourceHeader, 0, file, 0, 512);
        System.arraycopy(body, 0, file, 512, bodySize);
        Files.write(input, file);

        // The reader reports the linear body; the splitter must still cut the file's own bytes
        SnesRom rom = new SnesRom(new byte[bodySize], RomType.LoROM, 0, "Test ROM", false, 0, 0, 0, 0x0001, 0xFFFE,
                0, false, true);
        when(mockReader.load(eq(input), any(CopierFormat.class))).thenReturn(rom);

        Path out = Files.createDirectory(tempDir.resolve("out"));

        List<File> parts = splitter.split(input.toFile(), out, CopierFormat.GD3);

        assertEquals(2, parts.size());
        assertEquals("SF16GAMA.078", parts.getFirst().getName());
        byte[] partA = Files.readAllBytes(parts.getFirst().toPath());
        assertArrayEquals(sourceHeader, Arrays.copyOfRange(partA, 0, 512), "Part A starts with the source header");
        assertArrayEquals(Arrays.copyOfRange(body, 0, 8 * SnesConstants.MBIT), Arrays.copyOfRange(partA, 512, partA.length));
        byte[] partB = Files.readAllBytes(parts.get(1).toPath());
        assertArrayEquals(Arrays.copyOfRange(body, 8 * SnesConstants.MBIT, bodySize), partB, "Part B has no header");
        verifyNoInteractions(mockHeaderFactory);
    }

    @Test
    void testUfoHiRomPadsToChunkerSupportedSize_6Mbit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(6 * SnesConstants.MBIT); // 6 Mbit HiROM
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        // 6Mbit pads to 12Mbit (next UFO-supported size), which produces 4 parts via UfoHiRomChunker
        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(4, parts.size(),
                "6Mbit UFO HiROM should pad to 12Mbit (next chunker-supported size), producing 4 parts");
    }

    @Test
    void testUfoHiRomPadsToChunkerSupportedSize_3Mbit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(3 * SnesConstants.MBIT); // 3 Mbit HiROM
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        // 3Mbit pads to 4Mbit (next UFO-supported size after 2Mbit), which is in chunker table
        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(2, parts.size(),
                "3Mbit UFO HiROM should pad to 4Mbit (chunker-supported), producing 2 parts per chunker table");
    }

    private File createRomFile(String name) throws IOException {
        Path romPath = tempDir.resolve(name);
        Files.write(romPath, new byte[]{0x01, 0x02, 0x03});
        return romPath.toFile();
    }

    private byte[] createRomData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }
        return data;
    }

    private SnesRom createLoRom(byte[] data) {
        return new SnesRom(
                data,
                RomType.LoROM,
                0,
                "Test ROM",
                false,
                0,
                0,
                0,
                0x0001,
                0xFFFE
        );
    }

    private SnesRom createHiRom(byte[] data) {
        return new SnesRom(
                data,
                RomType.HiROM,
                0,
                "Test ROM",
                false,
                0,
                0,
                0,
                0x0001,
                0xFFFE
        );
    }

    private SnesRom createExHiRom(byte[] data) {
        return new SnesRom(
                data,
                RomType.HiROM,
                0,
                "Test ROM",
                false,
                0,
                0,
                0,
                0x0001,
                0xFFFE,
                SnesRom.EXTENDED_BASE,
                false,
                false
        );
    }

    @Test
    void testGd3_8MbitHiRom_ForceSplitTo4Mbit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(2)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(1), eq(true), anyByte());

        assertEquals(2, parts.size());
        assertEquals("SF8TESXA.078", parts.getFirst().getName());
        assertEquals("SF8TESXB.078", parts.get(1).getName());
    }

    @Test
    void testGd3_8MbitLoRom_NoForceSplit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024);
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(1)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(0), eq(true), anyByte());

        assertEquals(1, parts.size());
        assertEquals("SF8TES.078", parts.getFirst().getName());
    }

    @Test
    void testGd3_16MbitHiRom_BoundaryCase() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(2)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(0), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(1), eq(true), anyByte());

        assertEquals(2, parts.size());
        assertEquals("SF16TESA.078", parts.getFirst().getName());
        assertEquals("SF16TESB.078", parts.get(1).getName());
    }

    @Test
    void testGd3_20MbitHiRom_PadsTo24MbitAndNamesBySizeOfThePaddedImage() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(2560 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(3)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(0), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(1), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(2), eq(true), anyByte());

        assertEquals(3, parts.size());
        assertEquals("SF24TESA.078", parts.getFirst().getName());
        assertEquals("SF24TESB.078", parts.get(1).getName());
        assertEquals("SF24TESC.078", parts.get(2).getName());
    }

    @Test
    void testUfo_12MbitHiRom_IrregularChunks() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1536 * 1024);
        SnesRom rom = createHiRom(romData);

        HeaderGenerator spyHeaderGen = spy(HeaderGenerator.class);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(spyHeaderGen);
        when(spyHeaderGen.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(4, parts.size(), "12Mbit HiROM UFO should produce 4 parts");

        assertEquals("test.1gm", parts.getFirst().getName());
        assertEquals("test.2gm", parts.get(1).getName());
        assertEquals("test.3gm", parts.get(2).getName());
        assertEquals("test.4gm", parts.get(3).getName());

        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(false), eq((byte) 0x40));
        verify(spyHeaderGen).generateHeader(eq(rom), eq(256 * 1024), eq(1), eq(false), eq((byte) 0x10));
        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(2), eq(false), eq((byte) 0x10));
        verify(spyHeaderGen).generateHeader(eq(rom), eq(256 * 1024), eq(3), eq(true), eq((byte) 0x00));

        assertEquals(512 * 1024 + 512, parts.getFirst().length(), "Part 1: 4Mbit + 512-byte header");
        assertEquals(256 * 1024 + 512, parts.get(1).length(), "Part 2: 2Mbit + 512-byte header");
        assertEquals(512 * 1024 + 512, parts.get(2).length(), "Part 3: 4Mbit + 512-byte header");
        assertEquals(256 * 1024 + 512, parts.get(3).length(), "Part 4: 2Mbit + 512-byte header");
    }

    @Test
    void testUfo_12MbitLoRom_StandardChunks() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1536 * 1024); // 12 Mbit LoROM, padded to 16 Mbit
        SnesRom rom = createLoRom(romData);

        HeaderGenerator spyHeaderGen = spy(HeaderGenerator.class);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(spyHeaderGen);
        when(spyHeaderGen.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(4, parts.size(), "12Mbit LoROM UFO pads to 16Mbit, producing 4 standard 4Mbit chunks");

        assertEquals("test.1gm", parts.getFirst().getName());
        assertEquals("test.2gm", parts.get(1).getName());
        assertEquals("test.3gm", parts.get(2).getName());
        assertEquals("test.4gm", parts.get(3).getName());

        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(false), anyByte());
        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(1), eq(false), anyByte());
        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(2), eq(false), anyByte());
        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(3), eq(true), anyByte());
    }

    @Test
    void testExtendedHiRom48MbitGd3_Accepted() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(48 * SnesConstants.MBIT);
        SnesRom rom = createExHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(6, parts.size());
    }

    @Test
    void testExtendedHiRom48MbitUfo_Rejected() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(48 * SnesConstants.MBIT);
        SnesRom rom = createExHiRom(romData);

        when(mockReader.load(eq(inputRom.toPath()), any(CopierFormat.class))).thenReturn(rom);

        assertThrows(UnsupportedHardwareException.class, () -> splitter.split(inputRom, tempDir, CopierFormat.UFO));
    }
}
