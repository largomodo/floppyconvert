package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.core.RomProcessor;
import com.largomodo.floppyconvert.service.MtoolsDriver;
import com.largomodo.floppyconvert.service.Ucon64Driver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    private static final String EMPTY_IMG_RESOURCE = "/empty.img";
    // Chrono Trigger is 32 Mbit - large enough for split testing (produces multiple parts)
    private static final String CHRONO_TRIGGER_RESOURCE = "/snes/Chrono Trigger (USA).sfc";
    // Super Mario World is 4 Mbit - too small to split, tests single-file-per-disk path
    private static final String SUPER_MARIO_WORLD_RESOURCE = "/snes/Super Mario World (USA).sfc";

    private static boolean toolsAvailable = false;
    private static String ucon64Path;

    @BeforeAll
    static void checkExternalToolsAvailable() {
        var ucon64Resource = AppE2ETest.class.getResource("/ucon64");
        if (ucon64Resource == null) {
            toolsAvailable = false;
            return;
        }
        try {
            File ucon64File = new File(ucon64Resource.toURI());
            if (!ucon64File.canExecute()) {
                toolsAvailable = false;
                return;
            }
            ucon64Path = ucon64File.getAbsolutePath();
        } catch (URISyntaxException e) {
            toolsAvailable = false;
            return;
        }

        toolsAvailable = isCommandAvailable("mcopy");
    }

    private static boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("which", command)
                .redirectErrorStream(true)
                .start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
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
    void testFullConversionPipeline(CopierFormat format, String romResourcePath, @TempDir Path outputDir) {

        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        File romFile;
        File emptyImg;
        try {
            var romResource = getClass().getResource(romResourcePath);
            if (romResource == null) {
                fail("Test resource not found: " + romResourcePath + " (ensure ROM files are in src/test/resources/)");
            }
            romFile = new File(romResource.toURI());

            var imgResource = getClass().getResource(EMPTY_IMG_RESOURCE);
            if (imgResource == null) {
                fail("Test resource not found: " + EMPTY_IMG_RESOURCE + " (ensure empty.img is in src/test/resources/)");
            }
            emptyImg = new File(imgResource.toURI());
        } catch (URISyntaxException e) {
            fail("Invalid test resource URI: " + e.getMessage());
            return;
        }

        assertTrue(romFile.exists(), "ROM file missing from filesystem: " + romResourcePath);
        assertTrue(emptyImg.exists(), "Empty image template missing from filesystem");

        Ucon64Driver ucon64 = new Ucon64Driver(ucon64Path);
        MtoolsDriver mtools = new MtoolsDriver("/usr/bin/mcopy");
        RomProcessor processor = new RomProcessor();

        try {
            processor.processRom(romFile, outputDir, emptyImg, ucon64, mtools, format);
        } catch (IOException e) {
            fail("RomProcessor.processRom() failed: " + e.getMessage(), e);
        }

        String baseName = romFile.getName().replaceFirst("\\.[^.]+$", "");
        Path gameOutputDir = outputDir.resolve(baseName);

        assertTrue(Files.exists(gameOutputDir), "Game output directory not created: " + baseName);
        assertTrue(Files.isDirectory(gameOutputDir), "Expected directory: " + gameOutputDir);

        try (var imgFiles = Files.list(gameOutputDir)) {
            var images = imgFiles
                .filter(p -> p.toString().endsWith(".img"))
                .toList();

            assertFalse(images.isEmpty(), "No .img files produced for " + format + " / " + baseName);

            for (Path img : images) {
                long size = Files.size(img);
                assertTrue(size > 0, "Empty floppy image: " + img.getFileName());
            }
        } catch (IOException e) {
            fail("Failed to verify output files: " + e.getMessage(), e);
        }
    }

    @Test
    void testSingleFileMode(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 and/or mcopy not available");

        Path testRom = tempDir.resolve("ChronoTrigger.sfc");
        Files.copy(getClass().getResourceAsStream(CHRONO_TRIGGER_RESOURCE), testRom);

        Path emptyImage = tempDir.resolve("empty.img");
        Files.copy(getClass().getResourceAsStream(EMPTY_IMG_RESOURCE), emptyImage);

        Path ucon64Binary = tempDir.resolve("ucon64");
        try (InputStream is = getClass().getResourceAsStream("/ucon64")) {
            Files.copy(is, ucon64Binary);
        }
        ucon64Binary.toFile().setExecutable(true);

        Class<?> configClass = Class.forName("com.largomodo.floppyconvert.App$Config");
        Constructor<?> configConstructor = configClass.getDeclaredConstructor(
            String.class, String.class, String.class, String.class, String.class,
            CopierFormat.class, String.class);
        configConstructor.setAccessible(true);
        Object config = configConstructor.newInstance(
            null,
            null,
            emptyImage.toString(),
            ucon64Binary.toString(),
            "mcopy",
            CopierFormat.FIG,
            testRom.toString()
        );

        Method runSingleFile = App.class.getDeclaredMethod("runSingleFile",
            configClass, Path.class);
        runSingleFile.setAccessible(true);
        runSingleFile.invoke(null, config, tempDir);

        Path outputDir = tempDir.resolve("ChronoTrigger");
        assertTrue(Files.exists(outputDir), "Output directory should exist in tempDir");
        assertTrue(Files.isDirectory(outputDir), "Output should be a directory");

        List<Path> imgFiles = Files.list(outputDir)
            .filter(p -> p.toString().endsWith(".img"))
            .collect(Collectors.toList());
        assertFalse(imgFiles.isEmpty(), "Should have generated at least one .img file");

        assertTrue(imgFiles.size() >= 1, "Should have at least one disk image");
    }

}
