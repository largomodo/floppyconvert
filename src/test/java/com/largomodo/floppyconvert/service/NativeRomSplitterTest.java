package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
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
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockHeaderFactory, times(1)).get(CopierFormat.SWC);
        verify(mockHeaderGenerator, times(1)).generateHeader(eq(rom), eq(0), eq(true));
        verifyNoInteractions(mockInterleaver);

        assertEquals(1, parts.size());
        assertEquals("test.1", parts.get(0).getName());
    }

    @Test
    void testSplit32MbitHiRomSwc_EightFiles() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(4 * 1024 * 1024); // 32 Mbit = 4 MB
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockHeaderFactory, times(1)).get(CopierFormat.SWC);
        verify(mockHeaderGenerator, times(8)).generateHeader(eq(rom), anyInt(), anyBoolean());
        verifyNoInteractions(mockInterleaver);

        assertEquals(8, parts.size());
        for (int i = 0; i < 8; i++) {
            assertEquals("test." + (i + 1), parts.get(i).getName());
        }
    }

    @Test
    void testSplit32MbitHiRomGd3_FourFiles() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(4 * 1024 * 1024); // 32 Mbit = 4 MB
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(romData)).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockInterleaver, times(1)).interleave(romData);
        verify(mockHeaderFactory, times(1)).get(CopierFormat.GD3);
        verify(mockHeaderGenerator, times(4)).generateHeader(eq(rom), anyInt(), anyBoolean());

        assertEquals(4, parts.size());
        assertEquals("TESTA.078", parts.get(0).getName());
        assertEquals("TESTB.078", parts.get(1).getName());
        assertEquals("TESTC.078", parts.get(2).getName());
        assertEquals("TESTD.078", parts.get(3).getName());
    }

    @Test
    void testSplit12MbitRomSwc_ThreeFiles() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1536 * 1024); // 12 Mbit = 1.5 MB
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.SWC)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockReader, times(1)).load(inputRom.toPath());
        verify(mockHeaderFactory, times(1)).get(CopierFormat.SWC);
        verify(mockHeaderGenerator, times(3)).generateHeader(eq(rom), anyInt(), anyBoolean());
        verifyNoInteractions(mockInterleaver);

        assertEquals(3, parts.size());
        assertEquals("test.1", parts.get(0).getName());
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
        when(mockInterleaver.interleave(romData)).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.GD3);

        verify(mockInterleaver, times(1)).interleave(romData);
    }

    @Test
    void testGd3LoRomDoesNotCallInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

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
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

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
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.FIG);

        verifyNoInteractions(mockInterleaver);
    }

    @Test
    void testUfoNeverCallsInterleaver() throws IOException {
        File inputRom = createRomFile("test.sfc");
        byte[] romData = createRomData(1024 * 1024);
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

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
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        splitter.split(inputRom, tempDir, CopierFormat.SWC);

        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(0), eq(false));
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(1), eq(false));
        verify(mockHeaderGenerator).generateHeader(eq(rom), eq(2), eq(true));
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
        byte[] romData = createRomData(512 * 1024); // 4 Mbit
        byte[] interleavedData = new byte[romData.length];
        SnesRom rom = createHiRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockInterleaver.interleave(romData)).thenReturn(interleavedData);
        when(mockHeaderFactory.get(CopierFormat.GD3)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.GD3);

        assertEquals(1, parts.size());
        assertEquals("TEST.078", parts.get(0).getName());
    }

    @Test
    void testFigFileNaming() throws IOException {
        File inputRom = createRomFile("game.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit = 2 parts
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.FIG)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.FIG);

        assertEquals(2, parts.size());
        assertEquals("game.1", parts.get(0).getName());
        assertEquals("game.2", parts.get(1).getName());
    }

    @Test
    void testUfoFileNaming() throws IOException {
        File inputRom = createRomFile("game.sfc");
        byte[] romData = createRomData(1024 * 1024); // 8 Mbit = 2 parts
        SnesRom rom = createLoRom(romData);

        when(mockReader.load(inputRom.toPath())).thenReturn(rom);
        when(mockHeaderFactory.get(CopierFormat.UFO)).thenReturn(mockHeaderGenerator);
        when(mockHeaderGenerator.generateHeader(eq(rom), anyInt(), anyBoolean())).thenReturn(new byte[512]);

        List<File> parts = splitter.split(inputRom, tempDir, CopierFormat.UFO);

        assertEquals(2, parts.size());
        assertEquals("game.1", parts.get(0).getName());
        assertEquals("game.2", parts.get(1).getName());
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
}
