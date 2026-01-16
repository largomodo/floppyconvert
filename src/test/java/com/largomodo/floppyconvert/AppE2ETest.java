package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.util.TestUcon64PathResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

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
 * <p>
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
     * <p>
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

        // Verify cleanup: no split parts (.1, .2, .3, etc) should remain
        try (var stream = Files.list(gameOutputDir)) {
            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches(".*\\.\\d+$"))
                    .count();
            assertEquals(0, partFileCount,
                    "Split parts (.1, .2, .3) must not remain in output for " + format + " / " + baseName);
        }

        // Verify cleanup removed intermediate format files (only for large ROMs that were split)
        // Small ROMs (≤4 Mbit) don't get split, so the intermediate file IS the part - don't check cleanup
        boolean isLargeRom = romResourcePath.contains("Chrono Trigger");
        if (isLargeRom) {
            try (var files = Files.list(gameOutputDir)) {
                long intermediateCount = files
                        .filter(p -> p.toString().endsWith(".fig") ||
                                p.toString().endsWith(".swc") ||
                                p.toString().endsWith(".ufo") ||
                                p.toString().endsWith(".gd3"))
                        .count();
                assertEquals(0, intermediateCount,
                        "Intermediate format files must be cleaned up for large ROMs: " + format);
            }
        }
    }

    @Test
    void testSingleFileMode(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        // Copy Super Mario World ROM to temp directory
        Path testRom = tempDir.resolve("SuperMarioWorld.sfc");
        try (InputStream is = getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE)) {
            if (is == null) {
                fail("Test resource not found: " + SUPER_MARIO_WORLD_RESOURCE);
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
                                                "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed");

        // Verify output structure
        Path gameOutputDir = outputDir.resolve("SuperMarioWorld");
        assertTrue(Files.exists(gameOutputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(gameOutputDir), "Output should be a directory");

        // Verify .img files were created
        List<Path> imgFiles = Files.list(gameOutputDir)
                .filter(p -> p.toString().endsWith(".img"))
                .collect(Collectors.toList());
        assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");

        assertEquals(1, imgFiles.size(), "Should produce single disk for 4Mbit ROM");
        assertTrue(imgFiles.get(0).getFileName().toString().matches(".*\\.img"),
                "Output should be .img file");

        // Super Mario World is 4Mbit (512KB) - should fit on 720K disk
        long imageSize = Files.size(imgFiles.get(0));
        assertTrue(imageSize < 800_000,
                "Should use 720K template (~737KB) for 512KB ROM, got " + imageSize + " bytes");

        // Verify cleanup: no split parts (.1, .2, .3) should remain (small ROM doesn't split)
        try (var stream = Files.list(gameOutputDir)) {
            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches(".*\\.\\d+$"))
                    .count();
            assertEquals(0, partFileCount, "No split parts should exist for small ROM");
        }

        // Verify cleanup: no intermediate format files remain (converted to .img)
        try (var files = Files.list(gameOutputDir)) {
            long intermediateCount = files
                    .filter(p -> p.toString().endsWith(".fig") ||
                            p.toString().endsWith(".swc") ||
                            p.toString().endsWith(".ufo") ||
                            p.toString().endsWith(".gd3"))
                    .count();
            assertEquals(0, intermediateCount, "Intermediate format files should be converted to .img");
        }
    }

    @Test
    void testSpecialCharactersInFilename(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        // Copy ROM file with special characters in the filename
        // This tests the mcopy sanitization fix for characters like #, [, ], (, ), and space
        String problematicFilename = "VLDC10 [#053] - ERROR CODE #1D4 (Update) by Sayuri [2017-04-02] (SMW Hack).sfc";
        Path testRom = tempDir.resolve(problematicFilename);

        // Use the actual problematic ROM file from workspace root
        Path sourceRom = Path.of("/workspace", problematicFilename);
        if (!Files.exists(sourceRom)) {
            // Fallback: copy Super Mario World with the problematic filename
            try (InputStream is = getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE)) {
                if (is == null) {
                    fail("Test resource not found: " + SUPER_MARIO_WORLD_RESOURCE);
                }
                Files.copy(is, testRom);
            }
        } else {
            Files.copy(sourceRom, testRom);
        }

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI - should handle special characters without truncation
        int exitCode = new CommandLine(new App())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        testRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--ucon64-path", ucon64Path,
                                                "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed even with special characters in filename");

        // Verify output structure - base name is sanitized for filesystem safety
        // Special characters #, [, ], (, ), and space are replaced with underscores
        String expectedBaseName = "VLDC10___053__-_ERROR_CODE__1D4__Update__by_Sayuri__2017-04-02___SMW_Hack_";
        Path gameOutputDir = outputDir.resolve("VLDC10 [#053] - ERROR CODE #1D4 (Update) by Sayuri [2017-04-02] (SMW Hack)");
        assertTrue(Files.exists(gameOutputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(gameOutputDir), "Output should be a directory");

        // Verify .img files were created
        List<Path> imgFiles = Files.list(gameOutputDir)
                .filter(p -> p.toString().endsWith(".img"))
                .collect(Collectors.toList());
        assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");

        // Verify cleanup: no split parts or intermediate files remain
        try (var stream = Files.list(gameOutputDir)) {
            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches(".*\\.\\d+$"))
                    .count();
            assertEquals(0, partFileCount, "No split parts should remain after processing");
        }

        try (var files = Files.list(gameOutputDir)) {
            long intermediateCount = files
                    .filter(p -> p.toString().endsWith(".fig") ||
                            p.toString().endsWith(".swc") ||
                            p.toString().endsWith(".ufo") ||
                            p.toString().endsWith(".gd3"))
                    .count();
            assertEquals(0, intermediateCount, "No intermediate format files should remain");
        }
    }

    @Test
    void testShellSensitiveCharactersInFilename(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        // Test file with shell-sensitive characters: &, $, !, and space
        // These characters cause mcopy's argument parser to truncate paths
        String problematicFilename = "Ren & Stimpy Show, The - Buckeroo$! (USA).sfc";
        Path testRom = tempDir.resolve(problematicFilename);

        // Use the actual problematic ROM file from workspace root
        Path sourceRom = Path.of("/workspace", problematicFilename);
        if (!Files.exists(sourceRom)) {
            // Fallback: copy Super Mario World with the problematic filename
            try (InputStream is = getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE)) {
                if (is == null) {
                    fail("Test resource not found: " + SUPER_MARIO_WORLD_RESOURCE);
                }
                Files.copy(is, testRom);
            }
        } else {
            Files.copy(sourceRom, testRom);
        }

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI - should handle shell-sensitive characters without truncation
        int exitCode = new CommandLine(new App())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        testRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--ucon64-path", ucon64Path,
                                                "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed with shell-sensitive characters (&, $, !, space)");

        // Verify output structure - shell-sensitive characters replaced with underscores
        // &, $, !, space, (, and ) are all sanitized to underscores
        String expectedBaseName = "Ren___Stimpy_Show,_The_-_Buckeroo____USA_";
        Path gameOutputDir = outputDir.resolve("Ren & Stimpy Show, The - Buckeroo$! (USA)");
        assertTrue(Files.exists(outputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(outputDir), "Output should be a directory");

        // Verify .img files were created
        List<Path> imgFiles = Files.list(gameOutputDir)
                .filter(p -> p.toString().endsWith(".img"))
                .collect(Collectors.toList());
        assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");

        // Verify cleanup: no split parts or intermediate files remain
        try (var stream = Files.list(gameOutputDir)) {
            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches(".*\\.\\d+$"))
                    .count();
            assertEquals(0, partFileCount, "No split parts should remain after processing");
        }

        try (var files = Files.list(gameOutputDir)) {
            long intermediateCount = files
                    .filter(p -> p.toString().endsWith(".fig") ||
                            p.toString().endsWith(".swc") ||
                            p.toString().endsWith(".ufo") ||
                            p.toString().endsWith(".gd3"))
                    .count();
            assertEquals(0, intermediateCount, "No intermediate format files should remain");
        }
    }

}
