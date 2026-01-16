package com.largomodo.floppyconvert;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for recursive directory traversal feature.
 * Verifies that batch mode correctly processes ROMs in nested directory structures
 * and mirrors the source structure in output.
 * <p>
 * Requires external tool: ucon64.
 * Tests skip gracefully when ucon64 is unavailable.
 */
class AppRecursionTest {

    private static final String SUPER_MARIO_WORLD_RESOURCE = "/snes/Super Mario World (USA).sfc";

    private static boolean toolsAvailable = false;
    private static String ucon64Path;

    @BeforeAll
    static void checkExternalToolsAvailable() {
        var ucon64Resource = AppRecursionTest.class.getResource("/ucon64");
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

        toolsAvailable = true;
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

    @Test
    void testDeepNesting(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 not available");

        Path inputRoot = tempDir.resolve("input");
        Path outputRoot = tempDir.resolve("output");

        Path categoryDir = inputRoot.resolve("Category");
        Path subCategoryDir = categoryDir.resolve("SubCategory");
        Files.createDirectories(subCategoryDir);

        Path testRom = subCategoryDir.resolve("game.sfc");
        Files.copy(getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE), testRom);

        int exitCode = new CommandLine(new App())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputRoot.toString(),
                        "--output-dir", outputRoot.toString(),
                        "--ucon64-path", ucon64Path,
                                                "--format", "fig"
                );
        assertEquals(0, exitCode, "Conversion should succeed");

        Path expectedCategoryDir = outputRoot.resolve("Category");
        Path expectedSubCategoryDir = expectedCategoryDir.resolve("SubCategory");
        Path expectedGameDir = expectedSubCategoryDir.resolve("game");
        Path expectedImg = expectedGameDir.resolve("game.img");

        assertTrue(Files.exists(expectedCategoryDir), "Category directory should exist");
        assertTrue(Files.isDirectory(expectedCategoryDir), "Category should be directory");
        assertTrue(Files.exists(expectedSubCategoryDir), "SubCategory directory should exist");
        assertTrue(Files.isDirectory(expectedSubCategoryDir), "SubCategory should be directory");
        assertTrue(Files.exists(expectedGameDir), "game directory should exist");
        assertTrue(Files.isDirectory(expectedGameDir), "game should be directory");
        assertTrue(Files.exists(expectedImg), "game.img should exist");
        assertTrue(Files.size(expectedImg) > 0, "game.img should not be empty");
    }

    @Test
    void testSiblingDirectories(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 not available");

        Path inputRoot = tempDir.resolve("input");
        Path outputRoot = tempDir.resolve("output");

        Path dirA = inputRoot.resolve("A");
        Path dirB = inputRoot.resolve("B");
        Files.createDirectories(dirA);
        Files.createDirectories(dirB);

        Path testRom1 = dirA.resolve("game1.sfc");
        Path testRom2 = dirB.resolve("game2.sfc");
        Files.copy(getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE), testRom1);
        Files.copy(getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE), testRom2);

        int exitCode = new CommandLine(new App())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputRoot.toString(),
                        "--output-dir", outputRoot.toString(),
                        "--ucon64-path", ucon64Path,
                                                "--format", "fig"
                );
        assertEquals(0, exitCode, "Conversion should succeed");

        Path outputA = outputRoot.resolve("A");
        Path outputB = outputRoot.resolve("B");
        Path game1Dir = outputA.resolve("game1");
        Path game2Dir = outputB.resolve("game2");

        assertTrue(Files.exists(outputA), "Output A directory should exist");
        assertTrue(Files.isDirectory(outputA), "Output A should be directory");
        assertTrue(Files.exists(outputB), "Output B directory should exist");
        assertTrue(Files.isDirectory(outputB), "Output B should be directory");
        assertTrue(Files.exists(game1Dir), "game1 directory should exist in A");
        assertTrue(Files.exists(game2Dir), "game2 directory should exist in B");

        assertNotEquals(game1Dir, game2Dir, "game1 and game2 should be in separate directories");

        Path img1 = game1Dir.resolve("game1.img");
        Path img2 = game2Dir.resolve("game2.img");
        assertTrue(Files.exists(img1), "game1.img should exist");
        assertTrue(Files.exists(img2), "game2.img should exist");
    }

    @Test
    void testRootLevelFile(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 not available");

        Path inputRoot = tempDir.resolve("input");
        Path outputRoot = tempDir.resolve("output");
        Files.createDirectories(inputRoot);

        Path testRom = inputRoot.resolve("game.sfc");
        Files.copy(getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE), testRom);

        int exitCode = new CommandLine(new App())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputRoot.toString(),
                        "--output-dir", outputRoot.toString(),
                        "--ucon64-path", ucon64Path,
                                                "--format", "fig"
                );
        assertEquals(0, exitCode, "Conversion should succeed");

        Path expectedGameDir = outputRoot.resolve("game");
        Path expectedImg = expectedGameDir.resolve("game.img");

        assertTrue(Files.exists(expectedGameDir), "game directory should exist at root of output");
        assertTrue(Files.isDirectory(expectedGameDir), "game should be directory");
        assertTrue(Files.exists(expectedImg), "game.img should exist");
        assertTrue(Files.size(expectedImg) > 0, "game.img should not be empty");

        try (var stream = Files.list(outputRoot)) {
            long directChildCount = stream.filter(Files::isDirectory).count();
            assertEquals(1, directChildCount, "Output root should have exactly one direct child directory");
        }
    }

    @Test
    void testSymlinkNotFollowed(@TempDir Path tempDir) throws Exception {
        assumeTrue(toolsAvailable, "Skipping: ucon64 not available");

        Path inputRoot = tempDir.resolve("input");
        Path outputRoot = tempDir.resolve("output");
        Files.createDirectories(inputRoot);

        Path realDir = tempDir.resolve("real");
        Files.createDirectories(realDir);
        Path testRom = realDir.resolve("game.sfc");
        Files.copy(getClass().getResourceAsStream(SUPER_MARIO_WORLD_RESOURCE), testRom);

        Path symlinkDir = inputRoot.resolve("symlinked");
        try {
            Files.createSymbolicLink(symlinkDir, realDir);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Skipping: filesystem does not support symlinks");
        }

        int exitCode = new CommandLine(new App())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(
                        inputRoot.toString(),
                        "--output-dir", outputRoot.toString(),
                        "--ucon64-path", ucon64Path,
                                                "--format", "fig"
                );
        assertEquals(0, exitCode, "Conversion should succeed");

        Path symlinkOutputDir = outputRoot.resolve("symlinked");
        assertFalse(Files.exists(symlinkOutputDir), "Symlinked directory should not be traversed");

        try (var stream = Files.list(outputRoot)) {
            long fileCount = stream.count();
            assertEquals(0, fileCount, "Output directory should be empty (symlink not followed)");
        }
    }
}
