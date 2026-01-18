package com.largomodo.floppyconvert.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for SnesRomMatcher.
 * <p>
 * Example-based unit tests for SnesRomMatcher.
 */
class SnesRomMatcherTest {

    @Test
    void testStandardExtensions(@TempDir Path tempDir) throws IOException {
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "game.sfc")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "game.fig")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "game.smw")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "game.swc")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "game.ufo")));
    }

    @Test
    void testCaseInsensitiveExtensions(@TempDir Path tempDir) throws IOException {
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "GAME.SFC")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "Game.Sfc")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "GAME.FIG")));
    }

    @Test
    void testGameDoctorFormats(@TempDir Path tempDir) throws IOException {
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "sf32chra.078")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "SF12TEST")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "sf1abc")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "sf01test.048")));
        assertTrue(SnesRomMatcher.isRom(createFile(tempDir, "sf99abcd.058")));
    }

    @Test
    void testInvalidFiles(@TempDir Path tempDir) throws IOException {
        assertFalse(SnesRomMatcher.isRom(createFile(tempDir, "readme.txt")));
        assertFalse(SnesRomMatcher.isRom(createFile(tempDir, "game.zip")));
        assertFalse(SnesRomMatcher.isRom(createFile(tempDir, "sf")), "Too short for Game Doctor (no extension, only 2 chars)");
        assertFalse(SnesRomMatcher.isRom(createFile(tempDir, "sfa")), "Too short for Game Doctor (no extension, only 3 chars)");
        assertFalse(SnesRomMatcher.isRom(createFile(tempDir, "readme")), "No extension, doesn't match Game Doctor pattern");
    }

    @Test
    void testNullPath() {
        assertFalse(SnesRomMatcher.isRom(null));
    }

    @Test
    void testDirectory(@TempDir Path tempDir) throws IOException {
        // Create a directory with Game Doctor-like name to test false positive prevention
        Path dir = tempDir.resolve("sf01test");
        Files.createDirectory(dir);
        assertFalse(SnesRomMatcher.isRom(dir), "Directories should return false");
    }

    @Test
    void testNonexistentPath(@TempDir Path tempDir) {
        Path nonexistent = tempDir.resolve("does-not-exist.sfc");
        assertFalse(SnesRomMatcher.isRom(nonexistent), "Non-existent paths should return false");
    }

    /**
     * Create a test file with given name.
     */
    private Path createFile(Path dir, String name) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, "test content");
        return file;
    }
}
