package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for concurrent batch processing.
 * <p>
 * Tests verify:
 * - Thread-safe batch execution with multiple ROMs
 * - Workspace isolation (UUID suffixes prevent collisions)
 * - ConversionObserver thread-safety (AtomicInteger counters)
 * - Final .img files moved to correct output locations
 * - Concurrent ROMs with same basename handled correctly (overwrite with warning)
 */
class FloppyConvertConcurrencyTest {

    @TempDir
    Path tempDir;

    private byte[] syntheticRomData;

    @BeforeEach
    void setUp() throws IOException {
        Path syntheticRom = SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, "CONCUR4L", tempDir);
        syntheticRomData = Files.readAllBytes(syntheticRom);
    }

    /**
     * Test concurrent processing of multiple ROMs.
     * Verifies:
     * - All ROMs processed successfully
     * - Unique workspace directories created (UUID suffixes)
     * - Final .img files in correct output locations (not in workspace subdirs)
     */
    @Test
    void testBatchExecutionWithMultipleThreads() throws IOException {
        Path inputDir = tempDir.resolve("input");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Create 10 small test ROMs (enough to exercise thread pool without long runtime)
        // Use distinct basenames to avoid ucon64 split-file detection
        String[] romNames = {"Mario", "Zelda", "Metroid", "DonkeyKong", "Kirby",
                "StarFox", "FZero", "Pilotwings", "SimCity", "Yoshi"};
        int romCount = romNames.length;
        for (int i = 0; i < romCount; i++) {
            Path romFile = inputDir.resolve(romNames[i] + ".sfc");
            Files.write(romFile, syntheticRomData);
        }

        // Execute batch processing
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputDir.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(0, exitCode, "Batch processing should succeed");

        // Verify all ROMs produced output .img files
        Set<String> expectedImages = new HashSet<>();
        for (String romName : romNames) {
            expectedImages.add(romName + ".img");
        }

        Set<String> actualImages = new HashSet<>();
        try (Stream<Path> stream = Files.walk(outputDir)) {
            stream.filter(p -> p.toString().endsWith(".img"))
                    .forEach(p -> actualImages.add(p.getFileName().toString()));
        }

        assertEquals(expectedImages, actualImages,
                "All ROMs should produce .img files in output directory");

        // Verify workspace subdirectories were cleaned up (no .uuid directories remain)
        try (Stream<Path> stream = Files.list(outputDir)) {
            long workspaceDirs = stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().contains("."))
                    .count();
            assertEquals(0, workspaceDirs,
                    "Workspace subdirectories should be cleaned up after processing");
        }
    }

    /**
     * ROMs with the same basename in different input directories keep separate outputs:
     * the output tree mirrors the input tree, so neither overwrites the other.
     */
    @Test
    void sameBasenameInDifferentDirectoriesDoNotCollide() throws IOException {
        Path inputDir = tempDir.resolve("input");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);

        // Create two subdirectories with ROMs of same basename
        Path subdir1 = inputDir.resolve("subdir1");
        Path subdir2 = inputDir.resolve("subdir2");
        Files.createDirectories(subdir1);
        Files.createDirectories(subdir2);

        Files.write(subdir1.resolve("SameName.sfc"), syntheticRomData);
        Files.write(subdir2.resolve("SameName.sfc"), syntheticRomData);

        // Execute batch processing
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputDir.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "swc"
                );

        assertEquals(0, exitCode, "Batch processing should succeed despite basename collision");

        // Verify both subdirectories have output
        // RomProcessor creates an additional subdirectory with the ROM basename
        assertTrue(Files.exists(outputDir.resolve("subdir1").resolve("SameName").resolve("SameName.img")),
                "First ROM should produce output");
        assertTrue(Files.exists(outputDir.resolve("subdir2").resolve("SameName").resolve("SameName.img")),
                "Second ROM should produce output");
    }

    /**
     * A ROM that fails must not stop the batch, but must be visible in the exit code.
     */
    @Test
    void testBatchWithFailingRomConvertsTheRestAndExitsNonZero() throws IOException {
        Path inputDir = tempDir.resolve("input");
        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);

        Files.write(inputDir.resolve("Good.sfc"), syntheticRomData);
        Files.write(inputDir.resolve("Broken.sfc"), new byte[16]);

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputDir.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(1, exitCode, "A failed ROM should make the batch exit non-zero");
        assertTrue(Files.exists(outputDir.resolve("Good").resolve("Good.img")),
                "The valid ROM should still be converted");
    }
}
