package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.ResourceDiskTemplateFactory;
import com.largomodo.floppyconvert.core.RomPartNormalizer;
import com.largomodo.floppyconvert.core.RomProcessor;
import com.largomodo.floppyconvert.core.domain.GreedyDiskPacker;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.DefaultConversionFacade;
import com.largomodo.floppyconvert.service.NativeRomSplitter;
import com.largomodo.floppyconvert.service.fat.Fat12ImageWriter;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the full ROM conversion pipeline.
 * Tests all supported backup unit formats (FIG, SWC, UFO, GD3) with real ROM files.
 * <p>
 * Tests now use native Java splitter and synthetic ROM data, no longer require ucon64.
 * Legacy tests with ucon64 skip gracefully when tools or copyrighted ROMs are unavailable.
 */
class FloppyConvertE2ETest {

    // Chrono Trigger is 32 Mbit - large enough for split testing (produces multiple parts)
    private static final String CHRONO_TRIGGER_RESOURCE = "/snes/Chrono Trigger (USA).sfc";
    // Super Mario World is 4 Mbit - too small to split, tests single-file-per-disk path
    private static final String SUPER_MARIO_WORLD_RESOURCE = "/snes/Super Mario World (USA).sfc";
    private static final int LOROM_HEADER_OFFSET = 0x7FB0;
    private static final int HIROM_HEADER_OFFSET = 0xFFB0;
    @TempDir
    Path tempDir;
    Path testResourcesDir;
    Path outputDir;

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

    @BeforeEach
    void setUp() {
        testResourcesDir = Path.of("src/test/resources/snes");
        outputDir = tempDir.resolve("output");
    }

    private RomProcessor createProcessor() {
        NativeRomSplitter splitter = new NativeRomSplitter(
                new SnesRomReader(),
                new SnesInterleaver(),
                new HeaderGeneratorFactory()
        );
        Fat12ImageWriter writer = new Fat12ImageWriter();
        DefaultConversionFacade facade = new DefaultConversionFacade(splitter, writer);

        return new RomProcessor(
                new GreedyDiskPacker(),
                facade,
                new ResourceDiskTemplateFactory(),
                new RomPartNormalizer()
        );
    }

    private List<Path> listDiskImages(String romName) throws Exception {
        String sanitizedName = new RomPartNormalizer().sanitizeName(romName);
        Path gameDir = outputDir.resolve(sanitizedName);
        if (!Files.exists(gameDir)) {
            return List.of();
        }
        try (var stream = Files.list(gameDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".img"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource("formatAndRomProvider")
    void testFullConversionPipeline(CopierFormat format, String romResourcePath, @TempDir Path tempDir) throws Exception {
        // Use real ROM if available, fallback to synthetic ROM based on registry spec
        Path inputRom = TestRomProvider.getRomOrSynthetic(romResourcePath, tempDir);

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI via CommandLine.execute() with positional input parameter
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", format.name().toLowerCase()
                );

        assertEquals(0, exitCode, "Conversion should succeed");

        // Verify output structure
        String baseName = inputRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        // Sanitize baseName to match RomProcessor behavior (replaces special chars with underscores)
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);

        assertTrue(Files.exists(gameOutputDir), "Game output directory not created: " + sanitizedBaseName);
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

        // Verify cleanup: no split parts should remain
        // Format-specific patterns: SWC/FIG use .1, .2, .3; UFO uses .1gm, .2gm, .3gm; GD3 uses .078
        try (var stream = Files.list(gameOutputDir)) {
            String formatPattern = switch (format) {
                case UFO -> ".*\\.(\\d+)gm$";
                case GD3 -> "SF\\d+[A-Z_]+\\.078$";
                default -> ".*\\.\\d+$";
            };

            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches(formatPattern))
                    .count();
            assertEquals(0, partFileCount,
                    "Split parts must not remain in output for " + format + " / " + baseName +
                            " (pattern: " + formatPattern + ")");
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
        // Use real ROM if available, fallback to synthetic ROM
        Path testRom = TestRomProvider.getRomOrSynthetic(SUPER_MARIO_WORLD_RESOURCE, tempDir);

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI via CommandLine.execute() with positional input parameter (single file mode)
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        testRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed");

        // Verify output structure - compute sanitized base name from actual input file
        String baseName = testRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(gameOutputDir), "Output should be a directory");

        // Verify .img files were created
        try (var stream = Files.list(gameOutputDir)) {
            List<Path> imgFiles = stream
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");

            assertEquals(1, imgFiles.size(), "Should produce single disk for 4Mbit ROM");
            assertTrue(imgFiles.getFirst().getFileName().toString().matches(".*\\.img"),
                    "Output should be .img file");

            // Super Mario World is 4Mbit (512KB) - should fit on 720K disk
            long imageSize = Files.size(imgFiles.getFirst());
            assertTrue(imageSize < 800_000,
                    "Should use 720K template (~737KB) for 512KB ROM, got " + imageSize + " bytes");
        }

        // Verify cleanup: no split parts should remain (small ROM doesn't split)
        // FIG format uses .1, .2, .3 extensions
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
        // Copy ROM file with special characters in the filename
        // Validates filename sanitization for special characters (#, [, ], (, ), space)
        String problematicFilename = "VLDC10 [#053] - ERROR CODE #1D4 (Update) by Sayuri [2017-04-02] (SMW Hack).sfc";
        Path testRom = tempDir.resolve(problematicFilename);

        // Try to source from workspace or fallback to TestRomProvider
        Path sourceRom = Path.of("/workspace", problematicFilename);
        if (Files.exists(sourceRom)) {
            Files.copy(sourceRom, testRom);
        } else {
            // Fallback to TestRomProvider (uses Super Mario World spec)
            testRom = TestRomProvider.getRomOrSynthetic(SUPER_MARIO_WORLD_RESOURCE, tempDir);
            // Rename to problematic filename for test
            Path renamed = tempDir.resolve(problematicFilename);
            Files.move(testRom, renamed);
            testRom = renamed;
        }

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI - should handle special characters without truncation
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        testRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed even with special characters in filename");

        // Verify output structure - base name is sanitized for filesystem safety
        // Special characters #, [, ], (, ), and space are replaced with underscores
        String expectedBaseName = "VLDC10___053__-_ERROR_CODE__1D4__Update__by_Sayuri__2017-04-02___SMW_Hack_";
        Path gameOutputDir = outputDir.resolve(expectedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(gameOutputDir), "Output should be a directory");

        // Verify .img files were created
        try (var stream = Files.list(gameOutputDir)) {
            List<Path> imgFiles = stream
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");
        }

        // Verify cleanup: no split parts should remain
        // FIG format uses .1, .2, .3 extensions
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
        // Test file with shell-sensitive characters: &, $, !, and space
        // These characters require escaping to prevent argument parser truncation
        String problematicFilename = "Ren & Stimpy Show, The - Buckeroo$! (USA).sfc";
        Path testRom = tempDir.resolve(problematicFilename);

        // Use the actual problematic ROM file from workspace root
        Path sourceRom = Path.of("/workspace", problematicFilename);
        if (Files.exists(sourceRom)) {
            Files.copy(sourceRom, testRom);
        } else {
            // Fallback to TestRomProvider (uses Super Mario World spec)
            testRom = TestRomProvider.getRomOrSynthetic(SUPER_MARIO_WORLD_RESOURCE, tempDir);
            // Rename to problematic filename for test
            Path renamed = tempDir.resolve(problematicFilename);
            Files.move(testRom, renamed);
            testRom = renamed;
        }

        Path outputDir = tempDir.resolve("output");

        // Invoke CLI - should handle shell-sensitive characters without truncation
        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        testRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed with shell-sensitive characters (&, $, !, space)");

        // Verify output structure - shell-sensitive characters replaced with underscores
        // &, $, !, space, (, ), and , are all sanitized to underscores
        String expectedBaseName = "Ren___Stimpy_Show__The_-_Buckeroo____USA_";
        Path gameOutputDir = outputDir.resolve(expectedBaseName);

        assertTrue(Files.exists(outputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(outputDir), "Output should be a directory");

        // Verify .img files were created
        try (var stream = Files.list(gameOutputDir)) {
            List<Path> imgFiles = stream
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");
        }

        // Verify cleanup: no split parts should remain
        // FIG format uses .1, .2, .3 extensions
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
    void testAlternativeExtension(@TempDir Path tempDir) throws Exception {
        // Use real ROM if available, fallback to synthetic ROM
        Path testRom = TestRomProvider.getRomOrSynthetic(CHRONO_TRIGGER_RESOURCE, tempDir);
        // Rename to .fig extension for test
        Path renamedRom = tempDir.resolve("ChronoTrigger.fig");
        Files.move(testRom, renamedRom);
        testRom = renamedRom;

        Path outputDir = tempDir.resolve("output");

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        testRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed with .fig extension");

        Path gameOutputDir = outputDir.resolve("ChronoTrigger");
        assertTrue(Files.exists(gameOutputDir), "Output directory should exist");
        assertTrue(Files.isDirectory(gameOutputDir), "Output should be a directory");

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();

            assertFalse(images.isEmpty(), "Should have generated at least one .img file");

            for (Path img : images) {
                long size = Files.size(img);
                assertTrue(size > 0, "Empty floppy image: " + img.getFileName());
            }
        }
    }

    @Test
    void testConvertSynthetic8MbitLoRomWithSwc(@TempDir Path tempDir) throws Exception {
        Path syntheticRom = SyntheticRomFactory.generateLoRom(8, 0, DspChipset.ABSENT, "test_8mbit_lorom", tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        syntheticRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "swc"
                );

        assertEquals(0, exitCode, "Conversion should succeed for 8 Mbit LoROM");

        // Compute sanitized base name from actual input file
        String baseName = syntheticRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Game output directory not created");

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(images.isEmpty(), "Should generate at least one .img file");
        }
    }

    @Test
    void testConvertSynthetic16MbitHiRomWithGd3(@TempDir Path tempDir) throws Exception {
        Path syntheticRom = SyntheticRomFactory.generateHiRom(16, 0, DspChipset.ABSENT, "test_16mbit_hirom", tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        syntheticRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "gd3"
                );

        assertEquals(0, exitCode, "Conversion should succeed for 16 Mbit HiROM with GD3");

        // Compute sanitized base name from actual input file
        String baseName = syntheticRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Game output directory not created");

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(images.isEmpty(), "Should generate .img files for 16 Mbit HiROM with GD3");
            assertTrue(images.size() >= 2, "16 Mbit ROM should span multiple disks");

            for (Path img : images) {
                long size = Files.size(img);
                assertTrue(size > 0, "Empty floppy image: " + img.getFileName());
            }
        }

        try (var stream = Files.list(gameOutputDir)) {
            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches("SF\\d+[A-Z_]+\\.078$"))
                    .count();
            assertEquals(0, partFileCount, "GD3 split parts (SF*.078) should be cleaned up");
        }
    }

    @Test
    void testConvertSynthetic12MbitHiRomWithGd3Padding(@TempDir Path tempDir) throws Exception {
        Path syntheticRom = SyntheticRomFactory.generateHiRom(12, 0, DspChipset.ABSENT, "test_12mbit_hirom", tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        syntheticRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "gd3"
                );

        assertEquals(0, exitCode, "Conversion should succeed for 12 Mbit HiROM with GD3");

        // Compute sanitized base name from actual input file
        String baseName = syntheticRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Game output directory not created");

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(images.isEmpty(), "Should generate .img files for 12 Mbit HiROM with GD3");

            for (Path img : images) {
                long size = Files.size(img);
                assertTrue(size > 0, "Empty floppy image: " + img.getFileName());
            }
        }

        try (var stream = Files.list(gameOutputDir)) {
            long partFileCount = stream
                    .filter(p -> p.getFileName().toString().matches(".*\\.(078|\\d+)$"))
                    .count();
            assertEquals(0, partFileCount, "Split parts should be cleaned up after conversion");
        }
    }

    @Test
    void testConvertSynthetic8MbitHiRomWith64KbSram(@TempDir Path tempDir) throws Exception {
        Path syntheticRom = SyntheticRomFactory.generateHiRom(8, 64, DspChipset.ABSENT, "test_8mbit_hirom_64kb_sram", tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        syntheticRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "fig"
                );

        assertEquals(0, exitCode, "Conversion should succeed for 8 Mbit HiROM with 64KB SRAM");

        // Compute sanitized base name from actual input file
        String baseName = syntheticRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Game output directory not created");

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(images.isEmpty(), "Should generate at least one .img file");
        }
    }

    @Test
    void testConvertSynthetic12MbitLoRomWithDsp(@TempDir Path tempDir) throws Exception {
        Path syntheticRom = SyntheticRomFactory.generateLoRom(12, 0, DspChipset.PRESENT, "test_12mbit_lorom_dsp", tempDir);
        Path outputDir = tempDir.resolve("output");

        int exitCode = new CommandLine(new FloppyConvert())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        syntheticRom.toString(),
                        "--output-dir", outputDir.toString(),
                        "--format", "ufo"
                );

        assertEquals(0, exitCode, "Conversion should succeed for 12 Mbit LoROM with DSP");

        // Compute sanitized base name from actual input file
        String baseName = syntheticRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        String sanitizedBaseName = new RomPartNormalizer().sanitizeName(baseName);
        Path gameOutputDir = outputDir.resolve(sanitizedBaseName);
        assertTrue(Files.exists(gameOutputDir), "Game output directory not created");

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                    .filter(p -> p.toString().endsWith(".img"))
                    .toList();
            assertFalse(images.isEmpty(), "Should generate at least one .img file");
        }
    }


    @Test
    void testGd3_8MbitHiRom_ForceSplit_E2E() throws Exception {
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.SUPER_BOMBERMAN_2, tempDir);

        RomProcessor processor = createProcessor();
        int diskCount = processor.processRom(romPath.toFile(), outputDir, "test", CopierFormat.GD3);

        assertEquals(1, diskCount, "8Mbit ROM should fit on 1 disk");

        String baseName = romPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        List<Path> diskImages = listDiskImages(baseName);
        assertEquals(1, diskImages.size(), "Should produce 1 disk image");

        Path diskImage = diskImages.get(0);
        assertTrue(Files.size(diskImage) > 700_000, "Disk image should be at least 720KB");
    }

    @Test
    void testGd3_16MbitHiRom_BoundaryCase_E2E() throws Exception {
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.ART_OF_FIGHTING, tempDir);

        RomProcessor processor = createProcessor();
        int diskCount = processor.processRom(romPath.toFile(), outputDir, "test", CopierFormat.GD3);

        assertTrue(diskCount >= 2, "16Mbit ROM should span multiple disks");

        String baseName = romPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        List<Path> diskImages = listDiskImages(baseName);
        assertTrue(diskImages.size() >= 2, "Should produce at least 2 disk images");
    }

    @Test
    void testGd3_20MbitHiRom_NoForceSplit_E2E() throws Exception {
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.STREET_FIGHTER_II, tempDir);

        RomProcessor processor = createProcessor();
        int diskCount = processor.processRom(romPath.toFile(), outputDir, "test", CopierFormat.GD3);

        assertTrue(diskCount >= 2, "20Mbit ROM should span multiple disks");

        String baseName = romPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        List<Path> diskImages = listDiskImages(baseName);
        assertTrue(diskImages.size() >= 2, "Should produce at least 2 disk images");
    }

    @Test
    void testUfo_12MbitHiRom_IrregularChunks_E2E() throws Exception {
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.ACTRAISER_2, tempDir);

        RomProcessor processor = createProcessor();
        int diskCount = processor.processRom(romPath.toFile(), outputDir, "test", CopierFormat.UFO);

        assertTrue(diskCount >= 1, "12Mbit ROM should produce at least 1 disk");

        String baseName = romPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        List<Path> diskImages = listDiskImages(baseName);
        assertFalse(diskImages.isEmpty(), "Should produce disk images");

        for (Path diskImage : diskImages) {
            assertTrue(Files.size(diskImage) > 700_000, "Each disk should be at least 720KB");
        }
    }

    @Test
    void testUfo_12MbitLoRom_StandardChunks_E2E() throws Exception {
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.BREATH_OF_FIRE, tempDir);

        RomProcessor processor = createProcessor();
        int diskCount = processor.processRom(romPath.toFile(), outputDir, "test", CopierFormat.UFO);

        assertTrue(diskCount >= 1, "12Mbit ROM should produce at least 1 disk");

        String baseName = romPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        List<Path> diskImages = listDiskImages(baseName);
        assertFalse(diskImages.isEmpty(), "Should produce disk images");

        for (Path diskImage : diskImages) {
            assertTrue(Files.size(diskImage) > 700_000, "Each disk should be at least 720KB");
        }
    }

    @Test
    void testUfoRejectsLargeHiRom() throws Exception {
        Path romFile = tempDir.resolve("large_hirom.sfc");
        byte[] romData = new byte[48 * SnesConstants.MBIT];
        romData[HIROM_HEADER_OFFSET + 0x15] = 0x21;
        romData[HIROM_HEADER_OFFSET + 0x1C] = (byte) 0xFF;
        romData[HIROM_HEADER_OFFSET + 0x1D] = (byte) 0xFF;
        romData[HIROM_HEADER_OFFSET + 0x1E] = 0x00;
        romData[HIROM_HEADER_OFFSET + 0x1F] = 0x00;
        Files.write(romFile, romData);

        RomProcessor processor = createProcessor();

        assertThrows(UnsupportedHardwareException.class, () ->
                processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.UFO)
        );
    }

    @Test
    void testGd3AcceptsExHiRom() throws Exception {
        Path romFile = tempDir.resolve("exhirom.sfc");
        byte[] romData = new byte[48 * SnesConstants.MBIT];
        int headerOffset = 0x40FFB0;
        romData[headerOffset + 0x15] = 0x25;
        romData[headerOffset + 0x1C] = (byte) 0xAA;
        romData[headerOffset + 0x1D] = (byte) 0xBB;
        romData[headerOffset + 0x1E] = (byte) 0x55;
        romData[headerOffset + 0x1F] = (byte) 0x44;
        Files.write(romFile, romData);

        RomProcessor processor = createProcessor();
        processor.processRom(romFile.toFile(), outputDir, "test", CopierFormat.GD3);
    }

    @Test
    void testGd3_48MbitExHiRom_E2E() throws Exception {
        // Generate 48Mbit ExHiROM via TestRomProvider (synthetic-only, no real ROM)
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.EXHIROM_48MBIT, tempDir);

        RomProcessor processor = createProcessor();
        processor.processRom(romPath.toFile(), outputDir, "test", CopierFormat.GD3);

        List<Path> images = listDiskImages(romPath.getFileName().toString().replaceFirst("\\.[^.]+$", ""));

        assertFalse(images.isEmpty(), "Should generate at least one disk image");
        assertTrue(images.size() > 1, "48Mbit ROM should span multiple disks");

        for (Path img : images) {
            assertTrue(Files.size(img) > 0, "Disk image should not be empty: " + img.getFileName());
        }
    }

}
