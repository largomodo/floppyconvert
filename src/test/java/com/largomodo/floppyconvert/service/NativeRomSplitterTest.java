package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.format.CopierFormat;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for NativeRomSplitter with mocked dependencies.
 * <p>
 * Tests verify:
 * - Orchestration logic without real file I/O
 * - Split boundary calculations
 * - File naming for all formats
 * - Conditional interleaving (GD3 + HiROM only)
 * - Header generation delegation
 */
class NativeRomSplitterTest {

    @TempDir
    Path tempDir;

    private SnesRomReader mockReader;
    private SnesInterleaver mockInterleaver;
    private HeaderGeneratorFactory mockHeaderFactory;
    private HeaderGenerator mockHeaderGenerator;
    private NativeRomSplitter splitter;

    @BeforeEach
    void setUp() {
        mockReader = mock(SnesRomReader.class);
        mockInterleaver = mock(SnesInterleaver.class);
        mockHeaderFactory = mock(HeaderGeneratorFactory.class);
        mockHeaderGenerator = mock(HeaderGenerator.class);
        splitter = new NativeRomSplitter(mockReader, mockInterleaver, mockHeaderFactory);
    }

    @Test
    void testSplit4MbitLoRomSwc_SingleFile() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(512 * 1024); // 4 Mbit = 512 KB
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockHeaderFactory, times(1)).get(CopierFormat.SWC);
        verify(mockHeaderGenerator, times(1)).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(true), anyByte());
        verifyNoInteractions(mockInterleaver);

        assertEquals(1, parts.size());
        assertEquals("test.1", parts.getFirst().getName());
    }

    @Test
    void testSplit32MbitHiRomSwc_EightFiles() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(4 * 1024 * 1024); // 32 Mbit = 4 MB
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockHeaderFactory, times(1)).get(CopierFormat.SWC);
        verify(mockHeaderGenerator, times(8)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verifyNoInteractions(mockInterleaver);

        assertEquals(8, parts.size());
        for (int i = 0; i < 8; i++) {
            assertEquals("test." + (i + 1), parts.get(i).getName());
        }
    }

    @Test
    void testSplit32MbitHiRomGd3_FourFiles() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(4 * 1024 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockInterleaver, times(1)).interleave(any(byte[].class), any(RomType.class));
        verify(mockHeaderFactory, times(1)).get(CopierFormat.GD3);
        verify(mockHeaderGenerator, times(4)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());

        assertEquals(4, parts.size());
        assertEquals("SF32TESA.078", parts.getFirst().getName());
        assertEquals("SF32TESB.078", parts.get(1).getName());
        assertEquals("SF32TESC.078", parts.get(2).getName());
        assertEquals("SF32TESD.078", parts.get(3).getName());
    }

    @Test
    void testSplit12MbitRomSwc_ThreeFiles() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1536 * 1024); // 12 Mbit = 1.5 MB
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockHeaderFactory, times(1)).get(CopierFormat.SWC);
        verify(mockHeaderGenerator, times(3)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verifyNoInteractions(mockInterleaver);

        assertEquals(3, parts.size());
        assertEquals("test.1", parts.getFirst().getName());
        assertEquals("test.2", parts.get(1).getName());
        assertEquals("test.3", parts.get(2).getName());
    }

    @Test
    void testGd3HiRomCallsInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockInterleaver, times(1)).interleave(any(byte[].class), any(RomType.class));
    }

    @Test
    void testGd3LoRomDoesNotCallInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verifyNoInteractions(mockInterleaver);
    }

    @Test
    void testSwcNeverCallsInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verifyNoInteractions(mockInterleaver);
    }

    @Test
    void testFigNeverCallsInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.FIG)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.FIG);

        verifyNoInteractions(mockInterleaver);
    }

    @Test
    void testUfoNeverCallsInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(512 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.UFO);

        verifyNoInteractions(mockInterleaver);
    }

    @Test
    void testHeaderGeneratorCalledWithCorrectIndices() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1536 * 1024); // 12 Mbit = 3 parts
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(1), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(2), eq(true), anyByte());
    }

    @Test
    void testSnesRomReaderThrowsIOException() throws IOException {
        File inputRom = createRomFile("test.sfc");

        when(mockReader.load(inputRom.toPath())).thenThrow(new IOException("Failed to read ROM"));

        IOException exception = assertThrows(IOException.class, () ->
                splitter.split(inputRom, tempDir, CopierFormat.SWC)
        );

        assertEquals("Failed to read ROM", exception.getMessage());
        verify(mockReader, times(1)).load(inputRom.toPath());
        verifyNoInteractions(mockHeaderFactory);
        verifyNoInteractions(mockInterleaver);
    }

    @Test
    void testGd3SingleFileSinglePart() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(512 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(1, parts.size());
        assertEquals("SF4TES__.078", parts.getFirst().getName());
    }

    @Test
    void testFigFileNaming() throws IOException {
        File inputRom = createRomFile("game.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit = 2 parts
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
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

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
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
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(1, parts.size());
        assertTrue(parts.getFirst().getName().matches("SF4SUP__.078"),
                "Single-file GD3 should match SF-Code pattern with underscore padding");
    }

    @Test
    void testGd3MultiFileLoRomNaming() throws IOException {
        File inputRom = createRomFile("StreetFighter.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size());
        assertEquals("SF16STRA.078", parts.getFirst().getName());
        assertEquals("SF16STRB.078", parts.get(1).getName());
    }

    @Test
    void testGd3MultiFileHiRomWithXPadding() throws IOException {
        File inputRom = createRomFile("ChronoTrigger.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(romData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(4, parts.size());
        assertEquals("SF16CHRA.078", parts.getFirst().getName());
        assertEquals("SF16CHRB.078", parts.get(1).getName());
        assertEquals("SF16CHRC.078", parts.get(2).getName());
        assertEquals("SF16CHRD.078", parts.get(3).getName());
    }

    @Test
    void testGd3MultiFileHiRomWithoutXPadding() throws IOException {
        File inputRom = createRomFile("TestGame.sfc");
        byte[] romData = createRomData(3 * 1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(romData);
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
        File inputRom = createRomFile("My-Game!.sfc");
        byte[] romData = createRomData(512 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(1, parts.size());
        assertEquals("SF4MYG__.078", parts.getFirst().getName());
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
                0,
                0
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
                0,
                0
        );
    }

    private SnesRom createExHiRom(byte[] data) {
        return new SnesRom(
                data,
                RomType.ExHiROM,
                0,
                "Test ROM",
                false,
                0,
                0,
                0,
                0,
                0
        );
    }

    @Test
    void testGd3_8MbitHiRom_ForceSplitTo4Mbit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
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

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(1)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(0), eq(true), anyByte());

        assertEquals(1, parts.size());
        assertEquals("SF8TES__.078", parts.getFirst().getName());
    }

    @Test
    void testGd3_16MbitHiRom_BoundaryCase() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(2 * 1024 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(4)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(1), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(2), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(3), eq(true), anyByte());

        assertEquals(4, parts.size());
        assertEquals("SF16TESA.078", parts.getFirst().getName());
        assertEquals("SF16TESB.078", parts.get(1).getName());
        assertEquals("SF16TESC.078", parts.get(2).getName());
        assertEquals("SF16TESD.078", parts.get(3).getName());
    }

    @Test
    void testGd3_20MbitHiRom_NoForceSplit() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(2560 * 1024);
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(any(byte[].class), any(RomType.class))).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockHeaderGenerator, times(3)).generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(0), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1024 * 1024), eq(1), eq(false), anyByte());
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(512 * 1024), eq(2), eq(true), anyByte());

        assertEquals(3, parts.size());
        assertEquals("SF20TESA.078", parts.getFirst().getName());
        assertEquals("SF20TESB.078", parts.get(1).getName());
        assertEquals("SF20TESC.078", parts.get(2).getName());
    }

    @Test
    void testUfo_12MbitHiRom_IrregularChunks() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1536 * 1024);
        SnesRom rom = createHiRom(romData);

        HeaderGenerator spyHeaderGen = spy(HeaderGenerator.class);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
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
        byte[] romData = createRomData(1536 * 1024);
        SnesRom rom = createLoRom(romData);

        HeaderGenerator spyHeaderGen = spy(HeaderGenerator.class);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(spyHeaderGen);
        when(spyHeaderGen.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(3, parts.size(), "12Mbit LoROM UFO should produce 3 parts (standard 4Mbit chunks)");

        assertEquals("test.1gm", parts.getFirst().getName());
        assertEquals("test.2gm", parts.get(1).getName());
        assertEquals("test.3gm", parts.get(2).getName());

        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(0), eq(false), anyByte());
        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(1), eq(false), anyByte());
        verify(spyHeaderGen).generateHeader(eq(rom), eq(512 * 1024), eq(2), eq(true), anyByte());
    }

    @Test
    void testExHiRom48MbitGd3_Accepted() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(48 * SnesConstants.MBIT);
        SnesRom rom = createExHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyInt(), anyBoolean(), anyByte())).thenReturn(new byte[512]);
        when(mockInterleaver.interleave(any(byte[].class), eq(RomType.ExHiROM))).thenReturn(romData);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockInterleaver, times(1)).interleave(romData, RomType.ExHiROM);
        assertEquals(6, parts.size());
    }

    @Test
    void testExHiRom48MbitUfo_Rejected() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(48 * SnesConstants.MBIT);
        SnesRom rom = createExHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);

        assertThrows(UnsupportedHardwareException.class, () -> splitter.split(inputRom, tempDir, CopierFormat.UFO));
    }
}
