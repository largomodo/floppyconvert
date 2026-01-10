package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.util.TestUcon64PathResolver;

import com.largomodo.floppyconvert.core.CopierFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the full ROM conversion pipeline.
 * Tests all supported backup unit formats (FIG, SWC, UFO, GD3) with real ROM files.
 *
 * Requires external tools: ucon64 and mtools (mcopy).
 * Tests skip gracefully when tools are unavailable.
 */
class AppE2ETest {

    // Chrono Trigger is 32 Mbit - large enough for split testing (produces multiple parts)
    private static final String CHRONO_TRIGGER_RESOURCE = "/snes/Chrono Trigger (USA).sfc";
    // Super Mario World is 4 Mbit - too small to split, tests single-file-per-disk path
    private static final String SUPER_MARIO_WORLD_RESOURCE = "/snes/Super Mario World (USA).sfc";

    private static boolean toolsAvailable = false;
    private static String ucon64Path;

    /**
     * Verify both ucon64 and mcopy are available before running E2E tests.
     *
     * Delegates ucon64 resolution to TestUcon64PathResolver (eliminates
     * code duplication with Ucon64DriverTest). mcopy check remains local
     * (no other tests require mcopy, so no shared utility needed).
     */
    @BeforeAll
    static void checkExternalToolsAvailable() {
        // Use shared resolver for ucon64 (PATH-first, then classpath fallback)
        var resolvedPath = TestUcon64PathResolver.resolveUcon64Path();
        if (resolvedPath.isEmpty()) {
            toolsAvailable = false;
            return;
        }
        ucon64Path = resolvedPath.get();

        // mcopy check independent of ucon64 resolution
        toolsAvailable = TestUcon64PathResolver.isCommandAvailable("mcopy");
    }

    static Stream<Arguments> formatAndRomProvider() {
        return Stream.of(
            // Multi-part tests: Chrono Trigger (32 Mbit - produces 8 split parts)
            Arguments.of(CopierFormat.FIG, CHRONO_TRIGGER_RESOURCE),
            Arguments.of(CopierFormat.SWC, CHRONO_TRIGGER_RESOURCE),
            Arguments.of(CopierFormat.UFO, CHRONO_TRIGGER_RESOURCE),
            Arguments.of(CopierFormat.GD3, CHRONO_TRIGGER_RESOURCE),
            // Single-file tests: Super Mario World (4 Mbit - no split, 1 file per disk)
            Arguments.of(CopierFormat.FIG, SUPER_MARIO_WORLD_RESOURCE),
            Arguments.of(CopierFormat.SWC, SUPER_MARIO_WORLD_RESOURCE),
            Arguments.of(CopierFormat.UFO, SUPER_MARIO_WORLD_RESOURCE),
            Arguments.of(CopierFormat.GD3, SUPER_MARIO_WORLD_RESOURCE)
        );
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("formatAndRomProvider")
    void testFullConversionPipeline(CopierFormat format, String romResourcePath, @TempDir Path tempDir) throws Exception {

        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        // Copy ROM file to temp directory
        Path inputRom = tempDir.resolve("input.sfc");
        try (InputStream is = getClass().getResourceAsStream(romResourcePath)) {
            if (is == null) {
                fail("Test resource not found: " + romResourcePath + " (ensure ROM files are in src/test/resources/)");
            }
            Files.copy(is, inputRom);
        }

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI via CommandLine.execute() with positional input parameter
        int exitCode = new CommandLine(new App())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(
                inputRom.toString(),
                "--output-dir", outputDir.toString(),
                "--ucon64-path", ucon64Path,
                "--mtools-path", "mcopy",
                "--format", format.name().toLowerCase()
            );

        assertEquals(0, exitCode, "Conversion should succeed");

        // Verify output structure
        String baseName = inputRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path gameOutputDir = outputDir.resolve(baseName);

        assertTrue(Files.exists(gameOutputDir), "Game output directory not created: " + baseName);
        assertTrue(Files.isDirectory(gameOutputDir), "Expected directory: " + gameOutputDir);

        // Verify .img files were created and are non-empty
        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                .filter(p -> p.toString().endsWith(".img"))
                .toList();

            assertFalse(images.isEmpty(), "No .img files produced for " + format + " / " + baseName);

            for (Path img : images) {
                long size = Files.size(img);
                assertTrue(size > 0, "Empty floppy image: " + img.getFileName());
            }
        }
    }

    @Test
    void testSingleFileMode(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        // Copy Chrono Trigger ROM to temp directory
        Path testRom = tempDir.resolve("ChronoTrigger.sfc");
        try (InputStream is = getClass().getResourceAsStream(CHRONO_TRIGGER_RESOURCE)) {
            if (is == null) {
                fail("Test resource not found: " + CHRONO_TRIGGER_RESOURCE);
            }
            Files.copy(is, testRom);
        }

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI via CommandLine.execute() with positional input parameter (single file mode)
        int exitCode = new CommandLine(new App())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(
                testRom.toString(),
                "--output-dir", outputDir.toString(),
                "--ucon64-path", ucon64Path,
                "--mtools-path", "mcopy",
                "--format", "fig"
            );

        assertEquals(0, exitCode, "Conversion should succeed");

        // Verify output structure
        Path gameOutputDir = outputDir.resolve("ChronoTrigger");
        assertTrue(Files.exists(gameOutputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(gameOutputDir), "Output should be a directory");

        // Verify .img files were created
        List<Path> imgFiles = Files.list(gameOutputDir)
            .filter(p -> p.toString().endsWith(".img"))
            .collect(Collectors.toList());
        assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");

        assertTrue(imgFiles.size() >= 1, "Should have at least one disk image");
    }

}
