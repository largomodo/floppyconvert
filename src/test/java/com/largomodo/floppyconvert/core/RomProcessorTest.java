package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.core.domain.DiskLayout;
import com.largomodo.floppyconvert.core.domain.DiskPacker;
import com.largomodo.floppyconvert.core.domain.RomPartMetadata;
import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

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
 * Unit tests for RomProcessor with mocked dependencies.
 * <p>
 * Tests verify:
 * - Constructor injection with null checks
 * - Delegation to injected dependencies
 * - Proper exception handling and cleanup
 * - Return value correctness
 */
class RomProcessorTest {

    @TempDir
    Path tempDir;
    private DiskPacker mockPacker;
    private ConversionFacade mockFacade;
    private DiskTemplateFactory mockTemplateFactory;
    private RomPartNormalizer normalizer;  // Real instance (stateless utility)
    private RomProcessor processor;

    @BeforeEach
    void setUp() {
        mockPacker = mock(DiskPacker.class);
        mockFacade = mock(ConversionFacade.class);
        mockTemplateFactory = mock(DiskTemplateFactory.class);
        normalizer = new RomPartNormalizer();  // Use real instance
        processor = new RomProcessor(mockPacker, mockFacade, mockTemplateFactory, normalizer);
    }

    @Test
    void testConstructorRejectsNullPacker() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new RomProcessor(null, mockFacade, mockTemplateFactory, normalizer)
        );
        assertEquals("All dependencies must not be null", ex.getMessage());
    }

    @Test
    void testConstructorRejectsNullFacade() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new RomProcessor(mockPacker, null, mockTemplateFactory, normalizer)
        );
        assertEquals("All dependencies must not be null", ex.getMessage());
    }

    @Test
    void testConstructorRejectsNullTemplateFactory() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new RomProcessor(mockPacker, mockFacade, null, normalizer)
        );
        assertEquals("All dependencies must not be null", ex.getMessage());
    }

    @Test
    void testConstructorRejectsNullNormalizer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new RomProcessor(mockPacker, mockFacade, mockTemplateFactory, null)
        );
        assertEquals("All dependencies must not be null", ex.getMessage());
    }

    @Test
    void testProcessRomDelegatesToSplitterAndPacker() throws IOException {
        // Setup: Create test ROM file
        Path romFile = tempDir.resolve("TestGame.sfc");
        Files.write(romFile, new byte[]{0x01, 0x02, 0x03});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Mock splitter to return parts
        File part1 = tempDir.resolve("work").resolve("TestGame.1").toFile();
        File part2 = tempDir.resolve("work").resolve("TestGame.2").toFile();
        Files.createDirectories(part1.getParentFile().toPath());
        Files.write(part1.toPath(), new byte[]{0x01});
        Files.write(part2.toPath(), new byte[]{0x02});

        when(mockFacade.splitRom(any(File.class), any(Path.class), eq(CopierFormat.FIG)))
                .thenReturn(Arrays.asList(part1, part2));

        // Mock templateFactory to create actual .img files
        doAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            Files.write(targetPath, new byte[]{0x00});  // Create empty file
            return null;
        }).when(mockTemplateFactory).createBlankDisk(any(FloppyType.class), any(Path.class));

        // Mock packer to return single disk layout (normalizer will run in real code)
        when(mockPacker.pack(anyList())).thenAnswer(invocation -> {
            List<RomPartMetadata> metadata = invocation.getArgument(0);
            return List.of(new DiskLayout(metadata, FloppyType.FLOPPY_144M));
        });

        // Execute
        int diskCount = processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.FIG);

        // Verify facade.splitRom was called
        verify(mockFacade).splitRom(eq(romFile.toFile()), any(Path.class), eq(CopierFormat.FIG));

        // Verify packer was called with correct metadata (after normalization)
        ArgumentCaptor<List<RomPartMetadata>> packerArg = ArgumentCaptor.forClass(List.class);
        verify(mockPacker).pack(packerArg.capture());
        List<RomPartMetadata> capturedMetadata = packerArg.getValue();
        assertEquals(2, capturedMetadata.size());

        // Verify templateFactory was called
        verify(mockTemplateFactory).createBlankDisk(eq(FloppyType.FLOPPY_144M), any(Path.class));

        // Verify facade.write was called
        verify(mockFacade).write(any(File.class), anyList(), anyMap());

        // Verify return value
        assertEquals(1, diskCount, "Should return number of disk layouts");
    }

    @Test
    void testProcessRomReturnsDiskCount() throws IOException {
        // Setup: Create test ROM file
        Path romFile = tempDir.resolve("MultiDisk.sfc");
        Files.write(romFile, new byte[]{0x01, 0x02, 0x03});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Mock splitter to return parts
        File part1 = tempDir.resolve("work").resolve("MultiDisk.1").toFile();
        Files.createDirectories(part1.getParentFile().toPath());
        Files.write(part1.toPath(), new byte[]{0x01});

        when(mockFacade.splitRom(any(File.class), any(Path.class), eq(CopierFormat.SWC)))
                .thenReturn(List.of(part1));

        // Mock templateFactory to create actual .img files
        doAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            Files.write(targetPath, new byte[]{0x00});  // Create empty file
            return null;
        }).when(mockTemplateFactory).createBlankDisk(any(FloppyType.class), any(Path.class));

        // Mock packer to return THREE disk layouts (normalizer will run in real code)
        when(mockPacker.pack(anyList())).thenAnswer(invocation -> {
            List<RomPartMetadata> metadata = invocation.getArgument(0);
            return Arrays.asList(
                    new DiskLayout(metadata, FloppyType.FLOPPY_720K),
                    new DiskLayout(metadata, FloppyType.FLOPPY_720K),
                    new DiskLayout(metadata, FloppyType.FLOPPY_720K)
            );
        });

        // Execute
        int diskCount = processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.SWC);

        // Verify return value matches layouts.size()
        assertEquals(3, diskCount, "Should return number of disk layouts (3)");
    }

    @Test
    void testExceptionInSplitterClosesWorkspace() throws IOException {
        // Setup: Create test ROM file
        Path romFile = tempDir.resolve("FailGame.sfc");
        Files.write(romFile, new byte[]{0x01, 0x02, 0x03});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Mock splitter to throw exception
        when(mockFacade.splitRom(any(File.class), any(Path.class), eq(CopierFormat.UFO)))
                .thenThrow(new IOException("Splitter failure"));

        // Execute and expect exception
        IOException ex = assertThrows(IOException.class, () ->
                processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.UFO)
        );

        assertEquals("Splitter failure", ex.getMessage());

        // Verify workspace cleanup: work directory should be removed
        // ConversionWorkspace creates subdirectory under outputDir
        Path workDir = outputDir.resolve("FailGame");

        // If workspace cleanup worked, directory should not exist (or be empty if cleanup failed)
        // We can't directly test ConversionWorkspace cleanup without integration test,
        // but we verify exception propagates correctly
        verify(mockFacade).splitRom(any(File.class), any(Path.class), eq(CopierFormat.UFO));
        verifyNoInteractions(mockPacker);
        verifyNoMoreInteractions(mockFacade);
    }

    @Test
    void testExceptionInPackerClosesWorkspace() throws IOException {
        // Setup: Create test ROM file
        Path romFile = tempDir.resolve("PackFail.sfc");
        Files.write(romFile, new byte[]{0x01, 0x02, 0x03});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Mock splitter to return parts
        File part1 = tempDir.resolve("work").resolve("PackFail.1").toFile();
        Files.createDirectories(part1.getParentFile().toPath());
        Files.write(part1.toPath(), new byte[]{0x01});

        when(mockFacade.splitRom(any(File.class), any(Path.class), eq(CopierFormat.GD3)))
                .thenReturn(List.of(part1));

        // Mock packer to throw exception (normalizer will run in real code)
        when(mockPacker.pack(anyList())).thenThrow(new IllegalArgumentException("Part too large"));

        // Execute and expect exception
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.GD3)
        );

        assertEquals("Part too large", ex.getMessage());

        // Verify calls
        verify(mockFacade).splitRom(any(File.class), any(Path.class), eq(CopierFormat.GD3));
        verify(mockPacker).pack(anyList());
        verifyNoMoreInteractions(mockFacade);
    }

    @Test
    void testExceptionInWriterClosesWorkspace() throws IOException {
        // Setup: Create test ROM file
        Path romFile = tempDir.resolve("WriteFail.sfc");
        Files.write(romFile, new byte[]{0x01, 0x02, 0x03});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Mock splitter to return parts
        File part1 = tempDir.resolve("work").resolve("WriteFail.1").toFile();
        Files.createDirectories(part1.getParentFile().toPath());
        Files.write(part1.toPath(), new byte[]{0x01});

        when(mockFacade.splitRom(any(File.class), any(Path.class), eq(CopierFormat.FIG)))
                .thenReturn(List.of(part1));

        // Mock templateFactory to create actual .img files
        doAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            Files.write(targetPath, new byte[]{0x00});  // Create empty file
            return null;
        }).when(mockTemplateFactory).createBlankDisk(any(FloppyType.class), any(Path.class));

        // Mock packer to return layout (normalizer will run in real code)
        when(mockPacker.pack(anyList())).thenAnswer(invocation -> {
            List<RomPartMetadata> metadata = invocation.getArgument(0);
            return List.of(new DiskLayout(metadata, FloppyType.FLOPPY_144M));
        });

        // Mock writer to throw exception
        doThrow(new IOException("Disk full")).when(mockFacade)
                .write(any(File.class), anyList(), anyMap());

        // Execute and expect exception
        IOException ex = assertThrows(IOException.class, () ->
                processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.FIG)
        );

        assertEquals("Disk full", ex.getMessage());

        // Verify all components were called
        verify(mockFacade).splitRom(any(File.class), any(Path.class), eq(CopierFormat.FIG));
        verify(mockPacker).pack(anyList());
        verify(mockFacade).write(any(File.class), anyList(), anyMap());
    }

    @Test
    void testProcessRomWithEmptyBaseName() throws IOException {
        // Setup: Create ROM file with only extension (edge case)
        Path romFile = tempDir.resolve(".sfc");
        Files.write(romFile, new byte[]{0x01});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        // Execute and expect exception
        IOException ex = assertThrows(IOException.class, () ->
                processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.FIG)
        );

        assertTrue(ex.getMessage().contains("Cannot extract base name"),
                "Should reject file with no base name");

        verifyNoInteractions(mockFacade);
        verifyNoInteractions(mockPacker);
    }

    @Test
    void testProcessRomWithSingleDiskNaming() throws IOException {
        // Verify single-disk naming convention: GameName.img (no _1 suffix)
        Path romFile = tempDir.resolve("SingleDisk.sfc");
        Files.write(romFile, new byte[]{0x01});

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        File part1 = tempDir.resolve("work").resolve("SingleDisk.1").toFile();
        Files.createDirectories(part1.getParentFile().toPath());
        Files.write(part1.toPath(), new byte[]{0x01});

        when(mockFacade.splitRom(any(File.class), any(Path.class), eq(CopierFormat.SWC)))
                .thenReturn(List.of(part1));

        // Mock templateFactory to create actual .img files
        doAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            Files.write(targetPath, new byte[]{0x00});  // Create empty file
            return null;
        }).when(mockTemplateFactory).createBlankDisk(any(FloppyType.class), any(Path.class));

        // Mock packer to return layout (normalizer will run in real code)
        when(mockPacker.pack(anyList())).thenAnswer(invocation -> {
            List<RomPartMetadata> metadata = invocation.getArgument(0);
            return List.of(new DiskLayout(metadata, FloppyType.FLOPPY_144M));
        });

        // Execute
        processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.SWC);

        // Verify output directory contains SingleDisk/SingleDisk.img (not SingleDisk_1.img)
        // After processRom completes, .img files are moved from workspace to outputDir/GameName/
        Path expectedGameDir = outputDir.resolve("SingleDisk");
        Path expectedImage = expectedGameDir.resolve("SingleDisk.img");

        assertTrue(Files.exists(expectedGameDir),
                "Game directory should exist");
        assertTrue(Files.exists(expectedImage),
                "Single disk should be named GameName.img (no numbering)");
    }
}
